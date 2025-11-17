package com.ismile.core.chronovcscli.core.objectsStore.impl;

import com.ismile.core.chronovcscli.core.hash.HashEngine;
import com.ismile.core.chronovcscli.core.hash.impl.Sha256HashEngine;
import com.ismile.core.chronovcscli.core.ignore.IgnoreEngine;
import com.ismile.core.chronovcscli.core.ignore.IgnoreParser;
import com.ismile.core.chronovcscli.core.ignore.impl.IgnoreEngineImpl;
import com.ismile.core.chronovcscli.utils.HashUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ObjectStoreImplTest {

    private ObjectStoreImpl objectStore;
    private HashEngine hashEngine;
    private IgnoreEngine ignoreEngine;

    @TempDir
    Path tempDir;

    private File projectRoot;

    @BeforeEach
    void setUp() throws IOException {
        projectRoot = tempDir.toFile();
        File vcsDir = new File(projectRoot, ".vcs");
        vcsDir.mkdirs();

        hashEngine = new Sha256HashEngine();
        ignoreEngine = new IgnoreEngineImpl(new IgnoreParser());
        objectStore = new ObjectStoreImpl(hashEngine, ignoreEngine, projectRoot);
    }

    @Test
    void testWriteBlobAndReadBlob() throws IOException {
        File testFile = new File(projectRoot, "test.txt");
        String content = "hello world";
        Files.writeString(testFile.toPath(), content);

        String hash = objectStore.writeBlob(testFile);

        // Verify hash
        String expectedHash = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";
        assertEquals(expectedHash, hash);

        // Verify file is stored
        File blobFile = new File(projectRoot, ".vcs/objects/" + hash.substring(0, 2) + "/" + hash.substring(2));
        assertTrue(blobFile.exists());

        // Verify content
        byte[] readContent = objectStore.readBlob(hash);
        assertEquals(content, new String(readContent));
    }

    @Test
    void testExists() throws IOException {
        File testFile = new File(projectRoot, "test.txt");
        String content = "hello world";
        Files.writeString(testFile.toPath(), content);

        String hash = objectStore.writeBlob(testFile);

        assertTrue(objectStore.exists(hash));
        assertFalse(objectStore.exists("nonexistenthash1234567890"));
    }

    @Test
    void testWriteBlobIsIgnored() throws IOException {
        // Create .chronoignore file
        File ignoreFile = new File(projectRoot, ".chronoignore");
        Files.writeString(ignoreFile.toPath(), "ignored.txt\nlogs/\n");

        // Create an ignored file
        File ignoredFile = new File(projectRoot, "ignored.txt");
        Files.writeString(ignoredFile.toPath(), "this should be ignored");

        // Create a file in an ignored directory
        File ignoredDir = new File(projectRoot, "logs");
        ignoredDir.mkdir();
        File fileInIgnoredDir = new File(ignoredDir, "test.log");
        Files.writeString(fileInIgnoredDir.toPath(), "this should also be ignored");

        // Test ignored file
        String hash1 = objectStore.writeBlob(ignoredFile);
        assertNull(hash1);

        // Test file in ignored directory
        String hash2 = objectStore.writeBlob(fileInIgnoredDir);
        assertNull(hash2);
    }

    @Test
    void testWriteBlobAlreadyExists() throws IOException {
        File testFile = new File(projectRoot, "test.txt");
        String content = "hello world";
        Files.writeString(testFile.toPath(), content);

        String hash1 = objectStore.writeBlob(testFile);
        
        // To simulate a clean run, we need to delete the file before writing it again
        // but the test is about re-hashing and finding it exists.
        // Let's check if the file is there, then write again.
        File blobFile = new File(projectRoot, ".vcs/objects/" + hash1.substring(0, 2) + "/" + hash1.substring(2));
        assertTrue(blobFile.exists());
        long lastModified = blobFile.lastModified();

        // wait a bit to ensure the timestamp is different if the file is rewritten.
        try { Thread.sleep(10); } catch (InterruptedException e) {}

        String hash2 = objectStore.writeBlob(testFile);

        assertEquals(hash1, hash2);
        assertEquals(lastModified, blobFile.lastModified(), "Blob file should not have been modified.");
    }
}
