package com.ismile.core.chronovcs.service.storage;

import com.ismile.core.chronovcs.entity.BlobEntity;
import com.ismile.core.chronovcs.entity.RepositoryEntity;

import java.util.Optional;

public interface BlobStorageService {

    /**
     * Save blob content if not already stored.
     * If a blob with the same hash already exists for this repo,
     * returns existing entity without writing to storage again.
     */
    BlobEntity saveBlob(RepositoryEntity repository,
                        String blobHash,
                        byte[] content,
                        String contentType);

    /**
     * Lookup blob metadata by repo and hash.
     */
    Optional<BlobEntity> findByHash(RepositoryEntity repository, String hash);

    /**
     * Load blob bytes from underlying storage.
     */
    byte[] loadContent(BlobEntity blob);
}