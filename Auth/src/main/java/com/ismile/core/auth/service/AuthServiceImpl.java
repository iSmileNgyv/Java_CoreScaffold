package com.ismile.core.auth.service;

import auth.*;
import com.ismile.core.auth.util.GrpcContextUtil;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl extends AuthServiceGrpc.AuthServiceImplBase {

    private final AuthenticationService authenticationService;
    private static final Context.Key<Metadata> METADATA_KEY = Context.key("metadata");

    /**
     * Register new user
     */
    @Override
    public void register(RegisterRequest request, StreamObserver<AuthResponse> responseObserver) {
        try {
            String ipAddress = extractIpAddress();

            AuthResponse response = authenticationService.register(
                    request.getUsername(),
                    request.getPassword(),
                    request.getName(),
                    request.getSurname(),
                    request.getPhoneNumber(),
                    ipAddress
            );

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (com.ismile.core.auth.exception.SecurityException e) {
            log.warn("Registration failed: {}", e.getMessage());
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Registration error: ", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Registration failed")
                    .asRuntimeException());
        }
    }

    /**
     * Login user
     */
    @Override
    public void login(LoginRequest request, StreamObserver<AuthResponse> responseObserver) {
        try {
            String ipAddress = extractIpAddress();

            AuthResponse response = authenticationService.login(
                    request.getUsername(),
                    request.getPassword(),
                    ipAddress
            );

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (com.ismile.core.auth.exception.SecurityException e) {
            log.warn("Login failed for {}: {}", request.getUsername(), e.getMessage());

            AuthResponse errorResponse = AuthResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build();

            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Login error: ", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Login failed")
                    .asRuntimeException());
        }
    }

    /**
     * Logout user
     */
    @Override
    public void logout(LogoutRequest request, StreamObserver<LogoutResponse> responseObserver) {
        try {
            String ipAddress = extractIpAddress();

            authenticationService.logout(request.getRefreshToken(), ipAddress);

            LogoutResponse response = LogoutResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Logged out successfully")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Logout error: ", e);

            LogoutResponse errorResponse = LogoutResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build();

            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    /**
     * Refresh access token
     */
    @Override
    public void refreshToken(RefreshTokenRequest request, StreamObserver<AuthResponse> responseObserver) {
        try {
            AuthResponse response = authenticationService.refreshToken(request.getRefreshToken());

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (com.ismile.core.auth.exception.SecurityException e) {
            log.warn("Token refresh failed: {}", e.getMessage());

            AuthResponse errorResponse = AuthResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build();

            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Token refresh error: ", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Token refresh failed")
                    .asRuntimeException());
        }
    }

    /**
     * Protected endpoint - requires JWT token
     */
    @Override
    public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        // Extract authenticated user info from context
        Integer userId = GrpcContextUtil.getUserId();
        String username = GrpcContextUtil.getUsername();

        String message = String.format(
                "Hello, %s! You are authenticated as user: %s (ID: %d)",
                request.getName(),
                username != null ? username : "unknown",
                userId != null ? userId : 0
        );

        HelloReply reply = HelloReply.newBuilder()
                .setMessage(message)
                .build();

        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    /**
     * Extract IP address from gRPC metadata
     */
    private String extractIpAddress() {
        try {
            Metadata metadata = METADATA_KEY.get();
            if (metadata != null) {
                return GrpcContextUtil.getIpAddress(metadata);
            }
        } catch (Exception e) {
            log.debug("Could not extract IP address: {}", e.getMessage());
        }
        return "unknown";
    }
}