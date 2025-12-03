package com.ismile.core.chronovcscli.core.diff.impl;

import com.ismile.core.chronovcscli.core.VcsDirectoryManager;
import com.ismile.core.chronovcscli.core.commit.CommitModel;
import com.ismile.core.chronovcscli.core.diff.DiffEngine;
import com.ismile.core.chronovcscli.core.diff.DiffResult;
import com.ismile.core.chronovcscli.core.diff.FileDiff;
import com.ismile.core.chronovcscli.core.index.IndexEngine;
import com.ismile.core.chronovcscli.core.objectsStore.ObjectStore;
import com.ismile.core.chronovcscli.utils.HashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

@Component
@RequiredArgsConstructor
public class DiffEngineImpl implements DiffEngine {
    private final IndexEngine indexEngine;
    private final ObjectStore objectStore;
    private final VcsDirectoryManager vcsDirectoryManager;

    @Override
    public DiffResult diffWorkingVsStaged(File projectRoot) throws IOException {
        indexEngine.loadIndex(projectRoot);
        Map<String, String> stagedFiles = indexEngine.getEntries();

        List<FileDiff> fileDiffs = new ArrayList<>();

        // Check staged files for modifications
        for (Map.Entry<String, String> entry : stagedFiles.entrySet()) {
            String relativePath = entry.getKey();
            String stagedHash = entry.getValue();

            File workingFile = new File(projectRoot, relativePath);
            if (!workingFile.exists()) {
                // File deleted in working directory
                byte[] stagedContent = objectStore.readBlob(stagedHash);
                List<String> hunks = generateDeletedHunks(new String(stagedContent));
                fileDiffs.add(new FileDiff(relativePath, FileDiff.ChangeType.DELETED, hunks));
            } else {
                // Check if modified
                String workingHash = HashUtils.sha256(workingFile);
                if (!workingHash.equals(stagedHash)) {
                    byte[] stagedContent = objectStore.readBlob(stagedHash);
                    byte[] workingContent = Files.readAllBytes(workingFile.toPath());
                    List<String> hunks = generateDiffHunks(
                        new String(stagedContent),
                        new String(workingContent)
                    );
                    fileDiffs.add(new FileDiff(relativePath, FileDiff.ChangeType.MODIFIED, hunks));
                }
            }
        }

        return new DiffResult(fileDiffs);
    }

    @Override
    public DiffResult diffStagedVsHead(File projectRoot) throws IOException {
        indexEngine.loadIndex(projectRoot);
        Map<String, String> stagedFiles = indexEngine.getEntries();

        File headFile = vcsDirectoryManager.getHeadFile(projectRoot);
        Map<String, String> headFiles = new HashMap<>();

        if (headFile.exists()) {
            String headCommitHash = Files.readString(headFile.toPath()).trim();
            CommitModel headCommit = vcsDirectoryManager.readCommit(projectRoot, headCommitHash);
            headFiles = headCommit.getFiles();
        }

        List<FileDiff> fileDiffs = new ArrayList<>();
        Set<String> allFiles = new HashSet<>();
        allFiles.addAll(stagedFiles.keySet());
        allFiles.addAll(headFiles.keySet());

        for (String relativePath : allFiles) {
            String stagedHash = stagedFiles.get(relativePath);
            String headHash = headFiles.get(relativePath);

            if (stagedHash == null) {
                // Deleted in staged
                byte[] headContent = objectStore.readBlob(headHash);
                List<String> hunks = generateDeletedHunks(new String(headContent));
                fileDiffs.add(new FileDiff(relativePath, FileDiff.ChangeType.DELETED, hunks));
            } else if (headHash == null) {
                // Added in staged
                byte[] stagedContent = objectStore.readBlob(stagedHash);
                List<String> hunks = generateAddedHunks(new String(stagedContent));
                fileDiffs.add(new FileDiff(relativePath, FileDiff.ChangeType.ADDED, hunks));
            } else if (!stagedHash.equals(headHash)) {
                // Modified
                byte[] headContent = objectStore.readBlob(headHash);
                byte[] stagedContent = objectStore.readBlob(stagedHash);
                List<String> hunks = generateDiffHunks(
                    new String(headContent),
                    new String(stagedContent)
                );
                fileDiffs.add(new FileDiff(relativePath, FileDiff.ChangeType.MODIFIED, hunks));
            }
        }

        return new DiffResult(fileDiffs);
    }

    private List<String> generateDiffHunks(String oldContent, String newContent) {
        String[] oldLines = oldContent.split("\n");
        String[] newLines = newContent.split("\n");

        List<String> hunks = new ArrayList<>();

        for (int i = 0; i < Math.max(oldLines.length, newLines.length); i++) {
            String oldLine = i < oldLines.length ? oldLines[i] : "";
            String newLine = i < newLines.length ? newLines[i] : "";

            if (!oldLine.equals(newLine)) {
                if (!oldLine.isEmpty()) {
                    hunks.add("- " + oldLine);
                }
                if (!newLine.isEmpty()) {
                    hunks.add("+ " + newLine);
                }
            }
        }

        return hunks;
    }

    private List<String> generateAddedHunks(String content) {
        String[] lines = content.split("\n");
        List<String> hunks = new ArrayList<>();
        for (String line : lines) {
            hunks.add("+ " + line);
        }
        return hunks;
    }

    private List<String> generateDeletedHunks(String content) {
        String[] lines = content.split("\n");
        List<String> hunks = new ArrayList<>();
        for (String line : lines) {
            hunks.add("- " + line);
        }
        return hunks;
    }
}
