package com.ismile.core.auth.config;

import com.ismile.core.auth.repository.RefreshTokenRepository;
import com.ismile.core.auth.security.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Scheduled tasks for maintenance
 * - Clean up expired tokens
 * - Clean up old login attempts
 */
@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class ScheduledTasks {

    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptService loginAttemptService;

    /**
     * Clean up expired refresh tokens
     * Runs every day at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Starting expired token cleanup...");

        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.deleteExpiredTokens(now);

        log.info("Expired token cleanup completed");
    }

    /**
     * Clean up old login attempts
     * Runs every day at 3 AM
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOldLoginAttempts() {
        log.info("Starting old login attempts cleanup...");

        loginAttemptService.cleanupOldAttempts();

        log.info("Old login attempts cleanup completed");
    }
}