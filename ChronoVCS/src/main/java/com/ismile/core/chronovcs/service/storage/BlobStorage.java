package com.ismile.core.chronovcs.service.storage;

import com.ismile.core.chronovcs.entity.RepositoryEntity;

public interface BlobStorage {

    /**
     * Save blob and return provider-specific storage key.
     */
    String saveBlob(RepositoryEntity repo, String blobHash, byte[] content);

    byte[] getBlob(RepositoryEntity repo, String storageKey);

    boolean exists(RepositoryEntity repo, String storageKey);
}