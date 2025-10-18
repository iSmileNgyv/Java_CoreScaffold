package com.ismile.core.otp.repository;

import com.ismile.core.otp.entity.UserSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for the UserSettingsEntity.
 */
@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettingsEntity, Long> {

    /**
     * Finds user settings by the user's unique ID.
     * @param userId The ID of the user from the Auth service.
     * @return An Optional containing the UserSettingsEntity if found.
     */
    Optional<UserSettingsEntity> findByUserId(int userId);
}
