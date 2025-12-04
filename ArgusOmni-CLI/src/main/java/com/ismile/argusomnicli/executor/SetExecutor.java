package com.ismile.argusomnicli.executor;

import com.ismile.argusomnicli.extractor.ResponseExtractor;
import com.ismile.argusomnicli.model.SetConfig;
import com.ismile.argusomnicli.model.StepType;
import com.ismile.argusomnicli.model.TestStep;
import com.ismile.argusomnicli.runner.ExecutionContext;
import com.ismile.argusomnicli.variable.VariableResolver;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Set variable executor implementation.
 * Follows Open/Closed Principle - extends AbstractExecutor without modifying it.
 * Follows Single Responsibility - only sets variables.
 */
@Component
public class SetExecutor extends AbstractExecutor {

    public SetExecutor(VariableResolver variableResolver,
                      ResponseExtractor responseExtractor) {
        super(variableResolver, responseExtractor);
    }

    @Override
    public boolean supports(TestStep step) {
        return step.getType() == StepType.SET && step.getSet() != null;
    }

    @Override
    protected Object doExecute(TestStep step, ExecutionContext context) throws Exception {
        SetConfig config = step.getSet();
        Map<String, Object> result = new HashMap<>();

        if (config.getVariables() != null) {
            config.getVariables().forEach((key, value) -> {
                Object resolvedValue = variableResolver.resolveObject(value, context.getVariableContext());
                context.setVariable(key, resolvedValue);
                result.put(key, resolvedValue);
            });
        }

        return result;
    }
}
