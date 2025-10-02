package com.ismile.core.auth.security;

import com.ismile.core.auth.entity.LoginAttemptEntity;
import com.ismile.core.auth.entity.UserEntity;
import com.ismile.core.auth.repository.LoginAttemptRepository;
import com.ismile.core.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Login attempt tracking and brute force protection
 * Implements account lockout mechanism
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {

    private final LoginAttemptRepository loginAttemptRepository;
    private final UserRepository userRepository;

    private static final int MAX_ATTEMPTS = 5;
    private static final int ATTEMPT_WINDOW_MINUTES = 5;
    private static final int LOCKOUT_DURATION_MINUTES = 15;

    /**
     * Record login attempt
     */
    @Transactional
    public void recordLoginAttempt(String username, boolean success, String ipAddress, String reason) {
        LoginAttemptEntity attempt = LoginAttemptEntity.builder()
                .username(username)
                .success(success)
                .ipAddress(ipAddress)
                .failureReason(reason)
                .build();

        loginAttemptRepository.save(attempt);

        // Check if account should be locked
        if (!success) {
            checkAndLockAccount(username);
        }
    }

    /**
     * Check failed attempts and lock account if needed
     */
    private void checkAndLockAccount(String username) {
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(ATTEMPT_WINDOW_MINUTES);

        long failedAttempts = loginAttemptRepository
                .countByUsernameAndSuccessFalseAndAttemptTimeAfter(username, windowStart);

        if (failedAttempts >= MAX_ATTEMPTS) {
            userRepository.findByUsername(username).ifPresent(user -> {
                user.setAccountLocked(true);
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_DURATION_MINUTES));
                user.setFailedLoginAttempts((int) failedAttempts);
                userRepository.save(user);

                log.warn("Account locked due to {} failed attempts: {}", failedAttempts, username);
            });
        }
    }

    /**
     * Reset login attempts on successful login
     */
    @Transactional
    public void resetLoginAttempts(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.resetFailedAttempts();
            userRepository.save(user);
        });
    }

    /**
     * Check if user is locked
     */
    public boolean isLocked(UserEntity user) {
        if (user.isAccountLocked() && user.getLockedUntil() != null) {
            // Auto-unlock if lock period expired
            if (LocalDateTime.now().isAfter(user.getLockedUntil())) {
                user.resetFailedAttempts();
                userRepository.save(user);
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Clean up old login attempts (scheduled task can call this)
     */
    @Transactional
    public void cleanupOldAttempts() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        loginAttemptRepository.deleteOldAttempts(cutoff);
        log.info("Cleaned up login attempts older than 30 days");
    }
}