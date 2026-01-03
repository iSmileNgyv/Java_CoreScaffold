package com.ismile.core.chronovcs.security;

import com.ismile.core.chronovcs.config.security.ChronoUserPrincipal; // Importu dəqiqləşdiririk
import com.ismile.core.chronovcs.entity.UserEntity;
import com.ismile.core.chronovcs.entity.UserTokenEntity;
import com.ismile.core.chronovcs.repository.UserRepository;
import com.ismile.core.chronovcs.repository.UserTokenRepository;
import com.ismile.core.chronovcs.security.provider.PatTokenProvider;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser; // DTO importu
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PatAuthenticationProvider implements AuthenticationProvider {

    private final UserRepository userRepository;
    private final UserTokenRepository userTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PatTokenProvider patTokenProvider;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getName();
        String rawToken = authentication.getCredentials().toString();

        // 1. Check if the provided password looks like a PAT
        if (!patTokenProvider.isPatToken(rawToken)) {
            return null;
        }

        // 2. Find the user by email
        UserEntity user = userRepository.findByEmailAndActiveTrue(email)
                .orElseThrow(() -> new BadCredentialsException("User not found or inactive"));

        // 3. Find candidate tokens using the prefix
        String prefix = patTokenProvider.extractPrefix(rawToken);
        List<UserTokenEntity> candidates = userTokenRepository.findByTokenPrefixAndRevokedFalse(prefix);

        // 4. Verify the hash
        UserTokenEntity matchedToken = candidates.stream()
                .filter(t -> t.getUser().getId().equals(user.getId()))
                .filter(UserTokenEntity::isValid)
                .filter(t -> passwordEncoder.matches(rawToken, t.getTokenHash()))
                .findFirst()
                .orElseThrow(() -> new BadCredentialsException("Invalid Personal Access Token"));

        // 5. Update usage stats
        matchedToken.setLastUsedAt(LocalDateTime.now());
        userTokenRepository.save(matchedToken);

        // 6. Return successful authentication
        // --- DÜZƏLİŞ BURADADIR ---
        // UserEntity-ni AuthenticatedUser DTO-ya çeviririk
        AuthenticatedUser authUser = AuthenticatedUser.fromEntity(user);
        authUser.setTokenId(matchedToken.getId());

        return new UsernamePasswordAuthenticationToken(
                new ChronoUserPrincipal(authUser), // İndi düzgün tipi qəbul edir
                null,
                Collections.emptyList()
        );
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
