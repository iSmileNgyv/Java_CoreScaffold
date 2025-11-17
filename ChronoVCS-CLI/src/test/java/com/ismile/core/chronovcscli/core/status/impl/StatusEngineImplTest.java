package com.ismile.core.chronovcscli.core.status.impl;

import com.ismile.core.chronovcscli.core.hash.HashEngine;
import com.ismile.core.chronovcscli.core.ignore.IgnoreEngine;
import com.ismile.core.chronovcscli.core.index.IndexEngine;
import com.ismile.core.chronovcscli.core.objectsStore.ObjectStore;
import com.ismile.core.chronovcscli.core.status.StatusResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class StatusEngineImplTest {

    @TempDir
    Path tempDir;

    private File projectRoot;
    private IgnoreEngine ignoreEngine;
    private HashEngine hashEngine;
    private ObjectStore objectStore;
    private IndexEngine indexEngine;
    private StatusEngineImpl statusEngine;

    @BeforeEach
    void setUp() throws IOException {
        projectRoot = tempDir.toFile();

        ignoreEngine = Mockito.mock(IgnoreEngine.class);
        hashEngine = Mockito.mock(HashEngine.class);
        objectStore = Mockito.mock(ObjectStore.class);
        indexEngine = Mockito.mock(IndexEngine.class);

        // Default mock behavior: no files are ignored
        when(ignoreEngine.isIgnored(any(File.class), any(File.class))).thenReturn(false);

        statusEngine = new StatusEngineImpl(ignoreEngine, hashEngine, objectStore, indexEngine);
    }

    @Test
    void testGetStatus_emptyProject() throws IOException {
        when(indexEngine.getEntries()).thenReturn(Collections.emptyMap());

        StatusResult result = statusEngine.getStatus(projectRoot);

        assertTrue(result.getUntracked().isEmpty());
        assertTrue(result.getModified().isEmpty());
        assertTrue(result.getDeleted().isEmpty());
    }

    @Test
    void testGetStatus_untrackedFile() throws IOException {
        // Create an untracked file
        Path untrackedFilePath = tempDir.resolve("untracked.txt");
        Files.writeString(untrackedFilePath, "untracked content");

        when(indexEngine.getEntries()).thenReturn(Collections.emptyMap());
        when(hashEngine.hashFile(untrackedFilePath.toFile())).thenReturn("hash1");

        StatusResult result = statusEngine.getStatus(projectRoot);

        assertEquals(1, result.getUntracked().size());
        assertTrue(result.getUntracked().contains("untracked.txt"));
        assertTrue(result.getModified().isEmpty());
        assertTrue(result.getDeleted().isEmpty());
    }

    @Test
    void testGetStatus_modifiedFile() throws IOException {
        // Create a file that will be modified
        Path modifiedFilePath = tempDir.resolve("modified.txt");
        Files.writeString(modifiedFilePath, "original content");

        // Setup index with original hash
        Map<String, String> indexEntries = new HashMap<>();
        indexEntries.put("modified.txt", "original_hash");
        when(indexEngine.getEntries()).thenReturn(indexEntries);

        // Mock hash engine to return a new hash for the modified file
        when(hashEngine.hashFile(modifiedFilePath.toFile())).thenReturn("new_hash");

        StatusResult result = statusEngine.getStatus(projectRoot);

        assertTrue(result.getUntracked().isEmpty());
        assertEquals(1, result.getModified().size());
        assertTrue(result.getModified().contains("modified.txt"));
        assertTrue(result.getDeleted().isEmpty());
    }

    @Test
    void testGetStatus_deletedFile() throws IOException {
        // Setup index with a file that will be deleted
        Map<String, String> indexEntries = new HashMap<>();
        indexEntries.put("deleted.txt", "deleted_hash");
        when(indexEngine.getEntries()).thenReturn(indexEntries);

        // Do not create the file in the temp directory, simulating deletion

        StatusResult result = statusEngine.getStatus(projectRoot);

        assertTrue(result.getUntracked().isEmpty());
        assertTrue(result.getModified().isEmpty());
        assertEquals(1, result.getDeleted().size());
        assertTrue(result.getDeleted().contains("deleted.txt"));
    }

    @Test
    void testGetStatus_ignoredFile() throws IOException {
        // Create an ignored file
        Path ignoredFilePath = tempDir.resolve("ignored.txt");
        Files.writeString(ignoredFilePath, "ignored content");

        when(ignoreEngine.isIgnored(any(File.class), any(File.class))).thenReturn(true);
        when(indexEngine.getEntries()).thenReturn(Collections.emptyMap());

        StatusResult result = statusEngine.getStatus(projectRoot);

        assertTrue(result.getUntracked().isEmpty());
        assertTrue(result.getModified().isEmpty());
        assertTrue(result.getDeleted().isEmpty());
    }

    @Test
    void testGetStatus_fileInVcsDirectoryIsIgnored() throws IOException {
        // Create a file inside a .vcs directory
        Path vcsDirPath = tempDir.resolve(".vcs");
        Files.createDirectory(vcsDirPath);
        Path vcsFilePath = vcsDirPath.resolve("config.txt");
        Files.writeString(vcsFilePath, "vcs config");

        when(indexEngine.getEntries()).thenReturn(Collections.emptyMap());
        when(hashEngine.hashFile(vcsFilePath.toFile())).thenReturn("vcs_hash"); // This should not be called for .vcs files

        StatusResult result = statusEngine.getStatus(projectRoot);

        assertTrue(result.getUntracked().isEmpty());
        assertTrue(result.getModified().isEmpty());
        assertTrue(result.getDeleted().isEmpty());
    }

    @Test
    void testGetStatus_mixedStatus() throws IOException {
        // Untracked file
        Path untrackedFilePath = tempDir.resolve("untracked.txt");
        Files.writeString(untrackedFilePath, "untracked content");

        // Modified file
        Path modifiedFilePath = tempDir.resolve("modified.txt");
        Files.writeString(modifiedFilePath, "original content");

        // Deleted file (only in index)
        Map<String, String> indexEntries = new HashMap<>();
        indexEntries.put("modified.txt", "original_hash");
        indexEntries.put("deleted.txt", "deleted_hash");
        indexEntries.put("tracked.txt", "tracked_hash"); // A tracked, unchanged file
        when(indexEngine.getEntries()).thenReturn(indexEntries);

        // Tracked, unchanged file
        Path trackedFilePath = tempDir.resolve("tracked.txt");
        Files.writeString(trackedFilePath, "tracked content");

        when(hashEngine.hashFile(untrackedFilePath.toFile())).thenReturn("untracked_hash");
        when(hashEngine.hashFile(modifiedFilePath.toFile())).thenReturn("new_modified_hash");
        when(hashEngine.hashFile(trackedFilePath.toFile())).thenReturn("tracked_hash");


        StatusResult result = statusEngine.getStatus(projectRoot);

        assertEquals(1, result.getUntracked().size());
        assertTrue(result.getUntracked().contains("untracked.txt"));

        assertEquals(1, result.getModified().size());
        assertTrue(result.getModified().contains("modified.txt"));

        assertEquals(1, result.getDeleted().size());
        assertTrue(result.getDeleted().contains("deleted.txt"));
    }

    @Test
    void testGetStatus_noChanges() throws IOException {
        // Create a tracked, unchanged file
        Path trackedFilePath = tempDir.resolve("tracked.txt");
        Files.writeString(trackedFilePath, "tracked content");

        Map<String, String> indexEntries = new HashMap<>();
        indexEntries.put("tracked.txt", "tracked_hash");
        when(indexEngine.getEntries()).thenReturn(indexEntries);

        when(hashEngine.hashFile(trackedFilePath.toFile())).thenReturn("tracked_hash");

        StatusResult result = statusEngine.getStatus(projectRoot);

        assertTrue(result.getUntracked().isEmpty());
        assertTrue(result.getModified().isEmpty());
        assertTrue(result.getDeleted().isEmpty());
    }
}
