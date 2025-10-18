package com.ismile.core.auth.service;

import com.google.rpc.Code;
import com.google.rpc.Status;
import com.ismile.core.auth.security.JwtTokenProvider;
import io.envoyproxy.envoy.service.auth.v3.AuthorizationGrpc;
import io.envoyproxy.envoy.service.auth.v3.CheckRequest;
import io.envoyproxy.envoy.service.auth.v3.CheckResponse;
import io.envoyproxy.envoy.service.auth.v3.DeniedHttpResponse;
import io.envoyproxy.envoy.service.auth.v3.OkHttpResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;
import io.envoyproxy.envoy.type.v3.HttpStatus;
import io.envoyproxy.envoy.type.v3.StatusCode; // Corrected import

import java.util.List;

/**
 * gRPC service that implements Envoy's External Authorization API.
 * This service is the central point for authenticating and authorizing ALL
 * incoming requests that pass through the Envoy gateway.
 */
@GrpcService
@RequiredArgsConstructor
@Slf4j
public class AuthorizationServiceImpl extends AuthorizationGrpc.AuthorizationImplBase {

    // Using the more robust JwtTokenProvider for consistency across the service.
    private final JwtTokenProvider jwtTokenProvider;
    // Injecting the service that contains the core permission checking logic.
    private final AuthorizationCheckService authorizationCheckService;

    /**
     * This method is called by Envoy for every incoming request that requires authorization.
     * It performs both Authentication (validating the JWT) and Authorization (checking permissions).
     *
     * @param request The request from Envoy, containing headers and path information.
     * @param responseObserver The observer to send the response (Allow/Deny) back to Envoy.
     */
    @Override
    public void check(CheckRequest request, StreamObserver<CheckResponse> responseObserver) {
        try {
            // Extract the full path of the request, e.g., "/otp.OtpService/SendCode"
            String fullPath = request.getAttributes().getRequest().getHttp().getPath();
            // Remove the leading slash to match the format stored in the OperationEntity
            String operationCode = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;

            // Extract the "authorization" header from the request
            String authHeader = request.getAttributes().getRequest().getHttp()
                    .getHeadersOrDefault("authorization", "");

            log.debug("=== Envoy Auth Check Initiated ===");
            log.debug("Operation Code (Path): {}", operationCode);
            log.debug("Authorization Header: {}", authHeader);

            // --- 1. AUTHENTICATION ---
            // Check if the token exists and has the "Bearer " prefix.
            if (!authHeader.startsWith("Bearer ")) {
                log.warn("DENIED - Missing or malformed Bearer token for operation: {}", operationCode);
                denyRequest(responseObserver, "Authorization header is missing or invalid.");
                return;
            }

            String token = authHeader.substring(7);

            // Validate the JWT. This will throw an exception if the token is invalid or expired.
            jwtTokenProvider.validateToken(token);

            // --- 2. AUTHORIZATION ---
            // If the token is valid, extract roles to check permissions.
            List<String> userRoles = jwtTokenProvider.getRolesFromToken(token);
            String username = jwtTokenProvider.getUsernameFromToken(token);

            // Use AuthorizationCheckService to check if any of the user's roles have permission for this operation.
            if (!authorizationCheckService.hasPermission(userRoles, operationCode)) {
                log.warn("DENIED - User '{}' with roles {} does not have permission for operation: {}", username, userRoles, operationCode);
                denyRequest(responseObserver, "You do not have permission to perform this action.");
                return;
            }

            // --- 3. ALLOW ---
            // If both authentication and authorization are successful, allow the request.
            log.info("ALLOWED - User '{}' has access to operation: {}", username, operationCode);
            allowRequest(responseObserver);

        } catch (Exception e) {
            // This catches errors from token validation (e.g., expired token) or any other internal errors.
            log.error("DENIED - Exception during auth check for path '{}': {}", request.getAttributes().getRequest().getHttp().getPath(), e.getMessage());
            denyRequest(responseObserver, "Access Denied: " + e.getMessage());
        }
    }

    /**
     * Constructs and sends an "OK" response to Envoy, allowing the request to proceed.
     */
    private void allowRequest(StreamObserver<CheckResponse> responseObserver) {
        CheckResponse response = CheckResponse.newBuilder()
                .setStatus(Status.newBuilder().setCode(Code.OK_VALUE).build())
                .setOkResponse(OkHttpResponse.newBuilder().build())
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * Constructs and sends a "Denied" response to Envoy, blocking the request.
     * Envoy will then return a 403 Forbidden HTTP status to the original client.
     */
    private void denyRequest(StreamObserver<CheckResponse> responseObserver, String message) {
        CheckResponse response = CheckResponse.newBuilder()
                .setStatus(Status.newBuilder()
                        .setCode(Code.PERMISSION_DENIED_VALUE)
                        .setMessage(message)
                        .build())
                .setDeniedResponse(DeniedHttpResponse.newBuilder()
                        // Corrected usage of the StatusCode enum
                        .setStatus(HttpStatus.newBuilder().setCode(StatusCode.Forbidden).build())
                        .build())
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}

