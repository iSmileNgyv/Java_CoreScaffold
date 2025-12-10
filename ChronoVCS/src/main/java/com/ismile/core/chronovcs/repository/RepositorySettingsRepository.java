package com.ismile.core.chronovcs.repository;

import com.ismile.core.chronovcs.entity.RepositorySettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RepositorySettingsRepository extends JpaRepository<RepositorySettingsEntity, Long> {

    Optional<RepositorySettingsEntity> findByRepositoryId(Long repositoryId);
}
