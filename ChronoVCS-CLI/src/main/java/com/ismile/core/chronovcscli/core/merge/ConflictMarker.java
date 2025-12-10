package com.ismile.core.chronovcscli.core.merge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;

/**
 * Utility for adding conflict markers to files
 */
@Slf4j
@Component
public class ConflictMarker {

    private static final String CONFLICT_START = "<<<<<<< HEAD";
    private static final String CONFLICT_MIDDLE = "=======";
    private static final String CONFLICT_END = ">>>>>>> ";

    /**
     * Create a file with conflict markers
     *
     * @param targetFile     file to write to
     * @param localContent   content from local branch
     * @param remoteContent  content from remote branch
     * @param remoteBranch   remote branch name
     */
    public void writeConflictFile(File targetFile,
                                   byte[] localContent,
                                   byte[] remoteContent,
                                   String remoteBranch) throws Exception {

        StringBuilder result = new StringBuilder();

        result.append(CONFLICT_START).append("\n");
        result.append(new String(localContent));
        if (!new String(localContent).endsWith("\n")) {
            result.append("\n");
        }

        result.append(CONFLICT_MIDDLE).append("\n");
        result.append(new String(remoteContent));
        if (!new String(remoteContent).endsWith("\n")) {
            result.append("\n");
        }

        result.append(CONFLICT_END).append(remoteBranch).append("\n");

        targetFile.getParentFile().mkdirs();
        Files.writeString(targetFile.toPath(), result.toString());

        log.info("Created conflict markers in: {}", targetFile.getName());
    }

    /**
     * Check if a file contains conflict markers
     *
     * @param file file to check
     * @return true if file has unresolved conflicts
     */
    public boolean hasConflictMarkers(File file) {
        try {
            if (!file.exists()) {
                return false;
            }
            String content = Files.readString(file.toPath());
            return content.contains(CONFLICT_START) &&
                   content.contains(CONFLICT_MIDDLE) &&
                   content.contains(CONFLICT_END);
        } catch (Exception e) {
            log.error("Error checking conflict markers", e);
            return false;
        }
    }

    /**
     * Check if project has any files with conflict markers
     *
     * @param projectRoot project root directory
     * @param files       list of files to check
     * @return true if any file has conflict markers
     */
    public boolean hasAnyConflicts(File projectRoot, java.util.List<String> files) {
        return files.stream()
                .map(f -> new File(projectRoot, f))
                .anyMatch(this::hasConflictMarkers);
    }
}
