package com.ismile.core.chronovcs.repository;

import com.ismile.core.chronovcs.entity.RepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RepositoryRepository extends JpaRepository<RepositoryEntity, Long> {
    Optional<RepositoryEntity> findByRepoKey(String repoKey);
}
