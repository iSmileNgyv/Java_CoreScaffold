package com.ismile.core.chronovcs.repository;

import com.ismile.core.chronovcs.entity.ReleaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReleaseRepository extends JpaRepository<ReleaseEntity, Long> {

    List<ReleaseEntity> findByRepositoryIdOrderByCreatedAtDesc(Long repositoryId);

    Optional<ReleaseEntity> findByRepositoryIdAndVersion(Long repositoryId, String version);

    @Query("SELECT r FROM ReleaseEntity r WHERE r.repository.id = :repositoryId " +
           "ORDER BY r.createdAt DESC LIMIT 1")
    Optional<ReleaseEntity> findLatestByRepositoryId(Long repositoryId);
}
