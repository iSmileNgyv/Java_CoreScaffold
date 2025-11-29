package com.ismile.core.chronovcs.security;

import com.ismile.core.chronovcs.entity.UserEntity;
import com.ismile.core.chronovcs.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PatAuthenticationProvider implements AuthenticationProvider {

    private final ChronoUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getName();
        String rawToken = authentication.getCredentials().toString();

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        if (!passwordEncoder.matches(rawToken, userDetails.getPassword())) {
            throw new BadCredentialsException("Invalid token");
        }

        // Burda UserEntity-ni da götürmək istəyirik ki, sonradan AuthenticatedUser-də istifadə edək
        UserEntity userEntity = userRepository
                .findByEmailAndIsActiveTrue(email)
                .orElseThrow(() -> new BadCredentialsException("User not found or inactive"));

        // Authentication içində custom principal saxlaya bilərik
        ChronoPrincipal principal = ChronoPrincipal.fromEntity(userEntity);

        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                userDetails.getAuthorities()
        );
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
