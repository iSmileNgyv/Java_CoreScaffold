package com.ismile.core.chronovcs.repository;

import com.ismile.core.chronovcs.entity.BlobEntity;
import com.ismile.core.chronovcs.entity.RepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BlobRepository extends JpaRepository<BlobEntity, Long> {

    Optional<BlobEntity> findByRepositoryAndHash(RepositoryEntity repository, String hash);
}