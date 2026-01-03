package com.ismile.core.chronovcs.repository;

import com.ismile.core.chronovcs.entity.CommitEntity;
import com.ismile.core.chronovcs.entity.RepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommitRepository extends JpaRepository<CommitEntity, Long> {

    Optional<CommitEntity> findByRepositoryAndCommitId(RepositoryEntity repository, String commitId);

    /**
     * Find all commits with a specific parent commit ID.
     */
    List<CommitEntity> findByRepositoryAndParentCommitId(RepositoryEntity repository, String parentCommitId);

    /**
     * Find all commits in a specific branch.
     */
    List<CommitEntity> findByRepositoryAndBranchOrderByCreatedAtDesc(RepositoryEntity repository, String branch);

    /**
     * Check if a commit exists in the repository.
     */
    boolean existsByRepositoryAndCommitId(RepositoryEntity repository, String commitId);

    /**
     * Count commits in a specific branch.
     */
    long countByRepositoryAndBranch(RepositoryEntity repository, String branch);

    void deleteAllByRepository(RepositoryEntity repository);
}
