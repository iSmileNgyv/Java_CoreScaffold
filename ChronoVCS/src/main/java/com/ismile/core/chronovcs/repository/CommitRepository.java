package com.ismile.core.chronovcs.repository;

import com.ismile.core.chronovcs.entity.CommitEntity;
import com.ismile.core.chronovcs.entity.RepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommitRepository extends JpaRepository<CommitEntity, Long> {

    Optional<CommitEntity> findByRepositoryAndCommitId(RepositoryEntity repository, String commitId);
}