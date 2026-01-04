package com.ismile.core.chronovcs.repository;

import com.ismile.core.chronovcs.entity.PullRequestEntity;
import com.ismile.core.chronovcs.entity.PullRequestStatus;
import com.ismile.core.chronovcs.entity.RepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PullRequestRepository extends JpaRepository<PullRequestEntity, Long> {

    List<PullRequestEntity> findByRepositoryIdOrderByCreatedAtDesc(Long repositoryId);

    List<PullRequestEntity> findByRepositoryIdAndStatusOrderByCreatedAtDesc(Long repositoryId,
                                                                            PullRequestStatus status);

    Optional<PullRequestEntity> findByRepositoryIdAndId(Long repositoryId, Long id);

    Optional<PullRequestEntity> findByRepositoryIdAndSourceBranchAndTargetBranchAndStatus(Long repositoryId,
                                                                                          String sourceBranch,
                                                                                          String targetBranch,
                                                                                          PullRequestStatus status);

    void deleteAllByRepository(RepositoryEntity repository);
}
