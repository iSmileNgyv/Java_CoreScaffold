package com.ismile.core.chronovcscli.core.pull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcscli.auth.CredentialsEntry;
import com.ismile.core.chronovcscli.remote.RemoteCloneService;
import com.ismile.core.chronovcscli.remote.RemoteConfig;
import com.ismile.core.chronovcscli.remote.dto.BatchObjectsResponseDto;
import com.ismile.core.chronovcscli.remote.dto.CommitHistoryResponseDto;
import com.ismile.core.chronovcscli.remote.dto.CommitSnapshotDto;
import com.ismile.core.chronovcscli.remote.dto.RefsResponseDto;
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
                    return PullResult.error(
                            "Local and remote have diverged.\n" +
                            "Local HEAD: " + localHead + "\n" +
                            "Remote HEAD: " + remoteHead + "\n" +
                            "Please push your changes or manually merge."
                    );

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
            File commitFile = new File(projectRoot, ".vcs/commits/" + commit.getId());
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
        checkoutCommit(projectRoot, latestCommit, allObjects);

        return PullResult.success(
                "Fast-forward: " + analysis.getLocalHead() + ".." + remoteHead,
                newCommits.size()
        );
    }

    private File getBlobFile(File projectRoot, String hash) {
        String prefix = hash.substring(0, 2);
        String suffix = hash.substring(2);
        return new File(projectRoot, ".vcs/objects/" + prefix + "/" + suffix);
    }

    private void checkoutCommit(File projectRoot,
                                 CommitSnapshotDto commit,
                                 Map<String, String> objects) throws Exception {

        if (commit.getFiles() == null || commit.getFiles().isEmpty()) {
            return;
        }

        int checkedOut = 0;

        for (Map.Entry<String, String> entry : commit.getFiles().entrySet()) {
            String filePath = entry.getKey();
            String blobHash = entry.getValue();

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
            checkedOut++;
        }

        log.info("Checked out {} files", checkedOut);
    }
}
