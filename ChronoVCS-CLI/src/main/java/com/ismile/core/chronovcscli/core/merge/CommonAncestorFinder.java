package com.ismile.core.chronovcscli.core.merge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcscli.core.commit.CommitModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;

/**
 * Finds the common ancestor (base commit) between two commits
 * Uses a simple graph traversal algorithm
 */
@Slf4j
@Component
public class CommonAncestorFinder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Find the nearest common ancestor of two commits
     *
     * @param projectRoot project root directory
     * @param commit1Id   first commit hash
     * @param commit2Id   second commit hash
     * @return common ancestor commit hash, or null if not found
     */
    public String findCommonAncestor(File projectRoot, String commit1Id, String commit2Id) {
        try {
            // If commits are the same, return it
            if (commit1Id.equals(commit2Id)) {
                return commit1Id;
            }

            // Get all ancestors of commit1
            Set<String> ancestors1 = getAllAncestors(projectRoot, commit1Id);

            // Traverse ancestors of commit2 until we find one in ancestors1
            Queue<String> queue = new LinkedList<>();
            Set<String> visited = new HashSet<>();
            queue.offer(commit2Id);
            visited.add(commit2Id);

            while (!queue.isEmpty()) {
                String current = queue.poll();

                // If this commit is in ancestors1, it's the common ancestor
                if (ancestors1.contains(current)) {
                    log.info("Found common ancestor: {}", current);
                    return current;
                }

                // Add parent to queue
                CommitModel commit = loadCommit(projectRoot, current);
                if (commit != null && commit.getParentCommitId() != null) {
                    String parent = commit.getParentCommitId();
                    if (!visited.contains(parent)) {
                        queue.offer(parent);
                        visited.add(parent);
                    }
                }
            }

            log.warn("No common ancestor found between {} and {}", commit1Id, commit2Id);
            return null;

        } catch (Exception e) {
            log.error("Error finding common ancestor", e);
            return null;
        }
    }

    /**
     * Get all ancestors of a commit
     */
    private Set<String> getAllAncestors(File projectRoot, String commitId) {
        Set<String> ancestors = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.offer(commitId);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (ancestors.contains(current)) {
                continue;
            }
            ancestors.add(current);

            CommitModel commit = loadCommit(projectRoot, current);
            if (commit != null && commit.getParentCommitId() != null) {
                queue.offer(commit.getParentCommitId());
            }
        }

        return ancestors;
    }

    /**
     * Load commit from local storage
     */
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
}
