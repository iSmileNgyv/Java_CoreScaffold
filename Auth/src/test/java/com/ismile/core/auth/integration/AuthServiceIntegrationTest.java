package com.ismile.core.auth.integration;

import com.ismile.core.auth.entity.UserEntity;
import com.ismile.core.auth.repository.UserRepository;
import com.ismile.core.auth.service.AuthenticationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the AuthenticationService.
 * This class uses Testcontainers to spin up a real PostgreSQL database in a Docker container.
 * It tests the full flow from the service layer to the database.
 */
@SpringBootTest
@Testcontainers
public class AuthServiceIntegrationTest {

    // This will start a PostgreSQL container for the tests
    @Container
    public static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:15-alpine");

    // Dynamically set the database properties for the Spring application context
    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
    }

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Should register user and save it to the database")
    void register_shouldSaveUserToDatabase() {
        // Arrange
        String username = "integration_user";
        String password = "StrongPassword123!";
        String ipAddress = "192.168.1.1";

        // Act
        authenticationService.register(username, password, "Integration", "Test", "0501234567", ipAddress);

        // Assert
        // Verify directly from the database
        UserEntity savedUser = userRepository.findByUsername(username).orElse(null);

        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getId()).isPositive();
        assertThat(savedUser.getUsername()).isEqualTo(username);
        assertThat(savedUser.getName()).isEqualTo("Integration");
        assertThat(savedUser.getPhoneNumber()).isEqualTo("+994501234567"); // Check normalization
        assertThat(savedUser.getRoles()).hasSize(1);
        assertThat(savedUser.getRoles().stream().findFirst().get().getCode()).isEqualTo("USER");
    }

    @Test
    @DisplayName("Should register and then successfully login the user")
    void registerAndLogin_shouldWorkEndToEnd() {
        // Arrange - Register the user first
        String username = "login_user";
        String password = "ValidLoginPass123!";
        String ipAddress = "192.168.1.2";
        authenticationService.register(username, password, "Login", "Test", null, ipAddress);

        // Act - Try to log in with the same credentials
        var authResponse = authenticationService.login(username, password, ipAddress);

        // Assert
        assertThat(authResponse).isNotNull();
        assertThat(authResponse.getSuccess()).isTrue();
        assertThat(authResponse.getMessage()).isEqualTo("Authentication successful");
        assertThat(authResponse.getToken()).isNotBlank();
        assertThat(authResponse.getRefreshToken()).isNotBlank();
        assertThat(authResponse.getUser().getUsername()).isEqualTo(username);

        // Also check if last login was updated in DB
        UserEntity loggedInUser = userRepository.findByUsername(username).get();
        assertThat(loggedInUser.getLastLogin()).isNotNull();
    }
}
