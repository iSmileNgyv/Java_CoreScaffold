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
            return resolve((String) obj, context);
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

        // Simple variable lookup
        Object value = context.get(expression.trim());
        return value != null ? value.toString() : "";
    }
}
