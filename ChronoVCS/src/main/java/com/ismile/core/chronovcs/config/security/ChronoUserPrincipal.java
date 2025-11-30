package com.ismile.core.chronovcs.config.security;

import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@Getter
@AllArgsConstructor
public class ChronoUserPrincipal implements UserDetails {

    // Artıq UserEntity yox, yüngül DTO saxlayırıq (Lazy loading xətalarından qaçmaq üçün)
    private final AuthenticatedUser user;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList(); // Hələlik rol yoxdur
    }

    @Override
    public String getPassword() {
        return null; // Bizə parol lazım deyil
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}