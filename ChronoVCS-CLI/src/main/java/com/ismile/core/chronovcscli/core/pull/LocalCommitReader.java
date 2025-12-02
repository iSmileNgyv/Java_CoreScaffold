package com.ismile.core.chronovcscli.core.pull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcscli.remote.dto.CommitSnapshotDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class LocalCommitReader {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Read current branch HEAD commit hash
     */
    public String getCurrentHead(File projectRoot) throws Exception {
        File headFile = new File(projectRoot, ".vcs/HEAD");
        if (!headFile.exists()) {
            throw new IllegalStateException("No HEAD file found");
        }

        String headContent = Files.readString(headFile.toPath()).trim();

        if (headContent.startsWith("ref:")) {
            String refPath = headContent.substring(4).trim();
            File refFile = new File(projectRoot, ".vcs/" + refPath);

            if (!refFile.exists() || refFile.length() == 0) {
                return null;
            }

            return Files.readString(refFile.toPath()).trim();
        }

        return headContent;
    }

    /**
     * Get current branch name
     */
    public String getCurrentBranch(File projectRoot) throws Exception {
        File headFile = new File(projectRoot, ".vcs/HEAD");
        if (!headFile.exists()) {
            return "main";
        }

        String headContent = Files.readString(headFile.toPath()).trim();

        if (headContent.startsWith("ref: refs/heads/")) {
            return headContent.substring("ref: refs/heads/".length());
        }

        return "main";
    }

    /**
     * Read single commit from local storage
     */
    public CommitSnapshotDto readCommit(File projectRoot, String commitHash) throws Exception {
        if (commitHash == null || commitHash.isEmpty()) {
            return null;
        }

        File commitFile = new File(projectRoot, ".vcs/commits/" + commitHash + ".json");
        if (!commitFile.exists()) {
            log.warn("Commit file not found: {}", commitHash);
            return null;
        }

        String json = Files.readString(commitFile.toPath());
        return objectMapper.readValue(json, CommitSnapshotDto.class);
    }

    /**
     * Read commit history chain starting from given commit
     */
    public List<CommitSnapshotDto> readCommitChain(File projectRoot, String startCommitHash, int maxCommits) {
        List<CommitSnapshotDto> commits = new ArrayList<>();
        String currentHash = startCommitHash;

        while (currentHash != null && commits.size() < maxCommits) {
            try {
                CommitSnapshotDto commit = readCommit(projectRoot, currentHash);
                if (commit == null) {
                    break;
                }

                commits.add(commit);
                currentHash = commit.getParent();
            } catch (Exception e) {
                log.error("Failed to read commit: {}", currentHash, e);
                break;
            }
        }

        return commits;
    }

    /**
     * Check if commit exists locally
     */
    public boolean commitExists(File projectRoot, String commitHash) {
        if (commitHash == null || commitHash.isEmpty()) {
            return false;
        }

        File commitFile = new File(projectRoot, ".vcs/commits/" + commitHash + ".json");
        return commitFile.exists();
    }
}
