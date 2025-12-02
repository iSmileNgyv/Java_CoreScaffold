package com.ismile.core.chronovcs.security;

import com.ismile.core.chronovcs.config.security.ChronoUserPrincipal;
import com.ismile.core.chronovcs.entity.UserEntity;
import com.ismile.core.chronovcs.repository.UserRepository;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChronoUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // 1. Useri email-ə görə tapırıq
        UserEntity user = userRepository
                .findByEmailAndActiveTrue(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found or inactive: " + email));

        // 2. UserEntity-ni AuthenticatedUser DTO-sunu çeviririk
        AuthenticatedUser authUser = AuthenticatedUser.fromEntity(user);

        // 3. ChronoUserPrincipal qaytarırıq.
        // DİQQƏT: Normalda ChronoUserPrincipal parolu null qaytarır (təhlükəsizlik üçün).
        // Amma burada Login prosesi getdiyi üçün, Spring Security parolu yoxlamalıdır.
        // Ona görə də anonim klass ilə getPassword() metodunu override edirik.
        return new ChronoUserPrincipal(authUser) {
            @Override
            public String getPassword() {
                return user.getPasswordHash(); // Bazadakı BCrypt hash
            }
        };
    }
}