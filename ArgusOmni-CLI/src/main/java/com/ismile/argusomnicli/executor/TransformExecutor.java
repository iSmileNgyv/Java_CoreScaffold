package com.ismile.argusomnicli.executor;

import com.ismile.argusomnicli.extractor.ResponseExtractor;
import com.ismile.argusomnicli.model.StepType;
import com.ismile.argusomnicli.model.TestStep;
import com.ismile.argusomnicli.model.TransformConfig;
import com.ismile.argusomnicli.runner.ExecutionContext;
import com.ismile.argusomnicli.variable.VariableResolver;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Transform variable executor implementation.
 * Applies functions to existing variables.
 * Follows Open/Closed Principle - extends AbstractExecutor without modifying it.
 * Follows Single Responsibility - only transforms variables.
 */
@Component
public class TransformExecutor extends AbstractExecutor {

    public TransformExecutor(VariableResolver variableResolver,
                           ResponseExtractor responseExtractor) {
        super(variableResolver, responseExtractor);
    }

    @Override
    public boolean supports(TestStep step) {
        return step.getType() == StepType.TRANSFORM && step.getTransform() != null;
    }

    @Override
    protected Object doExecute(TestStep step, ExecutionContext context) throws Exception {
        TransformConfig config = step.getTransform();

        String inputValue = context.getVariableContext().getString(config.getInput());
        if (inputValue == null) {
            throw new IllegalArgumentException("Input variable not found: " + config.getInput());
        }

        // Apply transformation function
        String functionCall = "{{" + config.getFunction() + ":" + inputValue + "}}";
        String transformedValue = variableResolver.resolve(functionCall, context.getVariableContext());

        context.setVariable(config.getOutput(), transformedValue);

        Map<String, Object> result = new HashMap<>();
        result.put("input", inputValue);
        result.put("function", config.getFunction());
        result.put("output", transformedValue);

        return result;
    }
}
