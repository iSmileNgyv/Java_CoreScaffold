package com.ismile.core.chronovcs.service.auth;

import com.ismile.core.chronovcs.config.security.ChronoUserPrincipal;
import com.ismile.core.chronovcs.config.security.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtProperties jwtProperties;

    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();

        // DÜZƏLİŞ: User ID-ni tokenə əlavə edirik
        if (userDetails instanceof ChronoUserPrincipal) {
            AuthenticatedUser user = ((ChronoUserPrincipal) userDetails).getUser();
            claims.put("userId", user.getUserId());
            claims.put("userUid", user.getUserUid());
        }

        return buildToken(claims, userDetails, jwtProperties.getAccessTtlSeconds() * 1000);
    }

    // Token-i yoxlayır və UserDetails çıxarır
    public UserDetails getUserFromToken(String token) {
        String email = extractUsername(token);

        // DÜZƏLİŞ: User ID-ni tokendən geri oxuyuruq
        Claims claims = extractAllClaims(token);
        Long userId = claims.get("userId", Long.class);
        String userUid = claims.get("userUid", String.class);

        AuthenticatedUser authUser = new AuthenticatedUser();
        authUser.setEmail(email);
        authUser.setUserId(userId); // <--- Ən vacib hissə budur!
        authUser.setUserUid(userUid);

        return new ChronoUserPrincipal(authUser);
    }

    public boolean validateToken(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails, long expiration) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
