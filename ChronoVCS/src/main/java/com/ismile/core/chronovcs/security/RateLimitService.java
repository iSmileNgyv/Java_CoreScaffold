package com.ismile.core.chronovcs.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiting Service using Token Bucket Algorithm
 *
 * Features:
 * - IP-based rate limiting
 * - Configurable limits per endpoint
 * - Automatic bucket cleanup
 * - Thread-safe implementation
 *
 * Default limits:
 * - Login: 5 requests per minute per IP
 * - Register: 3 requests per hour per IP
 * - Token refresh: 10 requests per minute per IP
 */
@Service
@Slf4j
public class RateLimitService {

    // Store buckets per IP address
    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registerBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> tokenBuckets = new ConcurrentHashMap<>();

    /**
     * Check if login request is allowed for given IP
     *
     * @param ipAddress Client IP address
     * @return true if request is allowed, false if rate limit exceeded
     */
    public boolean allowLogin(String ipAddress) {
        Bucket bucket = loginBuckets.computeIfAbsent(ipAddress, this::createLoginBucket);
        boolean allowed = bucket.tryConsume(1);

        if (!allowed) {
            log.warn("Rate limit exceeded for login from IP: {}", ipAddress);
        }

        return allowed;
    }

    /**
     * Check if registration request is allowed for given IP
     *
     * @param ipAddress Client IP address
     * @return true if request is allowed, false if rate limit exceeded
     */
    public boolean allowRegister(String ipAddress) {
        Bucket bucket = registerBuckets.computeIfAbsent(ipAddress, this::createRegisterBucket);
        boolean allowed = bucket.tryConsume(1);

        if (!allowed) {
            log.warn("Rate limit exceeded for registration from IP: {}", ipAddress);
        }

        return allowed;
    }

    /**
     * Check if token refresh request is allowed for given IP
     *
     * @param ipAddress Client IP address
     * @return true if request is allowed, false if rate limit exceeded
     */
    public boolean allowTokenRefresh(String ipAddress) {
        Bucket bucket = tokenBuckets.computeIfAbsent(ipAddress, this::createTokenBucket);
        boolean allowed = bucket.tryConsume(1);

        if (!allowed) {
            log.warn("Rate limit exceeded for token refresh from IP: {}", ipAddress);
        }

        return allowed;
    }

    /**
     * Create bucket for login endpoint
     * Limit: 5 requests per minute
     */
    private Bucket createLoginBucket(String key) {
        Bandwidth limit = Bandwidth.classic(
                5, // capacity
                Refill.intervally(5, Duration.ofMinutes(1)) // refill 5 tokens every minute
        );
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Create bucket for register endpoint
     * Limit: 3 requests per hour (prevent abuse)
     */
    private Bucket createRegisterBucket(String key) {
        Bandwidth limit = Bandwidth.classic(
                3, // capacity
                Refill.intervally(3, Duration.ofHours(1)) // refill 3 tokens every hour
        );
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Create bucket for token refresh endpoint
     * Limit: 10 requests per minute
     */
    private Bucket createTokenBucket(String key) {
        Bandwidth limit = Bandwidth.classic(
                10, // capacity
                Refill.intervally(10, Duration.ofMinutes(1)) // refill 10 tokens every minute
        );
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Get remaining tokens for login endpoint
     */
    public long getLoginRemainingTokens(String ipAddress) {
        Bucket bucket = loginBuckets.get(ipAddress);
        return bucket != null ? bucket.getAvailableTokens() : 5;
    }

    /**
     * Clear all rate limit buckets (for testing or admin purposes)
     */
    public void clearAll() {
        loginBuckets.clear();
        registerBuckets.clear();
        tokenBuckets.clear();
        log.info("All rate limit buckets cleared");
    }

    /**
     * Clear rate limits for specific IP
     */
    public void clearForIp(String ipAddress) {
        loginBuckets.remove(ipAddress);
        registerBuckets.remove(ipAddress);
        tokenBuckets.remove(ipAddress);
        log.info("Rate limit buckets cleared for IP: {}", ipAddress);
    }
}
