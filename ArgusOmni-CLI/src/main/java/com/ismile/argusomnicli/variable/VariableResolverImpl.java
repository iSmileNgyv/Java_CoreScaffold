package com.ismile.argusomnicli.variable;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Variable resolver implementation.
 * Resolves {{variable}} and {{function:arg}} syntax.
 * Follows Single Responsibility - only resolves variables.
 */
@Component
public class VariableResolverImpl implements VariableResolver {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");
    private final BuiltInFunctions functions;

    public VariableResolverImpl(BuiltInFunctions functions) {
        this.functions = functions;
    }

    @Override
    public String resolve(String template, VariableContext context) {
        if (template == null) {
            return null;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String expression = matcher.group(1);
            String replacement = resolveExpression(expression, context);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    @Override
    public Object resolveObject(Object obj, VariableContext context) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof String) {
            String str = (String) obj;

            // Check if it's a single variable reference: {{variableName}}
            // If so, return the actual object without converting to String
            if (str.matches("^\\{\\{[^}]+\\}\\}$")) {
                String expression = str.substring(2, str.length() - 2).trim();

                // Handle nested property access
                if (expression.contains(".")) {
                    return resolveNestedProperty(expression, context);
                }

                // Return the actual object, not its string representation
                return context.get(expression);
            }

            // Otherwise, resolve as string template
            return resolve(str, context);
        }

        if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) obj;
            Map<String, Object> resolved = new HashMap<>();
            map.forEach((key, value) -> resolved.put(key, resolveObject(value, context)));
            return resolved;
        }

        if (obj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) obj;
            return list.stream()
                    .map(item -> resolveObject(item, context))
                    .toList();
        }

        return obj;
    }

    private String resolveExpression(String expression, VariableContext context) {
        // Check if it's a function call: function:argument
        if (expression.contains(":")) {
            String[] parts = expression.split(":", 2);
            String functionName = parts[0].trim();
            String argument = parts[1].trim();

            // Resolve argument first (might be a variable)
            String resolvedArg = context.has(argument) ? context.getString(argument) : argument;

            return functions.execute(functionName, resolvedArg);
        }

        // Check for nested property access (e.g., loopItem.name)
        expression = expression.trim();
        if (expression.contains(".")) {
            Object value = resolveNestedProperty(expression, context);
            return value != null ? value.toString() : "";
        }

        // Simple variable lookup
        Object value = context.get(expression);
        return value != null ? value.toString() : "";
    }

    /**
     * Resolve nested property access like "loopItem.name" or "user.address.city"
     */
    private Object resolveNestedProperty(String expression, VariableContext context) {
        String[] parts = expression.split("\\.");

        // Get root variable
        Object current = context.get(parts[0]);
        if (current == null) {
            return null;
        }

        // Navigate through nested properties
        for (int i = 1; i < parts.length; i++) {
            String property = parts[i];

            if (current instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) current;
                current = map.get(property);
            } else {
                // If it's not a Map, we can't access properties
                return null;
            }

            if (current == null) {
                return null;
            }
        }

        return current;
    }
}
