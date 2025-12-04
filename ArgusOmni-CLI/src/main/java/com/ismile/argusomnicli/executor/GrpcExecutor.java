package com.ismile.argusomnicli.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.argusomnicli.extractor.ResponseExtractor;
import com.ismile.argusomnicli.grpc.DynamicGrpcClient;
import com.ismile.argusomnicli.model.GrpcConfig;
import com.ismile.argusomnicli.model.StepType;
import com.ismile.argusomnicli.model.TestStep;
import com.ismile.argusomnicli.runner.ExecutionContext;
import com.ismile.argusomnicli.variable.VariableResolver;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * gRPC executor implementation.
 * Uses dynamic proto loading - no code generation required.
 * Follows Open/Closed Principle - extends AbstractExecutor without modifying it.
 * Follows Single Responsibility - only executes gRPC requests.
 */
@Component
public class GrpcExecutor extends AbstractExecutor {

    private final DynamicGrpcClient grpcClient;
    private final ObjectMapper objectMapper;

    public GrpcExecutor(VariableResolver variableResolver,
                       ResponseExtractor responseExtractor,
                       DynamicGrpcClient grpcClient,
                       ObjectMapper objectMapper) {
        super(variableResolver, responseExtractor);
        this.grpcClient = grpcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(TestStep step) {
        return step.getType() == StepType.GRPC && step.getGrpc() != null;
    }

    @Override
    protected Object doExecute(TestStep step, ExecutionContext context) throws Exception {
        GrpcConfig config = step.getGrpc();

        // Resolve variables
        String proto = variableResolver.resolve(config.getProto(), context.getVariableContext());
        String host = variableResolver.resolve(config.getHost(), context.getVariableContext());
        String service = variableResolver.resolve(config.getService(), context.getVariableContext());
        String method = variableResolver.resolve(config.getMethod(), context.getVariableContext());

        // Resolve request data
        @SuppressWarnings("unchecked")
        Map<String, Object> requestData = config.getRequest() != null
                ? (Map<String, Object>) variableResolver.resolveObject(config.getRequest(), context.getVariableContext())
                : new HashMap<>();

        // Execute gRPC call
        String jsonResponse = grpcClient.execute(proto, host, service, method, requestData);

        // Parse JSON response
        return objectMapper.readValue(jsonResponse, Object.class);
    }
}
