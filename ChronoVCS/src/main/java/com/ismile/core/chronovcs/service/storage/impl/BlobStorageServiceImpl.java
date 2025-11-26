package com.ismile.core.chronovcs.service.storage.impl;

import com.ismile.core.chronovcs.entity.BlobEntity;
import com.ismile.core.chronovcs.entity.RepositoryEntity;
import com.ismile.core.chronovcs.entity.StorageType;
import com.ismile.core.chronovcs.repository.BlobRepository;
import com.ismile.core.chronovcs.service.storage.BlobStorageClient;
import com.ismile.core.chronovcs.service.storage.BlobStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class BlobStorageServiceImpl implements BlobStorageService {

    private final BlobRepository blobRepository;
    private final Map<StorageType, BlobStorageClient> clients = new EnumMap<>(StorageType.class);

    public BlobStorageServiceImpl(
            BlobRepository blobRepository,
            List<BlobStorageClient> clientList
    ) {
        this.blobRepository = blobRepository;
        // Build registry from all BlobStorageClient beans
        for (BlobStorageClient client : clientList) {
            clients.put(client.getType(), client);
        }
    }

    @Override
    public BlobEntity saveBlob(RepositoryEntity repository,
                               String blobHash,
                               byte[] content,
                               String contentType) {

        // 1) If blob already exists for this repo, simply return it
        Optional<BlobEntity> existing =
                blobRepository.findByRepositoryAndHash(repository, blobHash);

        if (existing.isPresent()) {
            return existing.get();
        }

        // 2) Decide which storage type to use (for now always LOCAL)
        StorageType storageType = StorageType.LOCAL;
        BlobStorageClient client = resolveClient(storageType);

        // 3) Save content to underlying storage
        String storagePath = client.save(repository.getRepoKey(), blobHash, content, contentType);

        // 4) Persist metadata in DB
        BlobEntity entity = BlobEntity.builder()
                .repository(repository)
                .hash(blobHash)
                .storageType(storageType)
                .storagePath(storagePath)
                .contentType(contentType)
                .contentSize(content != null ? (long) content.length : null)
                .createdAt(LocalDateTime.now())
                .build();

        return blobRepository.save(entity);
    }

    @Override
    public Optional<BlobEntity> findByHash(RepositoryEntity repository, String hash) {
        return blobRepository.findByRepositoryAndHash(repository, hash);
    }

    @Override
    public byte[] loadContent(BlobEntity blob) {
        BlobStorageClient client = resolveClient(blob.getStorageType());
        return client.load(blob.getStoragePath());
    }

    private BlobStorageClient resolveClient(StorageType type) {
        BlobStorageClient client = clients.get(type);
        if (client == null) {
            throw new IllegalStateException("No BlobStorageClient registered for type: " + type);
        }
        return client;
    }
}