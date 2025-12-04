package com.ismile.argusomnicli.grpc;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.grpc.MethodDescriptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Marshaller for DynamicMessage to support dynamic gRPC calls.
 * Follows Single Responsibility - only marshals messages.
 */
public class DynamicMessageMarshaller implements MethodDescriptor.Marshaller<DynamicMessage> {

    private final Descriptors.Descriptor messageDescriptor;

    public DynamicMessageMarshaller(Descriptors.Descriptor messageDescriptor) {
        this.messageDescriptor = messageDescriptor;
    }

    @Override
    public InputStream stream(DynamicMessage value) {
        return new ByteArrayInputStream(value.toByteArray());
    }

    @Override
    public DynamicMessage parse(InputStream stream) {
        try {
            return DynamicMessage.parseFrom(messageDescriptor, stream);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse dynamic message", e);
        }
    }
}
