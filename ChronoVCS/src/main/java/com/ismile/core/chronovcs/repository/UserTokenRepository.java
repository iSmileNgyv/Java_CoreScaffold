package com.ismile.core.chronovcs.repository;

import com.ismile.core.chronovcs.entity.UserTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserTokenRepository extends JpaRepository<UserTokenEntity, Long> {
    // Finds all tokens matching a prefix that are not revoked
    List<UserTokenEntity> findByTokenPrefixAndRevokedFalse(String tokenPrefix);
}