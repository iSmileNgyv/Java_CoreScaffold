package com.ismile.core.chronovcscli.core.pull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcscli.auth.CredentialsEntry;
import com.ismile.core.chronovcscli.core.index.IndexEngine;
import com.ismile.core.chronovcscli.core.checkout.CheckoutService;
import com.ismile.core.chronovcscli.core.merge.MergeEngine;
import com.ismile.core.chronovcscli.core.merge.MergeResult;
import com.ismile.core.chronovcscli.core.merge.MergeStrategy;
import com.ismile.core.chronovcscli.core.release.ReleaseState;
import com.ismile.core.chronovcscli.remote.RemoteCloneService;
import com.ismile.core.chronovcscli.remote.RemoteConfig;
import com.ismile.core.chronovcscli.remote.dto.BatchObjectsResponseDto;
import com.ismile.core.chronovcscli.remote.dto.CommitHistoryResponseDto;
import com.ismile.core.chronovcscli.remote.dto.CommitSnapshotDto;
import com.ismile.core.chronovcscli.remote.dto.RefsResponseDto;
import com.ismile.core.chronovcscli.remote.dto.ReleaseResponseDto;
import com.ismile.core.chronovcscli.remote.dto.RepositoryInfoResponseDto;
import com.ismile.core.chronovcscli.utils.SemverUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PullService {

    private final RemoteCloneService remoteCloneService;
    private final LocalCommitReader localCommitReader;
    private final CommitComparator commitComparator;
    private final MergeEngine mergeEngine;
    private final IndexEngine indexEngine;
    private final CheckoutService checkoutService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Execute pull operation
     */
    public PullResult pull(File projectRoot, RemoteConfig remoteConfig, CredentialsEntry credentials) {
        try {
            // 1. Get current branch and HEAD
            String currentBranch = localCommitReader.getCurrentBranch(projectRoot);
            String localHead = localCommitReader.getCurrentHead(projectRoot);

            log.info("Current branch: {}, local HEAD: {}", currentBranch, localHead);

            RepositoryInfoResponseDto repoInfo = remoteCloneService.getRepositoryInfo(remoteConfig, credentials);
            boolean releaseEnabled = repoInfo != null && repoInfo.isReleaseEnabled();
            String defaultBranch = repoInfo != null && repoInfo.getDefaultBranch() != null
                    ? repoInfo.getDefaultBranch()
                    : "main";

            if (releaseEnabled && currentBranch != null && currentBranch.equals(defaultBranch)) {
                return PullResult.error(
                        "Default branch is protected while release mode is enabled. Use --release."
                );
            }

            // 2. Fetch remote refs
            RefsResponseDto refs = remoteCloneService.getRefs(remoteConfig, credentials);
            String remoteHead = refs.getBranches().get(currentBranch);

            if (remoteHead == null) {
                return PullResult.error("Remote branch '" + currentBranch + "' not found");
            }

            log.info("Remote HEAD: {}", remoteHead);

            // 3. Fetch remote commit history
            CommitHistoryResponseDto history = remoteCloneService.getCommitHistory(
                    remoteConfig, credentials, currentBranch, null
            );

            // 4. Analyze pull operation
            PullAnalysis analysis = commitComparator.analyzePull(
                    projectRoot, localHead, remoteHead, history.getCommits()
            );

            log.info("Pull strategy: {}", analysis.getStrategy());

            // 5. Handle based on strategy
            switch (analysis.getStrategy()) {
                case UP_TO_DATE:
                    return PullResult.success(analysis.getMessage(), 0);

                case LOCAL_AHEAD:
                    return PullResult.error(analysis.getMessage());

                case DIVERGED:
                    log.info("Branches have diverged, attempting automatic merge...");
                    return performMerge(projectRoot, remoteConfig, credentials,
                            currentBranch, localHead, remoteHead, history);

                case FAST_FORWARD:
                    return performFastForward(projectRoot, remoteConfig, credentials,
                            currentBranch, analysis, remoteHead);

                default:
                    return PullResult.error("Unknown pull strategy");
            }

        } catch (Exception e) {
            log.error("Pull failed", e);
            return PullResult.error("Pull failed: " + e.getMessage());
        }
    }

    public PullResult pullRelease(File projectRoot,
                                  RemoteConfig remoteConfig,
                                  CredentialsEntry credentials,
                                  String releaseVersion) {
        return applyRelease(projectRoot, remoteConfig, credentials, releaseVersion, false);
    }

    public PullResult revertRelease(File projectRoot,
                                    RemoteConfig remoteConfig,
                                    CredentialsEntry credentials,
                                    String releaseVersion) {
        return applyRelease(projectRoot, remoteConfig, credentials, releaseVersion, true);
    }

    private PullResult applyRelease(File projectRoot,
                                    RemoteConfig remoteConfig,
                                    CredentialsEntry credentials,
                                    String releaseVersion,
                                    boolean allowOlder) {
        try {
            ReleaseResponseDto release = "latest".equalsIgnoreCase(releaseVersion)
                    ? remoteCloneService.getLatestRelease(remoteConfig, credentials)
                    : remoteCloneService.getRelease(remoteConfig, credentials, releaseVersion);

            Optional<ReleaseState> localRelease = ReleaseState.load(projectRoot);
            if (localRelease.isPresent()) {
                String localVersion = localRelease.get().getVersion();
                String requestedVersion = release.getVersion();
                int comparison = SemverUtils.compare(requestedVersion, localVersion);
                if (comparison < 0 && !allowOlder) {
                    return PullResult.error(
                            "Requested release is older than current release. Use revert instead."
                    );
                }
                if (comparison > 0 && allowOlder) {
                    return PullResult.error(
                            "Requested release is newer than current release. Use pull --release instead."
                    );
                }
                if (comparison == 0 && localRelease.get().getCommitId() != null
                        && localRelease.get().getCommitId().equals(release.getSnapshotCommitId())) {
                    return PullResult.success("Already on release " + requestedVersion, 0);
                }
            }

            String snapshotCommitId = release.getSnapshotCommitId();
            if (snapshotCommitId == null || snapshotCommitId.isBlank()) {
                return PullResult.error("Release has no snapshot commit: " + release.getVersion());
            }

            CommitSnapshotDto snapshot = remoteCloneService.getCommit(
                    remoteConfig, credentials, snapshotCommitId
            );

            if (snapshot.getFiles() == null || snapshot.getFiles().isEmpty()) {
                return PullResult.error("Release snapshot has no files: " + release.getVersion());
            }

            Set<String> blobHashes = new HashSet<>(snapshot.getFiles().values());
            Set<String> blobsToDownload = new HashSet<>();
            for (String hash : blobHashes) {
                File blobFile = getBlobFile(projectRoot, hash);
                if (!blobFile.exists()) {
                    blobsToDownload.add(hash);
                }
            }

            Map<String, String> allObjects = new HashMap<>();
            if (!blobsToDownload.isEmpty()) {
                List<String> hashList = new ArrayList<>(blobsToDownload);
                int batchSize = 50;

                for (int i = 0; i < hashList.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, hashList.size());
                    List<String> batch = hashList.subList(i, end);

                    BatchObjectsResponseDto batchResponse = remoteCloneService.getBatchObjects(
                            remoteConfig, credentials, batch
                    );
                    allObjects.putAll(batchResponse.getObjects());
                }
            }

            for (Map.Entry<String, String> entry : allObjects.entrySet()) {
                String hash = entry.getKey();
                String base64Content = entry.getValue();

                byte[] content = Base64.getDecoder().decode(base64Content);

                File blobFile = getBlobFile(projectRoot, hash);
                blobFile.getParentFile().mkdirs();
                Files.write(blobFile.toPath(), content);
            }

            File commitFile = new File(projectRoot, ".vcs/commits/" + snapshot.getId() + ".json");
            if (!commitFile.exists()) {
                String commitJson = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(snapshot);
                Files.writeString(commitFile.toPath(), commitJson);
            }

            checkoutService.checkoutCommit(projectRoot, snapshotCommitId);
            ReleaseState.save(projectRoot, new ReleaseState(release.getVersion(), snapshotCommitId));

            return PullResult.success("Checked out release " + release.getVersion(), 1);
        } catch (Exception e) {
            log.error("Release checkout failed", e);
            return PullResult.error("Release checkout failed: " + e.getMessage());
        }
    }

    private PullResult performFastForward(File projectRoot,
                                          RemoteConfig remoteConfig,
                                          CredentialsEntry credentials,
                                          String branch,
                                          PullAnalysis analysis,
                                          String remoteHead) throws Exception {

        List<CommitSnapshotDto> newCommits = analysis.getNewCommits();

        log.info("Performing fast-forward: {} new commits", newCommits.size());

        // 1. Collect all blob hashes from new commits
        Set<String> newBlobHashes = new HashSet<>();
        for (CommitSnapshotDto commit : newCommits) {
            if (commit.getFiles() != null) {
                newBlobHashes.addAll(commit.getFiles().values());
            }
        }

        // Filter out blobs that already exist locally
        Set<String> blobsToDownload = new HashSet<>();
        for (String hash : newBlobHashes) {
            File blobFile = getBlobFile(projectRoot, hash);
            if (!blobFile.exists()) {
                blobsToDownload.add(hash);
            }
        }

        log.info("Downloading {} new objects", blobsToDownload.size());

        // 2. Download new blobs in batches
        Map<String, String> allObjects = new HashMap<>();
        if (!blobsToDownload.isEmpty()) {
            List<String> hashList = new ArrayList<>(blobsToDownload);
            int batchSize = 50;

            for (int i = 0; i < hashList.size(); i += batchSize) {
                int end = Math.min(i + batchSize, hashList.size());
                List<String> batch = hashList.subList(i, end);

                BatchObjectsResponseDto batchResponse = remoteCloneService.getBatchObjects(
                        remoteConfig, credentials, batch
                );
                allObjects.putAll(batchResponse.getObjects());

                log.info("Downloaded {}/{} objects", allObjects.size(), blobsToDownload.size());
            }
        }

        // 3. Write new blobs to local storage
        for (Map.Entry<String, String> entry : allObjects.entrySet()) {
            String hash = entry.getKey();
            String base64Content = entry.getValue();

            byte[] content = Base64.getDecoder().decode(base64Content);

            File blobFile = getBlobFile(projectRoot, hash);
            blobFile.getParentFile().mkdirs();
            Files.write(blobFile.toPath(), content);
        }

        // 4. Write new commits to local storage
        for (CommitSnapshotDto commit : newCommits) {
            File commitFile = new File(projectRoot, ".vcs/commits/" + commit.getId() + ".json");
            String commitJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(commit);
            Files.writeString(commitFile.toPath(), commitJson);
        }

        // 5. Update branch HEAD
        File branchRefFile = new File(projectRoot, ".vcs/refs/heads/" + branch);
        Files.writeString(branchRefFile.toPath(), remoteHead);

        log.info("Updated branch {} to {}", branch, remoteHead);

        // 6. Checkout latest commit (update working directory)
        CommitSnapshotDto latestCommit = newCommits.get(0);
        List<String> changedFiles = checkoutCommit(projectRoot, latestCommit, allObjects);

        return PullResult.builder()
                .success(true)
                .message("Fast-forward: " + analysis.getLocalHead() + ".." + remoteHead)
                .commitsDownloaded(newCommits.size())
                .changedFiles(changedFiles)
                .build();
    }

    private File getBlobFile(File projectRoot, String hash) {
        String prefix = hash.substring(0, 2);
        String suffix = hash.substring(2);
        return new File(projectRoot, ".vcs/objects/" + prefix + "/" + suffix);
    }

    private List<String> checkoutCommit(File projectRoot,
                                         CommitSnapshotDto commit,
                                         Map<String, String> objects) throws Exception {

        List<String> changedFiles = new ArrayList<>();

        if (commit.getFiles() == null || commit.getFiles().isEmpty()) {
            return changedFiles;
        }

        // Load and clear the index - we'll rebuild it from the commit
        indexEngine.loadIndex(projectRoot);

        // Save current index entries before clearing (to detect changes)
        Map<String, String> previousIndexEntries = new HashMap<>(indexEngine.getEntries());

        // Remove all files from index (we'll add them back from the commit)
        Map<String, String> currentIndexEntries = indexEngine.getEntries();
        for (String filePath : new ArrayList<>(currentIndexEntries.keySet())) {
            indexEngine.removeFile(filePath);
        }

        for (Map.Entry<String, String> entry : commit.getFiles().entrySet()) {
            String filePath = entry.getKey();
            String blobHash = entry.getValue();

            // Check if this file has changed (new file or different hash)
            String previousHash = previousIndexEntries.get(filePath);
            boolean fileChanged = previousHash == null || !previousHash.equals(blobHash);

            // Try to get from downloaded objects first
            String base64Content = objects.get(blobHash);
            byte[] content;

            if (base64Content != null) {
                content = Base64.getDecoder().decode(base64Content);
            } else {
                // Read from local storage
                File blobFile = getBlobFile(projectRoot, blobHash);
                if (!blobFile.exists()) {
                    log.warn("Missing blob for file: {} (hash: {})", filePath, blobHash);
                    continue;
                }
                content = Files.readAllBytes(blobFile.toPath());
            }

            File file = new File(projectRoot, filePath);
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), content);

            // Update index to reflect the checked out file
            indexEngine.updateFile(filePath, blobHash);

            // Only add to changedFiles if the file actually changed
            if (fileChanged) {
                changedFiles.add(filePath);
            }
        }

        // Save updated index
        indexEngine.saveIndex(projectRoot);

        log.info("Checked out {} files, {} changed", commit.getFiles().size(), changedFiles.size());
        return changedFiles;
    }

    private PullResult performMerge(File projectRoot,
                                     RemoteConfig remoteConfig,
                                     CredentialsEntry credentials,
                                     String branch,
                                     String localHead,
                                     String remoteHead,
                                     CommitHistoryResponseDto history) throws Exception {

        log.info("Performing three-way merge: local={}, remote={}", localHead, remoteHead);

        // 1. Download all remote commits and blobs first
        List<CommitSnapshotDto> remoteCommits = history.getCommits();

        // Collect all blob hashes from remote commits
        Set<String> remoteBlobHashes = new HashSet<>();
        for (CommitSnapshotDto commit : remoteCommits) {
            if (commit.getFiles() != null) {
                remoteBlobHashes.addAll(commit.getFiles().values());
            }
        }

        // Filter out blobs that already exist locally
        Set<String> blobsToDownload = new HashSet<>();
        for (String hash : remoteBlobHashes) {
            File blobFile = getBlobFile(projectRoot, hash);
            if (!blobFile.exists()) {
                blobsToDownload.add(hash);
            }
        }

        log.info("Downloading {} remote objects", blobsToDownload.size());

        // Download new blobs in batches
        Map<String, String> allObjects = new HashMap<>();
        if (!blobsToDownload.isEmpty()) {
            List<String> hashList = new ArrayList<>(blobsToDownload);
            int batchSize = 50;

            for (int i = 0; i < hashList.size(); i += batchSize) {
                int end = Math.min(i + batchSize, hashList.size());
                List<String> batch = hashList.subList(i, end);

                BatchObjectsResponseDto batchResponse = remoteCloneService.getBatchObjects(
                        remoteConfig, credentials, batch
                );
                allObjects.putAll(batchResponse.getObjects());
            }
        }

        // Write new blobs to local storage
        for (Map.Entry<String, String> entry : allObjects.entrySet()) {
            String hash = entry.getKey();
            String base64Content = entry.getValue();

            byte[] content = Base64.getDecoder().decode(base64Content);

            File blobFile = getBlobFile(projectRoot, hash);
            blobFile.getParentFile().mkdirs();
            Files.write(blobFile.toPath(), content);
        }

        // Write new commits to local storage
        for (CommitSnapshotDto commit : remoteCommits) {
            File commitFile = new File(projectRoot, ".vcs/commits/" + commit.getId() + ".json");
            if (!commitFile.exists()) {
                String commitJson = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(commit);
                Files.writeString(commitFile.toPath(), commitJson);
            }
        }

        // 2. Now perform the merge
        MergeResult mergeResult = mergeEngine.merge(projectRoot, localHead, remoteHead, branch);

        // 3. Convert MergeResult to PullResult
        if (mergeResult.isSuccess()) {
            if (mergeResult.getStrategy() == MergeStrategy.THREE_WAY) {
                return PullResult.success(
                        "Merge completed successfully\n" +
                        "Merged " + mergeResult.getAutoMergedFiles().size() + " file(s)\n" +
                        "Merge commit: " + mergeResult.getMergeCommitId(),
                        remoteCommits.size()
                );
            } else {
                return PullResult.success(mergeResult.getMessage(), remoteCommits.size());
            }
        } else {
            if (mergeResult.hasConflicts()) {
                StringBuilder message = new StringBuilder();
                message.append("Automatic merge failed; fix conflicts and run 'chronovcs merge --continue'\n\n");
                message.append("Conflicted files:\n");
                for (String file : mergeResult.getConflictedFiles()) {
                    message.append("  - ").append(file).append("\n");
                }
                message.append("\nAuto-merged files: ").append(mergeResult.getAutoMergedFiles().size());
                return PullResult.error(message.toString());
            } else {
                return PullResult.error(mergeResult.getMessage());
            }
        }
    }
}
