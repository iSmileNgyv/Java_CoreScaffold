package com.ismile.core.chronovcs.service.storage;

import com.ismile.core.chronovcs.entity.StorageType;

public interface BlobStorageClient {

    /**
     * Which storage type this client supports.
     */
    StorageType getType();

    /**
     * Save blob content and return storage-specific path/key.
     *
     * @param repoKey      logical repository key
     * @param blobHash     content hash (used to build directory structure)
     * @param content      raw bytes
     * @param contentType  optional content type
     * @return storagePath to be stored in DB
     */
    String save(String repoKey, String blobHash, byte[] content, String contentType);

    /**
     * Load blob content by storagePath (as stored in DB).
     */
    byte[] load(String storagePath);
}