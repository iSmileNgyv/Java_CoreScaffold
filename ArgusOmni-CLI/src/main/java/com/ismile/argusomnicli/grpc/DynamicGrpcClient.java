package com.ismile.argusomnicli.grpc;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;
import io.grpc.CallCredentials;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.stub.ClientCalls;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Dynamic gRPC client that doesn't require code generation.
 * Uses proto descriptors at runtime.
 * Follows Single Responsibility - only handles gRPC communication.
 */
@Component
public class DynamicGrpcClient {

    private final ProtoDescriptorLoader descriptorLoader;

    public DynamicGrpcClient(ProtoDescriptorLoader descriptorLoader) {
        this.descriptorLoader = descriptorLoader;
    }

    /**
     * Execute a gRPC call dynamically.
     *
     * @param protoPath Path to .proto file or descriptor set
     * @param host gRPC server host:port
     * @param serviceName Fully qualified service name
     * @param methodName Method name
     * @param requestData Request data as Map
     * @param metadata gRPC metadata (headers)
     * @return Response as JSON string
     */
    public String execute(String protoPath, String host, String serviceName,
                         String methodName, Map<String, Object> requestData,
                         Map<String, String> metadata) throws Exception {

        // Load proto descriptor
        Descriptors.FileDescriptor fileDescriptor = descriptorLoader.load(protoPath);

        // Find service - try with and without package prefix
        Descriptors.ServiceDescriptor serviceDescriptor = findService(fileDescriptor, serviceName);
        if (serviceDescriptor == null) {
            throw new IllegalArgumentException("Service not found: " + serviceName +
                " (available: " + fileDescriptor.getServices() + ")");
        }

        Descriptors.MethodDescriptor methodDescriptor = serviceDescriptor.findMethodByName(methodName);
        if (methodDescriptor == null) {
            throw new IllegalArgumentException("Method not found: " + methodName);
        }

        // Build request message
        DynamicMessage request = buildRequestMessage(methodDescriptor.getInputType(), requestData);

        // Create channel
        Channel channel = ManagedChannelBuilder.forTarget(host)
                .usePlaintext()
                .build();

        // Create method descriptor for gRPC
        String fullServiceName = serviceDescriptor.getFullName();
        MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethod = createMethodDescriptor(
                fullServiceName, methodName,
                methodDescriptor.getInputType(),
                methodDescriptor.getOutputType()
        );

        // Attach metadata (headers) to call
        CallOptions callOptions = CallOptions.DEFAULT;
        if (metadata != null && !metadata.isEmpty()) {
            io.grpc.Metadata grpcMetadata = new io.grpc.Metadata();
            metadata.forEach((key, value) -> {
                io.grpc.Metadata.Key<String> metadataKey =
                    io.grpc.Metadata.Key.of(key, io.grpc.Metadata.ASCII_STRING_MARSHALLER);
                grpcMetadata.put(metadataKey, value);
            });

            // Create CallCredentials to attach metadata
            io.grpc.CallCredentials credentials = new io.grpc.CallCredentials() {
                @Override
                public void applyRequestMetadata(RequestInfo requestInfo, Executor appExecutor,
                                                MetadataApplier applier) {
                    applier.apply(grpcMetadata);
                }

                @Override
                public void thisUsesUnstableApi() {
                    // Required by interface
                }
            };

            callOptions = callOptions.withCallCredentials(credentials);
        }

        // Execute call
        try {
            DynamicMessage response = ClientCalls.blockingUnaryCall(channel, grpcMethod, callOptions, request);
            return JsonFormat.printer().print(response);
        } catch (Exception e) {
            throw new RuntimeException("gRPC call failed: " + e.getMessage() +
                " (Service: " + fullServiceName + ", Method: " + methodName + ")", e);
        }
    }

    private DynamicMessage buildRequestMessage(Descriptors.Descriptor messageType,
                                              Map<String, Object> data) throws Exception {
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
            DynamicMessage.Builder builder = DynamicMessage.newBuilder(messageType);
            JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build request message: " + e.getMessage() +
                " (Message type: " + messageType.getFullName() + ")", e);
        }
    }

    private MethodDescriptor<DynamicMessage, DynamicMessage> createMethodDescriptor(
            String serviceName, String methodName,
            Descriptors.Descriptor inputType, Descriptors.Descriptor outputType) {

        return MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName(serviceName, methodName))
                .setRequestMarshaller(new DynamicMessageMarshaller(inputType))
                .setResponseMarshaller(new DynamicMessageMarshaller(outputType))
                .build();
    }

    private Descriptors.ServiceDescriptor findService(Descriptors.FileDescriptor fileDescriptor, String serviceName) {
        // Try to find service by name directly
        Descriptors.ServiceDescriptor service = fileDescriptor.findServiceByName(serviceName);
        if (service != null) {
            return service;
        }

        // If not found, try to match by full name or simple name
        for (Descriptors.ServiceDescriptor s : fileDescriptor.getServices()) {
            if (s.getFullName().equals(serviceName) || s.getName().equals(serviceName)) {
                return s;
            }
        }

        return null;
    }
}
