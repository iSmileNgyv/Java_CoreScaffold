package com.ismile.argusomnicli.model;

import lombok.Data;

import java.util.List;

/**
 * Configuration for conditional execution (IF/ELSE).
 * Supports complex conditions with logical operators and nested steps.
 */
@Data
public class IfConfig {
    /**
     * Condition expression to evaluate.
     * Supports:
     * - Simple comparisons: "{{var}} == value", "{{num}} > 10"
     * - Logical operators: "{{a}} == 1 AND {{b}} == 2", "{{x}} > 5 OR {{y}} < 3"
     * - Existence checks: "{{var}} exists", "{{var}} == null"
     * - String operations: "{{text}} contains 'substring'", "{{str}} startsWith 'prefix'"
     * - JSONPath: "{{response.data.length()}} > 0"
     */
    private String condition;

    /**
     * Steps to execute if condition evaluates to true.
     */
    private List<TestStep> then;

    /**
     * Steps to execute if condition evaluates to false (optional).
     */
    private List<TestStep> elseSteps;

    /**
     * Additional conditional branches (ELSE IF).
     * Each entry contains a condition and steps to execute.
     */
    private List<ElseIfBranch> elseIf;

    /**
     * Represents an ELSE IF branch.
     */
    @Data
    public static class ElseIfBranch {
        private String condition;
        private List<TestStep> then;
    }
}
