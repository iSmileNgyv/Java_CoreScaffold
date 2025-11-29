package com.ismile.core.chronovcs.security.provider;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Token Provider
 * Next.js frontend üçün Bearer token yaradır və validate edir
 */
@Slf4j
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long validityMinutes;

    public JwtTokenProvider(String secret, long validityMinutes) {
        if (secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters (256 bits)");
        }

        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validityMinutes = validityMinutes;

        log.info("JwtTokenProvider initialized - validity: {} minutes", validityMinutes);
    }

    /**
     * JWT token yarat
     */
    public String createToken(Long userId, String email) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(validityMinutes * 60);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("email", email);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiry))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Token validate et və claims qaytar
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            log.warn("Token expired");
            throw e;
        } catch (JwtException e) {
            log.error("Invalid token: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * UserId çıxart
     */
    public Long getUserId(String token) {
        Claims claims = validateToken(token);
        Object userId = claims.get("userId");

        if (userId instanceof Integer) {
            return ((Integer) userId).longValue();
        }
        return (Long) userId;
    }

    /**
     * Email çıxart
     */
    public String getEmail(String token) {
        Claims claims = validateToken(token);
        return claims.get("email", String.class);
    }

    /**
     * Token valid mi?
     */
    public boolean isValid(String token) {
        try {
            validateToken(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}