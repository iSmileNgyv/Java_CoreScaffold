package com.ismile.core.chronovcscli.core.hash.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Sha256HashEngineTest {

    private Sha256HashEngine hashEngine;

    @BeforeEach
    void setUp() {
        hashEngine = new Sha256HashEngine();
    }

    @Test
    void testHashString() {
        String input = "hello world";
        // SHA-256 hash of "hello world"
        String expectedHash = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";
        String actualHash = hashEngine.hashString(input);
        System.out.printf("Hashed: %s", actualHash);
        assertEquals(expectedHash, actualHash);
    }

    @Test
    void testHashBytes() {
        byte[] input = "hello world".getBytes();
        // SHA-256 hash of "hello world"
        String expectedHash = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";
        String actualHash = hashEngine.hashBytes(input);
        System.out.printf("Hash: %s", actualHash);
        assertEquals(expectedHash, actualHash);
    }

    @Test
    void testHashFile(@TempDir Path tempDir) throws IOException {
        Path tempFile = tempDir.resolve("test.txt");
        String fileContent = "This is a test file.";
        Files.writeString(tempFile, fileContent);

        // SHA-256 hash of "This is a test file."
        String expectedHash = "f29bc64a9d3732b4b9035125fdb3285f5b6455778edca72414671e0ca3b2e0de";
        String actualHash = hashEngine.hashFile(tempFile.toFile());
        System.out.printf("Hash: %s", actualHash);
        assertEquals(expectedHash, actualHash);
    }

    @Test
    void testHashEmptyString() {
        String input = "";
        // SHA-256 hash of an empty string
        String expectedHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        String actualHash = hashEngine.hashString(input);
        System.out.printf("Hash: %s", actualHash);
        assertEquals(expectedHash, actualHash);
    }

    @Test
    void testHashFileWithLargeContent(@TempDir Path tempDir) throws IOException {
        Path tempFile = tempDir.resolve("large_test.txt");
        // Create a string larger than the buffer size in Sha256HashEngine (8192)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("a");
        }
        String fileContent = sb.toString();
        Files.writeString(tempFile, fileContent);

        // Pre-calculated SHA-256 hash of 10000 'a' characters
        String expectedHash = "27dd1f61b867b6a0f6e9d8a41c43231de52107e53ae424de8f847b821db4b711";
        String actualHash = hashEngine.hashFile(tempFile.toFile());
        System.out.printf("Hash: %s", actualHash);
        assertEquals(expectedHash, actualHash);
    }
}
