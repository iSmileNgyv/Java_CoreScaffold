package com.ismile.core.chronovcs.repository;

import com.ismile.core.chronovcs.entity.TokenPermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TokenPermissionRepository extends JpaRepository<TokenPermissionEntity, Long> {
    Optional<TokenPermissionEntity> findByTokenIdAndRepositoryId(Long tokenId, Long repositoryId);
    List<TokenPermissionEntity> findAllByTokenId(Long tokenId);
    void deleteAllByRepositoryId(Long repositoryId);
}
