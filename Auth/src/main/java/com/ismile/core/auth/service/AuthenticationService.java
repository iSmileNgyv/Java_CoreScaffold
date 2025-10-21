package com.ismile.core.auth.service;

import auth.AuthResponse;
import auth.UserInfo;
import com.ismile.core.auth.entity.AuditLogEntity;
import com.ismile.core.auth.entity.RefreshTokenEntity;
import com.ismile.core.auth.entity.RoleEntity;
import com.ismile.core.auth.entity.UserEntity;
import com.ismile.core.auth.exception.SecurityException;
import com.ismile.core.auth.repository.AuditLogRepository;
import com.ismile.core.auth.repository.RefreshTokenRepository;
import com.ismile.core.auth.repository.RoleRepository;
import com.ismile.core.auth.repository.UserRepository;
import com.ismile.core.auth.security.*;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import otp.OtpServiceGrpc;
import otp.OtpType;
import otp.SendCodeRequest;
import otp.VerifyCodeRequest;
import otp.VerifyCodeResponse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Authentication business logic
 * Handles registration, login, logout, and token refresh
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogRepository auditLogRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginAttemptService loginAttemptService;
    private final OtpServiceGrpc.OtpServiceBlockingStub otpServiceBlockingStub;


    /**
     * Register new user
     */
    @Transactional
    public AuthResponse register(String username, String password, String name,
                                 String surname, String phoneNumber, String ipAddress) {

        // Validate inputs
        InputValidator.validateUsername(username);
        InputValidator.validateName(name, "Name");
        InputValidator.validateName(surname, "Surname");
        InputValidator.validatePhoneNumber(phoneNumber);
        passwordValidator.validate(password);

        // Sanitize inputs
        username = InputValidator.sanitize(username);
        name = InputValidator.sanitize(name);
        surname = InputValidator.sanitize(surname);
        phoneNumber = InputValidator.normalizePhoneNumber(phoneNumber);

        // Check if user exists
        if (userRepository.existsByUsername(username)) {
            throw new SecurityException("Username already exists");
        }

        if (phoneNumber != null && userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new SecurityException("Phone number already registered");
        }

        // Get default role
        RoleEntity defaultRole = roleRepository.findByDefaultRoleTrue()
                .orElseThrow(() -> new SecurityException("Default role not found"));

        // Create user
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setName(name);
        user.setSurname(surname);
        user.setPhoneNumber(phoneNumber);
        user.setAccountLocked(false);

        // Assign default role
        List<RoleEntity> roles = new ArrayList<>();
        roles.add(defaultRole);
        user.setRoles(roles);

        userRepository.save(user);

        // Audit log
        logAudit(user, AuditLogEntity.AuditEventType.REGISTER, ipAddress, "User registered", true);

        log.info("User registered successfully: {}", username);

        // Generate tokens
        return generateAuthResponse(user);
    }

    /**
     * Login user. If OTP is enabled for the user, it initiates the OTP flow.
     * Otherwise, it completes the login and returns JWT tokens.
     */
    @Transactional
    public AuthResponse login(String username, String password, String ipAddress) {

        // Find user
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    loginAttemptService.recordLoginAttempt(username, false, ipAddress, "User not found");
                    return new SecurityException("Invalid username or password");
                });

        // Check if account is locked
        if (loginAttemptService.isLocked(user)) {
            logAudit(user, AuditLogEntity.AuditEventType.LOGIN_FAILED, ipAddress, "Account locked", false);
            throw new SecurityException("Account is locked. Please try again later.");
        }

        // Check if account is active
        if (!user.isActive()) {
            logAudit(user, AuditLogEntity.AuditEventType.LOGIN_FAILED, ipAddress, "Account inactive", false);
            throw new SecurityException("Account is inactive");
        }

        // Verify password
        if (!passwordEncoder.matches(password, user.getPassword())) {
            loginAttemptService.recordLoginAttempt(username, false, ipAddress, "Invalid password");
            logAudit(user, AuditLogEntity.AuditEventType.LOGIN_FAILED, ipAddress, "Invalid password", false);
            throw new SecurityException("Invalid username or password");
        }

        // --- OTP FLOW ---
        // If user has OTP enabled, send OTP and return an intermediate response.
        if (user.isLoginOtp()) {
            log.info("OTP login is enabled for user: {}. Initiating OTP flow.", username);
            try {
                otpServiceBlockingStub.sendCode(SendCodeRequest.newBuilder()
                        .setUserId(user.getId())
                        .setType(OtpType.LOGIN)
                        .build());

                // Return a response indicating that OTP is now required to proceed.
                return AuthResponse.newBuilder()
                        .setSuccess(true)
                        .setMessage("OTP has been sent to your registered device.")
                        .setOtpRequired(true)
                        .build();
            } catch (Exception e) {
                log.error("Failed to send OTP for user: {}", username, e);
                throw new SecurityException("Could not send OTP. Please try again later.");
            }
        }


        // --- STANDARD LOGIN FLOW ---
        // Success - reset attempts
        loginAttemptService.resetLoginAttempts(username);
        loginAttemptService.recordLoginAttempt(username, true, ipAddress, null);

        // Update last login
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        logAudit(user, AuditLogEntity.AuditEventType.LOGIN_SUCCESS, ipAddress, "Login successful", true);

        log.info("User logged in successfully: {}", username);

        return generateAuthResponse(user);
    }

    /**
     * Verifies the OTP and, if successful, completes the login process by generating JWTs.
     * @param username The user's username.
     * @param otpCode The OTP code provided by the user.
     * @param ipAddress The IP address of the user.
     * @return AuthResponse containing JWTs upon successful verification.
     */
    @Transactional
    public AuthResponse verifyOtpAndLogin(String username, String otpCode, String ipAddress) {
        // 1. Find user
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new SecurityException("Invalid username or OTP."));

        // 2. Call OTP service to verify the code
        try {
            VerifyCodeRequest verifyRequest = VerifyCodeRequest.newBuilder()
                    .setUserId(user.getId())
                    .setType(OtpType.LOGIN)
                    .setCode(otpCode)
                    .build();
            VerifyCodeResponse otpResponse = otpServiceBlockingStub.verifyCode(verifyRequest);

            if (!otpResponse.getSuccess()) {
                log.warn("OTP service returned unsuccessful verification for user: {}", username);
                throw new SecurityException("Invalid or expired OTP code.");
            }
        } catch (StatusRuntimeException e) {
            log.warn("OTP verification gRPC call failed for user {}: {}", username, e.getStatus());
            throw new SecurityException("Invalid or expired OTP code.");
        }

        // 3. If OTP is correct, proceed with login success steps
        loginAttemptService.resetLoginAttempts(username);
        loginAttemptService.recordLoginAttempt(username, true, ipAddress, "Login successful via OTP");
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
        logAudit(user, AuditLogEntity.AuditEventType.LOGIN_SUCCESS, ipAddress, "Login successful with OTP", true);
        log.info("User logged in successfully with OTP: {}", username);

        // 4. Generate and return tokens
        return generateAuthResponse(user);
    }


    /**
     * Logout user
     */
    @Transactional
    public void logout(String refreshToken, String ipAddress) {
        RefreshTokenEntity token = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new SecurityException("Invalid refresh token"));

        token.setRevoked(true);
        token.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(token);

        logAudit(token.getUser(), AuditLogEntity.AuditEventType.LOGOUT, ipAddress, "User logged out", true);

        log.info("User logged out: {}", token.getUser().getUsername());
    }

    /**
     * Refresh access token
     */
    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        RefreshTokenEntity token = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new SecurityException("Invalid refresh token"));

        if (!token.isValid()) {
            throw new SecurityException("Refresh token expired or revoked");
        }

        UserEntity user = token.getUser();

        logAudit(user, AuditLogEntity.AuditEventType.TOKEN_REFRESH, null, "Token refreshed", true);

        return generateAuthResponse(user);
    }

    /**
     * Generate auth response with tokens
     */
    private AuthResponse generateAuthResponse(UserEntity user) {
        List<String> roles = user.getRoles().stream()
                .map(RoleEntity::getCode)
                .collect(Collectors.toList());

        String accessToken = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), roles);
        String refreshToken = UUID.randomUUID().toString();

        // Save refresh token
        RefreshTokenEntity refreshTokenEntity = RefreshTokenEntity.builder()
                .token(refreshToken)
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshTokenEntity);

        // Build user info
        UserInfo userInfo = UserInfo.newBuilder()
                .setId(user.getId())
                .setUsername(user.getUsername())
                .setName(user.getName())
                .setSurname(user.getSurname())
                .setPhoneNumber(user.getPhoneNumber() != null ? user.getPhoneNumber() : "")
                .addAllRoles(roles)
                .build();

        return AuthResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Authentication successful")
                .setToken(accessToken)
                .setRefreshToken(refreshToken)
                .setUser(userInfo)
                .build();
    }

    /**
     * Log audit event
     */
    private void logAudit(UserEntity user, AuditLogEntity.AuditEventType eventType,
                          String ipAddress, String details, boolean success) {
        AuditLogEntity audit = AuditLogEntity.builder()
                .user(user)
                .username(user.getUsername())
                .eventType(eventType)
                .ipAddress(ipAddress)
                .details(details)
                .success(success)
                .build();

        auditLogRepository.save(audit);
    }
}
