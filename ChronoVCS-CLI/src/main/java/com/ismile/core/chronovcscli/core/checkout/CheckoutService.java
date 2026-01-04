package com.ismile.core.chronovcscli.core.checkout;

import com.ismile.core.chronovcscli.core.index.IndexEngine;
import com.ismile.core.chronovcscli.core.pull.LocalCommitReader;
import com.ismile.core.chronovcscli.core.status.StatusEngine;
import com.ismile.core.chronovcscli.core.status.StatusResult;
import com.ismile.core.chronovcscli.remote.dto.CommitSnapshotDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutService {

    private final LocalCommitReader localCommitReader;
    private final StatusEngine statusEngine;
    private final IndexEngine indexEngine;

    /**
     * Checkout branch - switch to branch
     */
    public void checkoutBranch(File projectRoot, String branchName) throws Exception {
        checkoutBranchInternal(projectRoot, branchName, false);
    }

    /**
     * Checkout branch and discard local changes.
     */
    public void checkoutBranchHard(File projectRoot, String branchName) throws Exception {
        checkoutBranchInternal(projectRoot, branchName, true);
    }

    private void checkoutBranchInternal(File projectRoot, String branchName, boolean force) throws Exception {
        // Check if branch exists
        File branchFile = new File(projectRoot, ".vcs/refs/heads/" + branchName);
        if (!branchFile.exists()) {
            throw new IllegalArgumentException("Branch '" + branchName + "' not found");
        }

        // Get branch HEAD commit
        String branchHead = Files.readString(branchFile.toPath()).trim();
        if (branchHead.isEmpty()) {
            throw new IllegalStateException("Branch '" + branchName + "' has no commits");
        }

        // Read commit
        CommitSnapshotDto commit = localCommitReader.readCommit(projectRoot, branchHead);
        if (commit == null) {
            throw new IllegalStateException("Commit not found: " + branchHead);
        }

        // Update HEAD to point to branch
        File headFile = new File(projectRoot, ".vcs/HEAD");
        Files.writeString(headFile.toPath(), "ref: refs/heads/" + branchName);

        // Checkout commit files
        checkoutCommitFiles(projectRoot, commit, force);

        log.info("Switched to branch '{}' at commit {}", branchName, branchHead);
    }

    /**
     * Checkout commit - detached HEAD
     */
    public void checkoutCommit(File projectRoot, String commitHash) throws Exception {
        // Read commit
        CommitSnapshotDto commit = localCommitReader.readCommit(projectRoot, commitHash);
        if (commit == null) {
            throw new IllegalArgumentException("Commit not found: " + commitHash);
        }

        // Update HEAD to point directly to commit (detached)
        File headFile = new File(projectRoot, ".vcs/HEAD");
        Files.writeString(headFile.toPath(), commitHash);

        // Checkout commit files
        checkoutCommitFiles(projectRoot, commit, false);

        log.info("HEAD is now at {} (detached)", commitHash);
    }

    /**
     * Restore file from HEAD
     */
    public void restoreFile(File projectRoot, String filePath) throws Exception {
        // Get current HEAD commit
        String currentHead = localCommitReader.getCurrentHead(projectRoot);
        if (currentHead == null) {
            throw new IllegalStateException("No commits yet");
        }

        // Read HEAD commit
        CommitSnapshotDto commit = localCommitReader.readCommit(projectRoot, currentHead);
        if (commit == null) {
            throw new IllegalStateException("HEAD commit not found");
        }

        // Find file in commit
        if (commit.getFiles() == null || !commit.getFiles().containsKey(filePath)) {
            throw new IllegalArgumentException("File '" + filePath + "' not found in HEAD commit");
        }

        String blobHash = commit.getFiles().get(filePath);

        // Read blob content
        byte[] content = readBlob(projectRoot, blobHash);

        // Write to working directory
        File file = new File(projectRoot, filePath);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), content);

        log.info("Restored file: {}", filePath);
    }

    /**
     * Checkout all files from commit
     */
    private void checkoutCommitFiles(File projectRoot, CommitSnapshotDto commit, boolean force) throws Exception {
        if (commit.getFiles() == null || commit.getFiles().isEmpty()) {
            log.warn("Commit has no files");
            return;
        }

        // Check for uncommitted changes before clearing working directory
        if (!force) {
            checkForUncommittedChanges(projectRoot);
        }

        // Clear working directory (except .vcs)
        clearWorkingDirectory(projectRoot);

        // Load and clear the index - we'll rebuild it from the commit
        indexEngine.loadIndex(projectRoot);

        // Remove all files from index (we'll add them back from the commit)
        Map<String, String> currentIndexEntries = indexEngine.getEntries();
        for (String filePath : new java.util.ArrayList<>(currentIndexEntries.keySet())) {
            indexEngine.removeFile(filePath);
        }

        // Checkout all files and update index
        for (Map.Entry<String, String> entry : commit.getFiles().entrySet()) {
            String filePath = entry.getKey();
            String blobHash = entry.getValue();

            try {
                byte[] content = readBlob(projectRoot, blobHash);

                File file = new File(projectRoot, filePath);
                file.getParentFile().mkdirs();
                Files.write(file.toPath(), content);

                // Update index to reflect the checked out file
                indexEngine.updateFile(filePath, blobHash);
            } catch (Exception e) {
                log.error("Failed to checkout file: {}", filePath, e);
            }
        }

        // Save updated index
        indexEngine.saveIndex(projectRoot);

        log.info("Checked out {} files and updated index", commit.getFiles().size());
    }

    /**
     * Check for uncommitted changes and prevent checkout if any exist
     */
    private void checkForUncommittedChanges(File projectRoot) throws Exception {
        StatusResult status = statusEngine.getStatus(projectRoot);

        boolean hasUncommittedChanges = !status.getUntracked().isEmpty()
                || !status.getModified().isEmpty()
                || !status.getDeleted().isEmpty();

        if (hasUncommittedChanges) {
            StringBuilder errorMsg = new StringBuilder();
            errorMsg.append("Error: Your local changes to the following files would be overwritten by checkout:\n");

            if (!status.getModified().isEmpty()) {
                errorMsg.append("\nModified files:\n");
                for (String file : status.getModified()) {
                    errorMsg.append("  ").append(file).append("\n");
                }
            }

            if (!status.getDeleted().isEmpty()) {
                errorMsg.append("\nDeleted files:\n");
                for (String file : status.getDeleted()) {
                    errorMsg.append("  ").append(file).append("\n");
                }
            }

            if (!status.getUntracked().isEmpty()) {
                errorMsg.append("\nUntracked files:\n");
                for (String file : status.getUntracked()) {
                    errorMsg.append("  ").append(file).append("\n");
                }
            }

            errorMsg.append("\nPlease commit your changes or stash them before you switch branches.\n");
            errorMsg.append("Use 'chronovcs commit' to commit your changes.");

            throw new IllegalStateException(errorMsg.toString());
        }
    }

    /**
     * Read blob from local storage
     */
    private byte[] readBlob(File projectRoot, String hash) throws Exception {
        String prefix = hash.substring(0, 2);
        String suffix = hash.substring(2);

        File blobFile = new File(projectRoot, ".vcs/objects/" + prefix + "/" + suffix);
        if (!blobFile.exists()) {
            throw new IllegalStateException("Blob not found: " + hash);
        }

        return Files.readAllBytes(blobFile.toPath());
    }

    /**
     * Clear working directory (except .vcs and .chronoignore)
     */
    private void clearWorkingDirectory(File projectRoot) {
        File[] files = projectRoot.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            String name = file.getName();

            // Skip .vcs and special files
            if (name.equals(".vcs") || name.equals(".chronoignore") || name.equals(".gitignore")) {
                continue;
            }

            // Delete file or directory
            deleteRecursively(file);
        }
    }

    /**
     * Delete file or directory recursively
     */
    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }

        if (!file.delete()) {
            log.warn("Failed to delete: {}", file.getPath());
        }
    }

    /**
     * Check if HEAD is detached
     */
    public boolean isDetachedHead(File projectRoot) throws Exception {
        File headFile = new File(projectRoot, ".vcs/HEAD");
        if (!headFile.exists()) {
            return false;
        }

        String headContent = Files.readString(headFile.toPath()).trim();
        return !headContent.startsWith("ref:");
    }
}
