package com.ismile.core.auth.service;

import io.envoyproxy.envoy.service.auth.v3.AuthorizationGrpc;
import io.envoyproxy.envoy.service.auth.v3.CheckRequest;
import io.envoyproxy.envoy.service.auth.v3.CheckResponse;
import io.envoyproxy.envoy.service.auth.v3.OkHttpResponse;
import io.envoyproxy.envoy.service.auth.v3.DeniedHttpResponse;
import com.google.rpc.Status;
import com.google.rpc.Code;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class AuthorizationServiceImpl extends AuthorizationGrpc.AuthorizationImplBase {

    @Override
    public void check(CheckRequest request, StreamObserver<CheckResponse> responseObserver) {
        try {
            String path = request.getAttributes().getRequest().getHttp().getPath();
            String authHeader = request.getAttributes().getRequest().getHttp()
                    .getHeadersOrDefault("authorization", "");

            System.out.println("=== AUTH CHECK ===");
            System.out.println("Path: " + path);
            System.out.println("Auth header: " + authHeader);

            // Token yoxlaması
            if (authHeader.isEmpty() || !authHeader.startsWith("Bearer ")) {
                System.out.println("❌ DENIED - Unauthenticated");
                responseObserver.onError(io.grpc.Status.UNAUTHENTICATED.withDescription("Unauthenticated").asRuntimeException());
                return;
            }

            // Token validdir
            System.out.println("✅ ALLOWED");

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

            // Exception halında onError göndər
            responseObserver.onError(
                    io.grpc.Status.INTERNAL
                            .withDescription("Internal error: " + e.getMessage())
                            .asRuntimeException()
            );
        }
    }
}