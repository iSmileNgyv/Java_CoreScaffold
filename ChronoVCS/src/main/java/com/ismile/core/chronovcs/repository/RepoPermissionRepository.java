package com.ismile.core.chronovcs.repository;

import com.ismile.core.chronovcs.entity.RepoPermissionEntity;
import com.ismile.core.chronovcs.entity.RepositoryEntity;
import com.ismile.core.chronovcs.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RepoPermissionRepository extends JpaRepository<RepoPermissionEntity, Long> {
    Optional<RepoPermissionEntity> findByUserSettingsAndRepository(
            UserEntity user,
            RepositoryEntity repository
    );
    List<RepoPermissionEntity> findAllByUserSettings(UserEntity user);
}
