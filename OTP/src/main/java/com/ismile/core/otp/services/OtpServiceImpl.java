package com.ismile.core.otp.services;

import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;
import otp.Otp;
import otp.OtpServiceGrpc;

@GrpcService
public class OtpServiceImpl extends OtpServiceGrpc.OtpServiceImplBase {
    @Override
    public void sendCode(Otp.OtpRequest request, StreamObserver<Otp.OtpReply> responseObserver) {
        String msg = "OTP code sent to " + request.getName();
        Otp.OtpReply reply = Otp.OtpReply.newBuilder()
                .setMessage(msg)
                .build();

        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }
}
