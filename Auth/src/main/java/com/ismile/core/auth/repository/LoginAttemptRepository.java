package com.ismile.core.auth.repository;

import com.ismile.core.auth.entity.LoginAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttemptEntity, Long> {

    List<LoginAttemptEntity> findByUsernameAndAttemptTimeAfter(String username, LocalDateTime after);

    long countByUsernameAndSuccessFalseAndAttemptTimeAfter(String username, LocalDateTime after);

    @Modifying
    @Query("DELETE FROM LoginAttemptEntity la WHERE la.attemptTime < :before")
    void deleteOldAttempts(LocalDateTime before);
}