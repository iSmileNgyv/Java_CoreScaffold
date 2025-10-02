package com.ismile.core.auth.service;

import com.google.rpc.Code;
import com.google.rpc.Status;
import com.ismile.core.auth.util.JwtUtil;
import io.envoyproxy.envoy.service.auth.v3.AuthorizationGrpc;
import io.envoyproxy.envoy.service.auth.v3.CheckRequest;
import io.envoyproxy.envoy.service.auth.v3.CheckResponse;
import io.envoyproxy.envoy.service.auth.v3.OkHttpResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
@RequiredArgsConstructor
public class AuthorizationServiceImpl extends AuthorizationGrpc.AuthorizationImplBase {

    private final JwtUtil jwtUtil;

    @Override
    public void check(CheckRequest request, StreamObserver<CheckResponse> responseObserver) {
        try {
            String path = request.getAttributes().getRequest().getHttp().getPath();
            String authHeader = request.getAttributes().getRequest().getHttp()
                    .getHeadersOrDefault("authorization", "");

            System.out.println("=== AUTH CHECK ===");
            System.out.println("Path: " + path);
            System.out.println("Auth header: " + authHeader);

            // Check token
            if (!authHeader.startsWith("Bearer ")) {
                System.out.println("❌ DENIED - No Bearer token");
                responseObserver.onError(
                        io.grpc.Status.UNAUTHENTICATED
                                .withDescription("Missing or invalid Authorization header")
                                .asRuntimeException()
                );
                return;
            }

            String token = authHeader.substring(7).trim();

            // JWT validation
            if (!jwtUtil.validateToken(token)) {
                System.out.println("❌ DENIED - Invalid token");
                responseObserver.onError(
                        io.grpc.Status.UNAUTHENTICATED
                                .withDescription("Invalid or expired token")
                                .asRuntimeException()
                );
                return;
            }

            // Token valid
            String username = jwtUtil.extractUsername(token);
            System.out.println("✅ ALLOWED - User: " + username);

            CheckResponse response = CheckResponse.newBuilder()
                    .setStatus(Status.newBuilder()
                            .setCode(Code.OK_VALUE)
                            .build())
                    .setOkResponse(OkHttpResponse.newBuilder().build())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            System.err.println("❌ EXCEPTION: " + e.getMessage());
            e.printStackTrace();

            responseObserver.onError(
                    io.grpc.Status.INTERNAL
                            .withDescription("Internal error: " + e.getMessage())
                            .asRuntimeException()
            );
        }
    }
}