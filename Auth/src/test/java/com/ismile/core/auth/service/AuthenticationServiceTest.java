package com.ismile.core.auth.service;

import com.ismile.core.auth.entity.RoleEntity;
import com.ismile.core.auth.entity.UserEntity;
import com.ismile.core.auth.exception.SecurityException;
import com.ismile.core.auth.repository.AuditLogRepository;
import com.ismile.core.auth.repository.RefreshTokenRepository;
import com.ismile.core.auth.repository.RoleRepository;
import com.ismile.core.auth.repository.UserRepository;
import com.ismile.core.auth.security.JwtTokenProvider;
import com.ismile.core.auth.security.LoginAttemptService;
import com.ismile.core.auth.security.PasswordValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the AuthenticationService.
 * This class tests the business logic of the service in isolation,
 * mocking all external dependencies like repositories and encoders.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    // The class we are testing
    @InjectMocks
    private AuthenticationService authenticationService;

    // Mocks for dependencies
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

    private UserEntity testUser;
    private RoleEntity userRole;

    @BeforeEach
    void setUp() {
        // Common setup for tests
        userRole = new RoleEntity();
        userRole.setCode("USER");
        userRole.setName("User");
        userRole.setDefaultRole(true);

        testUser = new UserEntity();
        testUser.setId(1);
        testUser.setUsername("testuser");
        testUser.setPassword("encodedPassword");
        testUser.setName("Test");
        testUser.setSurname("User");
        testUser.setActive(true);
    }

    @Test
    @DisplayName("Should register a new user successfully")
    void register_whenValidUser_shouldSucceed() {
        // Arrange
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(roleRepository.findByDefaultRoleTrue()).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode("Password123!")).thenReturn("encodedPassword");
        // Mocking the user saving part to return the user with an ID.
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity userToSave = invocation.getArgument(0);
            userToSave.setId(1); // Simulate saving and getting an ID
            return userToSave;
        });

        // Act
        authenticationService.register("newuser", "Password123!", "New", "User", null, "127.0.0.1");

        // Assert
        // Verify that the password validator was called
        verify(passwordValidator, times(1)).validate("Password123!");
        // Verify that the user repository's save method was called exactly once
        verify(userRepository, times(1)).save(any(UserEntity.class));
        // Verify that the audit log repository's save method was called
        verify(auditLogRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Should throw exception when registering with an existing username")
    void register_whenUsernameExists_shouldThrowException() {
        // Arrange
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> authenticationService.register("testuser", "Password123!", "Test", "User", null, "127.0.0.1"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Username already exists");
    }

    @Test
    @DisplayName("Should log in a user successfully with correct credentials")
    void login_whenCredentialsAreValid_shouldSucceed() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(loginAttemptService.isLocked(testUser)).thenReturn(false);
        when(passwordEncoder.matches("password123", "encodedPassword")).thenReturn(true);
        // Mock token generation
        when(jwtTokenProvider.generateToken(anyInt(), anyString(), any())).thenReturn("fake-jwt-token");


        // Act
        var authResponse = authenticationService.login("testuser", "password123", "127.0.0.1");

        // Assert
        assertThat(authResponse).isNotNull();
        assertThat(authResponse.getSuccess()).isTrue();
        assertThat(authResponse.getToken()).isEqualTo("fake-jwt-token");
        verify(loginAttemptService, times(1)).resetLoginAttempts("testuser");
        verify(userRepository, times(1)).save(testUser); // To save lastLogin time
    }

    @Test
    @DisplayName("Should fail login with incorrect password")
    void login_whenPasswordIsInvalid_shouldFail() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(loginAttemptService.isLocked(testUser)).thenReturn(false);
        when(passwordEncoder.matches("wrongpassword", "encodedPassword")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> authenticationService.login("testuser", "wrongpassword", "127.0.0.1"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Invalid username or password");

        // Verify that a failed login attempt was recorded
        verify(loginAttemptService, times(1)).recordLoginAttempt("testuser", false, "127.0.0.1", "Invalid password");
    }

    @Test
    @DisplayName("Should fail login for a locked account")
    void login_whenAccountIsLocked_shouldFail() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(loginAttemptService.isLocked(testUser)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> authenticationService.login("testuser", "password123", "127.0.0.1"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Account is locked. Please try again later.");
    }
}
