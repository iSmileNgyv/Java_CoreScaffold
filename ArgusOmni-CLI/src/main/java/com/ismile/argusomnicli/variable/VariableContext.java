package com.ismile.argusomnicli.variable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encapsulates variable storage and retrieval with scope support.
 * Scopes behave like a stack where the root scope is global and subsequent
 * scopes are temporary/local (loop iterations, branch executions, etc).
 */
public class VariableContext {
    private final Deque<Map<String, Object>> scopes = new ArrayDeque<>();

    public VariableContext() {
        scopes.push(new LinkedHashMap<>());
    }

    private VariableContext(Deque<Map<String, Object>> sourceScopes) {
        // Copy constructor used for child contexts
        var iterator = sourceScopes.descendingIterator();
        iterator.forEachRemaining(scope -> scopes.push(new LinkedHashMap<>(scope)));
    }

    public synchronized void pushScope() {
        scopes.push(new LinkedHashMap<>());
    }

    /**
     * Removes the most recent scope. Root scope cannot be removed.
     *
     * @return removed scope contents
     */
    public synchronized Map<String, Object> popScope() {
        if (scopes.size() == 1) {
            throw new IllegalStateException("Cannot pop the root variable scope");
        }
        return scopes.pop();
    }

    public synchronized void set(String key, Object value) {
        scopes.peek().put(key, value);
    }

    public synchronized Object get(String key) {
        for (Map<String, Object> scope : scopes) {
            if (scope.containsKey(key)) {
                return scope.get(key);
            }
        }
        return null;
    }

    public synchronized String getString(String key) {
        Object value = get(key);
        return value != null ? value.toString() : null;
    }

    public synchronized boolean has(String key) {
        for (Map<String, Object> scope : scopes) {
            if (scope.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    public synchronized void setAll(Map<String, Object> vars) {
        if (vars == null || vars.isEmpty()) {
            return;
        }
        scopes.peek().putAll(vars);
    }

    public synchronized Map<String, Object> getAll() {
        Map<String, Object> merged = new HashMap<>();
        // Bottom scopes should be overridden by upper scopes
        scopes.descendingIterator().forEachRemaining(merged::putAll);
        return merged;
    }

    /**
     * Create a deep copy of the current context (all scopes).
     */
    public synchronized VariableContext copy() {
        return new VariableContext(scopes);
    }

    /**
     * Merge another context's visible variables into the current root scope.
     * The caller decides when merging should happen (for example, after a
     * parallel task finishes).
     */
    public synchronized void mergeFrom(VariableContext other) {
        if (other == null) {
            return;
        }
        Map<String, Object> root = scopes.peekLast();
        if (root == null) {
            throw new IllegalStateException("Variable context does not have a root scope");
        }
        root.putAll(other.getAll());
    }
}
