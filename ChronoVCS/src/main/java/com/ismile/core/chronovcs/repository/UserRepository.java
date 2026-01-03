package com.ismile.core.chronovcs.repository;

import com.ismile.core.chronovcs.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findByEmailAndActiveTrue(String email);

    Optional<UserEntity> findByUserUid(String userUid);
}
