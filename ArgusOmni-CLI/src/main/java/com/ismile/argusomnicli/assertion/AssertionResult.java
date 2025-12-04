package com.ismile.argusomnicli.assertion;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates assertion results.
 * Follows Encapsulation.
 */
@Data
@AllArgsConstructor
public class AssertionResult {
    private boolean passed;
    private List<String> failures;

    public static AssertionResult success() {
        return new AssertionResult(true, new ArrayList<>());
    }

    public static AssertionResult failure(String message) {
        List<String> failures = new ArrayList<>();
        failures.add(message);
        return new AssertionResult(false, failures);
    }

    public void addFailure(String message) {
        passed = false;
        failures.add(message);
    }
}
