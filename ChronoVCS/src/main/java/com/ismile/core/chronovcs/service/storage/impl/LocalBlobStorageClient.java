package com.ismile.core.chronovcs.service.storage.impl;

import com.ismile.core.chronovcs.entity.StorageType;
import com.ismile.core.chronovcs.service.storage.BlobStorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
@Slf4j
public class LocalBlobStorageClient implements BlobStorageClient {

    private final LocalStorageProperties properties;

    @Override
    public StorageType getType() {
        return StorageType.LOCAL;
    }

    @Override
    public String save(String repoKey, String blobHash, byte[] content, String contentType) {
        try {
            if (properties.getBasePath() == null || properties.getBasePath().isBlank()) {
                throw new IllegalStateException("Local blob storage basePath is not configured");
            }

            String prefix = blobHash.substring(0, 2);
            String rest = blobHash.substring(2);

            // Store relative path: <repoKey>/<prefix>/<rest>
            String relativePath = repoKey + "/" + prefix + "/" + rest;

            Path baseDir = Path.of(properties.getBasePath());
            Path filePath = baseDir.resolve(relativePath);

            Files.createDirectories(filePath.getParent());
            Files.write(filePath, content);

            log.debug("Saved blob to local storage: {}", filePath);

            // We store relative path in DB
            return relativePath;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save blob to local filesystem", e);
        }
    }

    @Override
    public byte[] load(String storagePath) {
        try {
            if (properties.getBasePath() == null || properties.getBasePath().isBlank()) {
                throw new IllegalStateException("Local blob storage basePath is not configured");
            }

            Path baseDir = Path.of(properties.getBasePath());
            Path filePath = baseDir.resolve(storagePath);

            if (!Files.exists(filePath)) {
                throw new IllegalStateException("Blob file not found: " + filePath);
            }

            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load blob from local filesystem", e);
        }
    }
}
