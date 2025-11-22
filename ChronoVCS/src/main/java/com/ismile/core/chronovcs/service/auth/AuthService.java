package com.ismile.core.chronovcs.service.auth;

import com.ismile.core.chronovcs.entity.UserEntity;
import com.ismile.core.chronovcs.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Basic Authorization header-ni parse edir və user-i doğrulayır.
     *
     * @param authorizationHeader "Basic base64(email:token)" formatında olmalıdır
     */
    public Optional<AuthenticatedUser> authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Basic ")) {
            return Optional.empty();
        }

        try {
            String base64Credentials = authorizationHeader.substring("Basic ".length()).trim();
            byte[] decodedBytes = Base64.getDecoder().decode(base64Credentials);
            String decoded = new String(decodedBytes, StandardCharsets.UTF_8);

            int colonIndex = decoded.indexOf(':');
            if (colonIndex == -1) {
                return Optional.empty();
            }

            String email = decoded.substring(0, colonIndex);
            String rawToken = decoded.substring(colonIndex + 1);

            Optional<UserEntity> userOpt = userRepository.findByEmailAndIsActiveTrue(email);
            if (userOpt.isEmpty()) {
                return Optional.empty();
            }

            UserEntity user = userOpt.get();
            String storedHash = user.getTokenHash();
            if (storedHash == null || storedHash.isBlank()) {
                return Optional.empty();
            }

            if (!passwordEncoder.matches(rawToken, storedHash)) {
                return Optional.empty();
            }

            // success
            return Optional.of(AuthenticatedUser.fromEntity(user));
        } catch (IllegalArgumentException e) {
            // Base64 decoding error və s.
            return Optional.empty();
        }
    }
}
