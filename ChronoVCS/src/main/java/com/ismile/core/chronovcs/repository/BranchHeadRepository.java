package com.ismile.core.chronovcs.repository;

import com.ismile.core.chronovcs.entity.BranchHeadEntity;
import com.ismile.core.chronovcs.entity.RepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BranchHeadRepository extends JpaRepository<BranchHeadEntity, Long> {

    Optional<BranchHeadEntity> findByRepositoryAndBranch(RepositoryEntity repository, String branch);
}