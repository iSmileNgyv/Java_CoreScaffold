package com.ismile.core.chronovcs.config.security;

import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@Getter
@RequiredArgsConstructor
public class ChronoUserPrincipal implements UserDetails {

    private final AuthenticatedUser user;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // İndilik sadə: roles yoxdur
        return Collections.emptyList();
    }

    @Override
    public String getPassword() {
        return null; // biz burada password saxlamırıq
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}