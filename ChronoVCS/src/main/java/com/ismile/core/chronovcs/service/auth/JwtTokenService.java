package com.ismile.core.chronovcs.service.auth;

import com.ismile.core.chronovcs.config.security.JwtProperties;
import com.ismile.core.chronovcs.dto.auth.TokenPair;
import com.ismile.core.chronovcs.exception.UnauthorizedException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Instant;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtProperties properties;

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(properties.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public TokenPair generateTokens(AuthenticatedUser user) {
        String access = generateToken(user, "access", properties.getAccessTtlSeconds());
        String refresh = generateToken(user, "refresh", properties.getRefreshTtlSeconds());
        return new TokenPair(access, refresh);
    }

    private String generateToken(AuthenticatedUser user, String type, long ttlSeconds) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);

        return Jwts.builder()
                .setSubject(user.getUserUid())
                .claim("uid", user.getUserUid())
                .claim("email", user.getEmail())
                .claim("type", type)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public AuthenticatedUser parseAccessToken(String token) {
        return parseToken(token, "access");
    }

    public AuthenticatedUser parseRefreshToken(String token) {
        return parseToken(token, "refresh");
    }

    private AuthenticatedUser parseToken(String token, String expectedType) {
        try {
            Jws<Claims> jws = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);

            Claims claims = jws.getBody();
            String type = claims.get("type", String.class);

            if (!expectedType.equals(type)) {
                throw new UnauthorizedException("Invalid token type");
            }

            String uid = claims.get("uid", String.class);
            String email = claims.get("email", String.class);

            if (uid == null || email == null) {
                throw new UnauthorizedException("Invalid token payload");
            }

            AuthenticatedUser user = new AuthenticatedUser();
            user.setUserUid(uid);
            user.setEmail(email);
            // id-ni claim-lərə əlavə etmək istəsən, burda da çıxardıb set edə bilərsən.

            return user;

        } catch (ExpiredJwtException e) {
            throw new UnauthorizedException("Token expired");
        } catch (JwtException e) {
            throw new UnauthorizedException("Invalid token");
        }
    }
}