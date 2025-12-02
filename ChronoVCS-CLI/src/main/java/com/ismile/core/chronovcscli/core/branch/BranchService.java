package com.ismile.core.chronovcscli.core.branch;

import com.ismile.core.chronovcscli.core.pull.LocalCommitReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BranchService {

    private final LocalCommitReader localCommitReader;

    /**
     * List all branches
     */
    public List<BranchInfo> listBranches(File projectRoot) throws Exception {
        File headsDir = new File(projectRoot, ".vcs/refs/heads");

        if (!headsDir.exists() || !headsDir.isDirectory()) {
            return Collections.emptyList();
        }

        String currentBranch = localCommitReader.getCurrentBranch(projectRoot);
        String currentHead = localCommitReader.getCurrentHead(projectRoot);

        List<BranchInfo> branches = new ArrayList<>();
        File[] branchFiles = headsDir.listFiles();

        if (branchFiles != null) {
            for (File branchFile : branchFiles) {
                if (branchFile.isFile()) {
                    String branchName = branchFile.getName();
                    String commitHash = Files.readString(branchFile.toPath()).trim();

                    boolean isCurrent = branchName.equals(currentBranch);

                    branches.add(BranchInfo.builder()
                            .name(branchName)
                            .commitHash(commitHash.isEmpty() ? null : commitHash)
                            .isCurrent(isCurrent)
                            .build());
                }
            }
        }

        branches.sort((a, b) -> {
            if (a.isCurrent()) return -1;
            if (b.isCurrent()) return 1;
            return a.getName().compareTo(b.getName());
        });

        return branches;
    }

    /**
     * Create new branch from current HEAD
     */
    public void createBranch(File projectRoot, String branchName) throws Exception {
        // Validate branch name
        if (branchName == null || branchName.isEmpty()) {
            throw new IllegalArgumentException("Branch name cannot be empty");
        }

        if (branchName.contains("/") || branchName.contains("\\")) {
            throw new IllegalArgumentException("Invalid branch name: " + branchName);
        }

        // Check if branch already exists
        File branchFile = new File(projectRoot, ".vcs/refs/heads/" + branchName);
        if (branchFile.exists()) {
            throw new IllegalStateException("Branch '" + branchName + "' already exists");
        }

        // Get current HEAD commit
        String currentHead = localCommitReader.getCurrentHead(projectRoot);
        if (currentHead == null || currentHead.isEmpty()) {
            throw new IllegalStateException("No commits yet - cannot create branch");
        }

        // Create branch file pointing to current HEAD
        branchFile.getParentFile().mkdirs();
        Files.writeString(branchFile.toPath(), currentHead);

        log.info("Created branch '{}' at commit {}", branchName, currentHead);
    }

    /**
     * Delete branch
     */
    public void deleteBranch(File projectRoot, String branchName, boolean force) throws Exception {
        // Cannot delete current branch
        String currentBranch = localCommitReader.getCurrentBranch(projectRoot);
        if (branchName.equals(currentBranch)) {
            throw new IllegalStateException("Cannot delete current branch '" + branchName + "'");
        }

        // Check if branch exists
        File branchFile = new File(projectRoot, ".vcs/refs/heads/" + branchName);
        if (!branchFile.exists()) {
            throw new IllegalArgumentException("Branch '" + branchName + "' not found");
        }

        // TODO: Check if branch is merged (unless force)
        // For now, always allow deletion

        // Delete branch file
        if (!branchFile.delete()) {
            throw new IllegalStateException("Failed to delete branch '" + branchName + "'");
        }

        log.info("Deleted branch '{}'", branchName);
    }

    /**
     * Rename branch
     */
    public void renameBranch(File projectRoot, String oldName, String newName) throws Exception {
        // Validate new name
        if (newName == null || newName.isEmpty()) {
            throw new IllegalArgumentException("New branch name cannot be empty");
        }

        // Check if old branch exists
        File oldBranchFile = new File(projectRoot, ".vcs/refs/heads/" + oldName);
        if (!oldBranchFile.exists()) {
            throw new IllegalArgumentException("Branch '" + oldName + "' not found");
        }

        // Check if new branch already exists
        File newBranchFile = new File(projectRoot, ".vcs/refs/heads/" + newName);
        if (newBranchFile.exists()) {
            throw new IllegalStateException("Branch '" + newName + "' already exists");
        }

        // Rename file
        if (!oldBranchFile.renameTo(newBranchFile)) {
            throw new IllegalStateException("Failed to rename branch");
        }

        // If renaming current branch, update HEAD
        String currentBranch = localCommitReader.getCurrentBranch(projectRoot);
        if (oldName.equals(currentBranch)) {
            File headFile = new File(projectRoot, ".vcs/HEAD");
            Files.writeString(headFile.toPath(), "ref: refs/heads/" + newName);
        }

        log.info("Renamed branch '{}' to '{}'", oldName, newName);
    }

    /**
     * Get current branch info
     */
    public BranchInfo getCurrentBranch(File projectRoot) throws Exception {
        String branchName = localCommitReader.getCurrentBranch(projectRoot);
        String commitHash = localCommitReader.getCurrentHead(projectRoot);

        return BranchInfo.builder()
                .name(branchName)
                .commitHash(commitHash)
                .isCurrent(true)
                .build();
    }
}
