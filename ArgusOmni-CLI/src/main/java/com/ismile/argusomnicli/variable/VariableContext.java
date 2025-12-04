package com.ismile.argusomnicli.variable;

import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates variable storage and retrieval.
 * Follows Encapsulation - controlled access to variables.
 */
public class VariableContext {
    private final Map<String, Object> variables = new HashMap<>();

    public void set(String key, Object value) {
        variables.put(key, value);
    }

    public Object get(String key) {
        return variables.get(key);
    }

    public String getString(String key) {
        Object value = get(key);
        return value != null ? value.toString() : null;
    }

    public boolean has(String key) {
        return variables.containsKey(key);
    }

    public void setAll(Map<String, Object> vars) {
        variables.putAll(vars);
    }

    public Map<String, Object> getAll() {
        return new HashMap<>(variables);
    }
}
