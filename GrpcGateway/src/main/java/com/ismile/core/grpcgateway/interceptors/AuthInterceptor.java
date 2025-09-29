package com.ismile.core.grpcgateway.interceptors;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

public class AuthInterceptor implements ServerInterceptor {
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata, ServerCallHandler<ReqT, RespT> serverCallHandler) {
        String token = metadata.get(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER));
        if(token == null) {
            serverCall.close(io.grpc.Status.UNAUTHENTICATED.withDescription("Invalid or missing token"), metadata);
            return new ServerCall.Listener<>() {};
        }
        return serverCallHandler.startCall(serverCall, metadata);
    }
}
