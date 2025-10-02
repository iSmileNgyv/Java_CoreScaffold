package com.ismile.core.auth.repository;

import com.ismile.core.auth.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, String> {

    Optional<RoleEntity> findByCode(String code);

    Optional<RoleEntity> findByDefaultRoleTrue();
}