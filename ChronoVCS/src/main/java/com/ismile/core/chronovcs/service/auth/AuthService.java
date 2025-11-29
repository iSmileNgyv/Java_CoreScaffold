package com.ismile.core.chronovcs.service.auth;

import com.ismile.core.chronovcs.entity.UserEntity;
import com.ismile.core.chronovcs.exception.UnauthorizedException;
import com.ismile.core.chronovcs.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthenticatedUser authenticateBasic(String email, String rawToken) {
        UserEntity user = userRepository
                .findByEmailAndIsActiveTrue(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or token"));

        if (user.getTokenHash() == null ||
                !passwordEncoder.matches(rawToken, user.getTokenHash())) {
            throw new UnauthorizedException("Invalid email or token");
        }

        return AuthenticatedUser.fromEntity(user);
    }
}