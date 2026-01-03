package com.ismile.core.chronovcs.service.auth;

import com.ismile.core.chronovcs.dto.token.CreateTokenRequest;
import com.ismile.core.chronovcs.dto.token.TokenResponse;
import com.ismile.core.chronovcs.dto.token.TokenSummaryDto;
import com.ismile.core.chronovcs.entity.UserEntity;
import com.ismile.core.chronovcs.entity.UserTokenEntity;
import com.ismile.core.chronovcs.repository.UserTokenRepository;
import com.ismile.core.chronovcs.security.provider.PatTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final UserTokenRepository userTokenRepository;
    private final PatTokenProvider patTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public TokenResponse createToken(UserEntity user, CreateTokenRequest request) {
        // Validation
        if (request.getTokenName() == null || request.getTokenName().isBlank()) {
            throw new IllegalArgumentException("Token name cannot be empty");
        }

        PatTokenProvider.TokenPair tokenPair = patTokenProvider.generate();
        String hashedToken = passwordEncoder.encode(tokenPair.getFullToken());

        UserTokenEntity entity = UserTokenEntity.builder()
                .user(user)
                .tokenName(request.getTokenName()) // <--- DÜZƏLİŞ: .tokenName() istifadə olunur
                .tokenHash(hashedToken)
                .tokenPrefix(tokenPair.getPrefix())
                .revoked(false)
                .build();

        if (request.getExpiresInDays() != null && request.getExpiresInDays() > 0) {
            entity.setExpiresAt(LocalDateTime.now().plusDays(request.getExpiresInDays()));
        }

        entity = userTokenRepository.save(entity);

        return TokenResponse.builder()
                .id(entity.getId())
                .tokenName(entity.getTokenName()) // <--- Düzəliş
                .rawToken(tokenPair.getFullToken())
                .tokenPrefix(entity.getTokenPrefix())
                .expiresAt(entity.getExpiresAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<TokenSummaryDto> listTokens(Long userId) {
        List<UserTokenEntity> tokens = userTokenRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        List<TokenSummaryDto> result = new ArrayList<>();
        for (UserTokenEntity token : tokens) {
            result.add(new TokenSummaryDto(
                    token.getId(),
                    token.getTokenName(),
                    token.getTokenPrefix(),
                    token.getExpiresAt(),
                    token.getCreatedAt(),
                    token.getLastUsedAt(),
                    token.isRevoked()
            ));
        }
        return result;
    }

    @Transactional
    public void revokeToken(Long userId, Long tokenId) {
        UserTokenEntity token = userTokenRepository.findById(tokenId)
                .orElseThrow(() -> new IllegalArgumentException("Token not found"));
        if (!token.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Token not found");
        }
        if (!token.isRevoked()) {
            token.setRevoked(true);
            userTokenRepository.save(token);
        }
    }
}
