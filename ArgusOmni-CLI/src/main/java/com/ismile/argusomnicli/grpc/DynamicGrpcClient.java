package com.ismile.argusomnicli.grpc;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.stub.ClientCalls;
import org.springframework.stereotype.Component;

import java.util.Map;

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
     * @return Response as JSON string
     */
    public String execute(String protoPath, String host, String serviceName,
                         String methodName, Map<String, Object> requestData) throws Exception {

        // Load proto descriptor
        Descriptors.FileDescriptor fileDescriptor = descriptorLoader.load(protoPath);

        // Find service and method
        Descriptors.ServiceDescriptor serviceDescriptor = fileDescriptor.findServiceByName(serviceName);
        if (serviceDescriptor == null) {
            throw new IllegalArgumentException("Service not found: " + serviceName);
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
        MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethod = createMethodDescriptor(
                serviceName, methodName,
                methodDescriptor.getInputType(),
                methodDescriptor.getOutputType()
        );

        // Execute call
        DynamicMessage response = ClientCalls.blockingUnaryCall(channel, grpcMethod, null, request);

        // Convert response to JSON
        return JsonFormat.printer().print(response);
    }

    private DynamicMessage buildRequestMessage(Descriptors.Descriptor messageType,
                                              Map<String, Object> data) throws Exception {
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
        DynamicMessage.Builder builder = DynamicMessage.newBuilder(messageType);
        JsonFormat.parser().merge(json, builder);
        return builder.build();
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
}
