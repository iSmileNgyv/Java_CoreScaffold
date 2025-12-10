package com.ismile.core.chronovcscli.core.merge.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcscli.core.commit.CommitEngine;
import com.ismile.core.chronovcscli.core.commit.CommitModel;
import com.ismile.core.chronovcscli.core.merge.*;
import com.ismile.core.chronovcscli.core.objectsStore.ObjectStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Core merge engine implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MergeEngineImpl implements MergeEngine {

    private final CommonAncestorFinder ancestorFinder;
    private final ThreeWayMerge threeWayMerge;
    private final ConflictMarker conflictMarker;
    private final ObjectStore objectStore;
    private final CommitEngine commitEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public MergeResult merge(File projectRoot, String localCommitId, String remoteCommitId, String branch) {
        try {
            log.info("Starting merge: local={}, remote={}", localCommitId, remoteCommitId);

            // 1. Check if already up to date
            if (localCommitId.equals(remoteCommitId)) {
                return MergeResult.success(MergeStrategy.UP_TO_DATE, "Already up to date");
            }

            // 2. Find common ancestor
            String baseCommitId = ancestorFinder.findCommonAncestor(projectRoot, localCommitId, remoteCommitId);
            if (baseCommitId == null) {
                return MergeResult.error("No common ancestor found - cannot merge");
            }

            log.info("Common ancestor: {}", baseCommitId);

            // 3. Check if fast-forward possible
            if (baseCommitId.equals(localCommitId)) {
                return MergeResult.builder()
                        .success(true)
                        .strategy(MergeStrategy.FAST_FORWARD)
                        .message("Fast-forward merge possible")
                        .baseCommitId(baseCommitId)
                        .localCommitId(localCommitId)
                        .remoteCommitId(remoteCommitId)
                        .build();
            }

            // 4. Load commits
            CommitModel baseCommit = loadCommit(projectRoot, baseCommitId);
            CommitModel localCommit = loadCommit(projectRoot, localCommitId);
            CommitModel remoteCommit = loadCommit(projectRoot, remoteCommitId);

            if (baseCommit == null || localCommit == null || remoteCommit == null) {
                return MergeResult.error("Failed to load commits");
            }

            // 5. Perform three-way merge
            return performThreeWayMerge(projectRoot, branch,
                    baseCommit, localCommit, remoteCommit);

        } catch (Exception e) {
            log.error("Merge failed", e);
            return MergeResult.error("Merge failed: " + e.getMessage());
        }
    }

    private MergeResult performThreeWayMerge(File projectRoot, String branch,
                                              CommitModel baseCommit,
                                              CommitModel localCommit,
                                              CommitModel remoteCommit) throws Exception {

        Map<String, String> baseFiles = baseCommit.getFiles() != null ? baseCommit.getFiles() : new HashMap<>();
        Map<String, String> localFiles = localCommit.getFiles() != null ? localCommit.getFiles() : new HashMap<>();
        Map<String, String> remoteFiles = remoteCommit.getFiles() != null ? remoteCommit.getFiles() : new HashMap<>();

        // Collect all file paths
        Set<String> allFiles = new HashSet<>();
        allFiles.addAll(baseFiles.keySet());
        allFiles.addAll(localFiles.keySet());
        allFiles.addAll(remoteFiles.keySet());

        List<ConflictInfo> conflicts = new ArrayList<>();
        List<String> autoMergedFiles = new ArrayList<>();
        Map<String, String> mergedFiles = new HashMap<>();

        // Process each file
        for (String filePath : allFiles) {
            String baseHash = baseFiles.get(filePath);
            String localHash = localFiles.get(filePath);
            String remoteHash = remoteFiles.get(filePath);

            log.debug("Merging file: {} (base={}, local={}, remote={})",
                    filePath, baseHash, localHash, remoteHash);

            // Case 1: File unchanged in both branches
            if (Objects.equals(localHash, remoteHash)) {
                if (localHash != null) {
                    mergedFiles.put(filePath, localHash);
                }
                continue;
            }

            // Case 2: File only changed in remote
            if (Objects.equals(baseHash, localHash) && remoteHash != null) {
                mergedFiles.put(filePath, remoteHash);
                autoMergedFiles.add(filePath);
                log.debug("Auto-merged (remote changed): {}", filePath);
                continue;
            }

            // Case 3: File only changed in local
            if (Objects.equals(baseHash, remoteHash) && localHash != null) {
                mergedFiles.put(filePath, localHash);
                autoMergedFiles.add(filePath);
                log.debug("Auto-merged (local changed): {}", filePath);
                continue;
            }

            // Case 4: File deleted in one branch
            if (localHash == null || remoteHash == null) {
                conflicts.add(ConflictInfo.builder()
                        .filePath(filePath)
                        .baseHash(baseHash)
                        .localHash(localHash)
                        .remoteHash(remoteHash)
                        .conflictType(ConflictInfo.ConflictType.DELETE_MODIFY)
                        .autoResolved(false)
                        .build());
                log.debug("Conflict (delete-modify): {}", filePath);
                continue;
            }

            // Case 5: File changed in both branches - attempt merge
            byte[] baseContent = baseHash != null ? objectStore.readBlob(baseHash) : new byte[0];
            byte[] localContent = objectStore.readBlob(localHash);
            byte[] remoteContent = objectStore.readBlob(remoteHash);

            ThreeWayMerge.MergeAttempt attempt = threeWayMerge.merge(baseContent, localContent, remoteContent);

            if (attempt.hasConflict) {
                // Create conflict markers in working directory
                File targetFile = new File(projectRoot, filePath);
                conflictMarker.writeConflictFile(targetFile, localContent, remoteContent, "remote/" + branch);

                conflicts.add(ConflictInfo.builder()
                        .filePath(filePath)
                        .baseHash(baseHash)
                        .localHash(localHash)
                        .remoteHash(remoteHash)
                        .conflictType(ConflictInfo.ConflictType.MODIFY_MODIFY)
                        .autoResolved(false)
                        .build());
                log.debug("Conflict (modify-modify): {}", filePath);
            } else {
                // Auto-merged successfully
                File targetFile = new File(projectRoot, filePath);
                targetFile.getParentFile().mkdirs();
                Files.write(targetFile.toPath(), attempt.mergedContent);

                // Write blob using ObjectStore (pass the file, not content)
                String mergedHash = objectStore.writeBlob(targetFile);
                mergedFiles.put(filePath, mergedHash);
                autoMergedFiles.add(filePath);
                log.debug("Auto-merged (content merge): {}", filePath);
            }
        }

        // If there are conflicts, save merge state and return
        if (!conflicts.isEmpty()) {
            saveMergeState(projectRoot, MergeState.builder()
                    .localCommitId(localCommit.getId())
                    .remoteCommitId(remoteCommit.getId())
                    .baseCommitId(baseCommit.getId())
                    .branch(branch)
                    .mergeMessage("Merge remote-tracking branch '" + branch + "'")
                    .conflictedFiles(conflicts.stream()
                            .map(ConflictInfo::getFilePath)
                            .toList())
                    .inProgress(true)
                    .build());

            return MergeResult.builder()
                    .success(false)
                    .strategy(MergeStrategy.CONFLICT)
                    .baseCommitId(baseCommit.getId())
                    .localCommitId(localCommit.getId())
                    .remoteCommitId(remoteCommit.getId())
                    .conflicts(conflicts)
                    .autoMergedFiles(autoMergedFiles)
                    .message(String.format("Automatic merge failed; fix conflicts in %d file(s) and run 'chronovcs merge --continue'",
                            conflicts.size()))
                    .build();
        }

        // No conflicts - create merge commit
        String mergeCommitId = createMergeCommit(projectRoot, branch,
                localCommit.getId(), remoteCommit.getId(),
                "Merge remote-tracking branch '" + branch + "'",
                mergedFiles);

        return MergeResult.builder()
                .success(true)
                .strategy(MergeStrategy.THREE_WAY)
                .baseCommitId(baseCommit.getId())
                .localCommitId(localCommit.getId())
                .remoteCommitId(remoteCommit.getId())
                .mergeCommitId(mergeCommitId)
                .autoMergedFiles(autoMergedFiles)
                .message(String.format("Merge completed successfully. %d file(s) merged.", autoMergedFiles.size()))
                .build();
    }

    @Override
    public MergeResult continueMerge(File projectRoot) {
        try {
            MergeState state = getMergeState(projectRoot);
            if (state == null || !state.isInProgress()) {
                return MergeResult.error("No merge in progress");
            }

            // Check if all conflicts are resolved
            if (conflictMarker.hasAnyConflicts(projectRoot, state.getConflictedFiles())) {
                return MergeResult.error("Cannot continue merge: conflicts still present in files");
            }

            // Load current index to get resolved file hashes
            Map<String, String> mergedFiles = loadIndexFiles(projectRoot);

            // Create merge commit
            String mergeCommitId = createMergeCommit(projectRoot, state.getBranch(),
                    state.getLocalCommitId(), state.getRemoteCommitId(),
                    state.getMergeMessage(), mergedFiles);

            // Clean up merge state
            deleteMergeState(projectRoot);

            return MergeResult.builder()
                    .success(true)
                    .strategy(MergeStrategy.THREE_WAY)
                    .mergeCommitId(mergeCommitId)
                    .message("Merge completed successfully: " + mergeCommitId)
                    .build();

        } catch (Exception e) {
            log.error("Continue merge failed", e);
            return MergeResult.error("Failed to continue merge: " + e.getMessage());
        }
    }

    @Override
    public boolean abortMerge(File projectRoot) {
        try {
            MergeState state = getMergeState(projectRoot);
            if (state == null) {
                return false;
            }

            // Restore working directory to local HEAD
            CommitModel localCommit = loadCommit(projectRoot, state.getLocalCommitId());
            if (localCommit != null && localCommit.getFiles() != null) {
                for (Map.Entry<String, String> entry : localCommit.getFiles().entrySet()) {
                    File file = new File(projectRoot, entry.getKey());
                    byte[] content = objectStore.readBlob(entry.getValue());
                    file.getParentFile().mkdirs();
                    Files.write(file.toPath(), content);
                }
            }

            // Delete merge state
            deleteMergeState(projectRoot);

            log.info("Merge aborted, HEAD reset to {}", state.getLocalCommitId());
            return true;

        } catch (Exception e) {
            log.error("Abort merge failed", e);
            return false;
        }
    }

    @Override
    public boolean isMergeInProgress(File projectRoot) {
        MergeState state = getMergeState(projectRoot);
        return state != null && state.isInProgress();
    }

    @Override
    public MergeState getMergeState(File projectRoot) {
        try {
            File stateFile = new File(projectRoot, ".vcs/MERGE_STATE");
            if (!stateFile.exists()) {
                return null;
            }
            return objectMapper.readValue(stateFile, MergeState.class);
        } catch (Exception e) {
            log.error("Error reading merge state", e);
            return null;
        }
    }

    private void saveMergeState(File projectRoot, MergeState state) throws Exception {
        File stateFile = new File(projectRoot, ".vcs/MERGE_STATE");
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(stateFile, state);
        log.info("Saved merge state");
    }

    private void deleteMergeState(File projectRoot) {
        File stateFile = new File(projectRoot, ".vcs/MERGE_STATE");
        if (stateFile.exists()) {
            stateFile.delete();
            log.info("Deleted merge state");
        }
    }

    private String createMergeCommit(File projectRoot, String branch,
                                      String parent1, String parent2,
                                      String message, Map<String, String> files) throws Exception {

        String commitId = UUID.randomUUID().toString();

        CommitModel mergeCommit = CommitModel.builder()
                .id(commitId)
                .parent(parent1)
                .mergeParent(parent2)  // Second parent for merge commit
                .message(message)
                .timestamp(LocalDateTime.now().toString())
                .files(files)
                .build();

        // Save commit
        File commitFile = new File(projectRoot, ".vcs/commits/" + commitId);
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(commitFile, mergeCommit);

        // Update branch HEAD
        File branchFile = new File(projectRoot, ".vcs/refs/heads/" + branch);
        Files.writeString(branchFile.toPath(), commitId);

        log.info("Created merge commit: {}", commitId);
        return commitId;
    }

    private CommitModel loadCommit(File projectRoot, String commitId) {
        try {
            File commitFile = new File(projectRoot, ".vcs/commits/" + commitId);
            if (!commitFile.exists()) {
                return null;
            }
            return objectMapper.readValue(commitFile, CommitModel.class);
        } catch (Exception e) {
            log.error("Error loading commit {}", commitId, e);
            return null;
        }
    }

    private Map<String, String> loadIndexFiles(File projectRoot) throws Exception {
        File indexFile = new File(projectRoot, ".vcs/index");
        if (!indexFile.exists()) {
            return new HashMap<>();
        }
        String json = Files.readString(indexFile.toPath());
        return objectMapper.readValue(json, objectMapper.getTypeFactory()
                .constructMapType(HashMap.class, String.class, String.class));
    }
}
