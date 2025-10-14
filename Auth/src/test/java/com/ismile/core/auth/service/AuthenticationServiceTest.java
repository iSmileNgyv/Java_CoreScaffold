package com.ismile.core.auth.service;

import auth.AuthResponse;
import com.ismile.core.auth.entity.RoleEntity;
import com.ismile.core.auth.entity.UserEntity;
import com.ismile.core.auth.exception.SecurityException;
import com.ismile.core.auth.repository.AuditLogRepository;
import com.ismile.core.auth.repository.RefreshTokenRepository;
import com.ismile.core.auth.repository.RoleRepository;
import com.ismile.core.auth.repository.UserRepository;
import com.ismile.core.auth.security.JwtTokenProvider;
import com.ismile.core.auth.security.PasswordValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordValidator passwordValidator;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private LoginAttemptService loginAttemptService;

    @InjectMocks
    private AuthenticationService authenticationService;

    private UserEntity user;
    private RoleEntity defaultRole;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("password");
        user.setName("Test");
        user.setSurname("User");
        user.setPhoneNumber("1234567890");

        defaultRole = new RoleEntity();
        defaultRole.setId(1L);
        defaultRole.setName("ROLE_USER");
        defaultRole.setCode("USER");
        defaultRole.setDefaultRole(true);
    }

    @Test
    void testRegister_Success() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("1234567890")).thenReturn(false);
        when(roleRepository.findByDefaultRoleTrue()).thenReturn(Optional.of(defaultRole));
        when(passwordEncoder.encode("password")).thenReturn("encodedPassword");
        when(userRepository.save(any(UserEntity.class))).thenReturn(user);
        when(jwtTokenProvider.generateToken(any(), any(), any())).thenReturn("accessToken");

        AuthResponse response = authenticationService.register("testuser", "password", "Test", "User", "1234567890", "127.0.0.1");

        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertEquals("Authentication successful", response.getMessage());
        assertEquals("accessToken", response.getToken());
    }

    @Test
    void testRegister_UsernameExists() {
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        SecurityException exception = assertThrows(SecurityException.class, () -> {
            authenticationService.register("testuser", "password", "Test", "User", "1234567890", "127.0.0.1");
        });

        assertEquals("Username already exists", exception.getMessage());
    }

    @Test
    void testRegister_InvalidPassword() {
        doNothing().when(passwordValidator).validate("invalidpassword");

        assertThrows(SecurityException.class, () -> {
            authenticationService.register("testuser", "invalidpassword", "Test", "User", "1234567890", "127.0.0.1");
        });
    }

    @Test
    void testLogin_Success() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(loginAttemptService.isLocked(user)).thenReturn(false);
        user.setActive(true);
        when(passwordEncoder.matches("password", "password")).thenReturn(true);
        when(jwtTokenProvider.generateToken(any(), any(), any())).thenReturn("accessToken");

        AuthResponse response = authenticationService.login("testuser", "password", "127.0.0.1");

        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertEquals("accessToken", response.getToken());
    }

    @Test
    void testLogin_InvalidUsername() {
        when(userRepository.findByUsername("wronguser")).thenReturn(Optional.empty());

        SecurityException exception = assertThrows(SecurityException.class, () -> {
            authenticationService.login("wronguser", "password", "127.0.0.1");
        });

        assertEquals("Invalid username or password", exception.getMessage());
    }

    @Test
    void testLogin_AccountLocked() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(loginAttemptService.isLocked(user)).thenReturn(true);

        SecurityException exception = assertThrows(SecurityException.class, () -> {
            authenticationService.login("testuser", "password", "127.0.0.1");
        });

        assertEquals("Account is locked. Please try again later.", exception.getMessage());
    }

    @Test
    void testLogout_Success() {
        RefreshTokenEntity refreshTokenEntity = RefreshTokenEntity.builder()
                .token("refreshToken")
                .user(user)
                .build();
        when(refreshTokenRepository.findByToken("refreshToken")).thenReturn(Optional.of(refreshTokenEntity));

        authenticationService.logout("refreshToken", "127.0.0.1");

        assertTrue(refreshTokenEntity.isRevoked());
        assertNotNull(refreshTokenEntity.getRevokedAt());
    }

    @Test
    void testRefreshToken_Success() {
        RefreshTokenEntity refreshTokenEntity = RefreshTokenEntity.builder()
                .token("refreshToken")
                .user(user)
                .expiresAt(java.time.LocalDateTime.now().plusDays(1))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByToken("refreshToken")).thenReturn(Optional.of(refreshTokenEntity));
        when(jwtTokenProvider.generateToken(any(), any(), any())).thenReturn("newAccessToken");

        AuthResponse response = authenticationService.refreshToken("refreshToken");

        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertEquals("newAccessToken", response.getToken());
    }

    @Test
    void testRefreshToken_InvalidToken() {
        when(refreshTokenRepository.findByToken("invalidToken")).thenReturn(Optional.empty());

        SecurityException exception = assertThrows(SecurityException.class, () -> {
            authenticationService.refreshToken("invalidToken");
        });

        assertEquals("Invalid refresh token", exception.getMessage());
    }
}
