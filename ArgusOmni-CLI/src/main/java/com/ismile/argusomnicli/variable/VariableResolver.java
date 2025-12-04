package com.ismile.argusomnicli.variable;

/**
 * Interface Segregation Principle (ISP):
 * Focused interface for variable resolution.
 *
 * Single Responsibility Principle (SRP):
 * Only responsible for resolving template variables.
 */
public interface VariableResolver {
    /**
     * Resolves variables in a template string.
     *
     * @param template Template string with {{variable}} syntax
     * @param context Variable context
     * @return Resolved string
     */
    String resolve(String template, VariableContext context);

    /**
     * Resolves an object (handles nested maps, lists, primitives).
     *
     * @param obj Object to resolve
     * @param context Variable context
     * @return Resolved object
     */
    Object resolveObject(Object obj, VariableContext context);
}
