package com.ismile.argusomnicli.runner;

import com.ismile.argusomnicli.variable.VariableContext;
import lombok.Getter;

/**
 * Encapsulates execution state and context.
 * Follows Encapsulation - controlled access to execution state.
 */
@Getter
public class ExecutionContext {
    private final VariableContext variableContext;
    private final boolean verbose;

    public ExecutionContext(VariableContext variableContext, boolean verbose) {
        this.variableContext = variableContext;
        this.verbose = verbose;
    }

    public void setVariable(String key, Object value) {
        variableContext.set(key, value);
    }

    public Object getVariable(String key) {
        return variableContext.get(key);
    }

    public void pushScope() {
        variableContext.pushScope();
    }

    public void popScope() {
        variableContext.popScope();
    }

    /**
     * Create a child execution context with a copy of the current variables.
     * Used by parallel execution to isolate modifications before merging.
     */
    public ExecutionContext createChildContext() {
        return new ExecutionContext(variableContext.copy(), verbose);
    }

    /**
     * Merge variables from a child context back into the root context.
     */
    public void mergeVariablesFrom(ExecutionContext childContext) {
        if (childContext == null) {
            return;
        }
        this.variableContext.mergeFrom(childContext.getVariableContext());
    }
}
