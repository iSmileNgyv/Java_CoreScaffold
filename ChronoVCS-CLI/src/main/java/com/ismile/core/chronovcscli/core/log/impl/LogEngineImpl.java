package com.ismile.core.chronovcscli.core.log.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcscli.core.commit.CommitModel;
import com.ismile.core.chronovcscli.core.log.LogEngine;
import com.ismile.core.chronovcscli.core.log.LogEntry;
import com.ismile.core.chronovcscli.core.log.LogOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Implementation of log engine
 */
@Slf4j
@Service
public class LogEngineImpl implements LogEngine {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter DISPLAY_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public List<LogEntry> getLog(File projectRoot, LogOptions options) {
        try {
            // Get current HEAD commit
            String headCommitId = getCurrentHead(projectRoot);
            if (headCommitId == null) {
                return new ArrayList<>();
            }

            // Get branch references for decoration
            Map<String, List<String>> commitToBranches = getCommitToBranchesMap(projectRoot);

            // Traverse commit history
            List<LogEntry> entries = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            Queue<String> queue = new LinkedList<>();
            queue.offer(headCommitId);

            while (!queue.isEmpty() && (options.getLimit() == null || entries.size() < options.getLimit())) {
                String commitId = queue.poll();

                if (visited.contains(commitId)) {
                    continue;
                }
                visited.add(commitId);

                CommitModel commit = loadCommit(projectRoot, commitId);
                if (commit == null) {
                    continue;
                }

                // Filter by file if specified
                if (options.getFilePath() != null) {
                    if (commit.getFiles() == null || !commit.getFiles().containsKey(options.getFilePath())) {
                        // File not in this commit, skip
                        if (commit.getParent() != null) {
                            queue.offer(commit.getParent());
                        }
                        continue;
                    }
                }

                // Create log entry
                LogEntry entry = LogEntry.builder()
                        .commitId(commit.getId())
                        .parentId(commit.getParent())
                        .mergeParentId(commit.getMergeParent())
                        .message(commit.getMessage())
                        .timestamp(commit.getTimestamp())
                        .files(commit.getFiles())
                        .isHead(commitId.equals(headCommitId))
                        .branches(commitToBranches.getOrDefault(commitId, new ArrayList<>()))
                        .build();

                entries.add(entry);

                // Add parents to queue
                if (commit.getParent() != null) {
                    queue.offer(commit.getParent());
                }
                if (commit.getMergeParent() != null) {
                    queue.offer(commit.getMergeParent());
                }
            }

            return entries;

        } catch (Exception e) {
            log.error("Error reading log", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<LogEntry> getLog(File projectRoot) {
        return getLog(projectRoot, LogOptions.builder().build());
    }

    @Override
    public String formatLog(List<LogEntry> entries, LogOptions.LogFormat format) {
        if (entries.isEmpty()) {
            return "No commits yet.";
        }

        StringBuilder sb = new StringBuilder();

        for (LogEntry entry : entries) {
            switch (format) {
                case ONELINE:
                    sb.append(formatOneLine(entry));
                    break;
                case SHORT:
                    sb.append(formatShort(entry));
                    break;
                case FULL:
                default:
                    sb.append(formatFull(entry));
                    break;
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String formatFull(LogEntry entry) {
        StringBuilder sb = new StringBuilder();

        // Commit line with decorations
        sb.append("commit ").append(entry.getCommitId());

        // Add HEAD and branch decorations
        if (entry.isHead() || !entry.getBranches().isEmpty()) {
            sb.append(" (");
            List<String> decorations = new ArrayList<>();
            if (entry.isHead()) {
                decorations.add("HEAD");
            }
            decorations.addAll(entry.getBranches());
            sb.append(String.join(", ", decorations));
            sb.append(")");
        }
        sb.append("\n");

        // Merge info
        if (entry.isMergeCommit()) {
            sb.append("Merge: ")
              .append(entry.getParentId().substring(0, 8))
              .append(" ")
              .append(entry.getMergeParentId().substring(0, 8))
              .append("\n");
        }

        // Date
        sb.append("Date:   ").append(formatTimestamp(entry.getTimestamp())).append("\n");

        // Message (indented)
        sb.append("\n");
        sb.append("    ").append(entry.getMessage()).append("\n");

        return sb.toString();
    }

    private String formatShort(LogEntry entry) {
        StringBuilder sb = new StringBuilder();

        sb.append(entry.getShortCommitId()).append(" ");

        // Add HEAD marker
        if (entry.isHead()) {
            sb.append("(HEAD) ");
        }

        sb.append(formatTimestamp(entry.getTimestamp())).append(" ");
        sb.append(entry.getMessage());

        return sb.toString();
    }

    private String formatOneLine(LogEntry entry) {
        StringBuilder sb = new StringBuilder();

        sb.append(entry.getShortCommitId()).append(" ");

        // Add decorations
        if (entry.isHead() || !entry.getBranches().isEmpty()) {
            sb.append("(");
            List<String> decorations = new ArrayList<>();
            if (entry.isHead()) {
                decorations.add("HEAD");
            }
            decorations.addAll(entry.getBranches());
            sb.append(String.join(", ", decorations));
            sb.append(") ");
        }

        sb.append(entry.getMessage());

        return sb.toString();
    }

    private String formatTimestamp(String timestamp) {
        try {
            LocalDateTime dateTime = LocalDateTime.parse(timestamp);
            return dateTime.format(DISPLAY_FORMATTER);
        } catch (DateTimeParseException e) {
            // If parsing fails, return as-is
            return timestamp;
        }
    }

    private String getCurrentHead(File projectRoot) {
        try {
            File headFile = new File(projectRoot, ".vcs/HEAD");
            if (!headFile.exists()) {
                return null;
            }

            String ref = Files.readString(headFile.toPath()).trim();
            if (!ref.startsWith("ref:")) {
                return ref; // Detached HEAD
            }

            String refPath = ref.replace("ref: ", "");
            File refFile = new File(projectRoot, ".vcs/" + refPath);

            if (!refFile.exists()) {
                return null;
            }

            return Files.readString(refFile.toPath()).trim();

        } catch (Exception e) {
            log.error("Error reading HEAD", e);
            return null;
        }
    }

    private Map<String, List<String>> getCommitToBranchesMap(File projectRoot) {
        Map<String, List<String>> map = new HashMap<>();

        try {
            File headsDir = new File(projectRoot, ".vcs/refs/heads");
            if (!headsDir.exists()) {
                return map;
            }

            File[] branchFiles = headsDir.listFiles();
            if (branchFiles == null) {
                return map;
            }

            for (File branchFile : branchFiles) {
                String branchName = branchFile.getName();
                String commitId = Files.readString(branchFile.toPath()).trim();

                map.computeIfAbsent(commitId, k -> new ArrayList<>()).add(branchName);
            }

        } catch (Exception e) {
            log.error("Error reading branches", e);
        }

        return map;
    }

    private CommitModel loadCommit(File projectRoot, String commitId) {
        try {
            File commitFile = new File(projectRoot, ".vcs/commits/" + commitId);
            if (!commitFile.exists()) {
                // Try with .json extension
                commitFile = new File(projectRoot, ".vcs/commits/" + commitId + ".json");
                if (!commitFile.exists()) {
                    return null;
                }
            }
            return objectMapper.readValue(commitFile, CommitModel.class);
        } catch (Exception e) {
            log.error("Error loading commit {}", commitId, e);
            return null;
        }
    }
}
