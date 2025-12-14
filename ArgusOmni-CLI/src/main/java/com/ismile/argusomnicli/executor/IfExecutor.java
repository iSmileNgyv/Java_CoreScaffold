package com.ismile.argusomnicli.executor;

import com.ismile.argusomnicli.assertion.Asserter;
import com.ismile.argusomnicli.extractor.ResponseExtractor;
import com.ismile.argusomnicli.model.IfConfig;
import com.ismile.argusomnicli.model.StepType;
import com.ismile.argusomnicli.model.TestStep;
import com.ismile.argusomnicli.runner.ExecutionContext;
import com.ismile.argusomnicli.variable.VariableResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Conditional execution executor (IF/ELSE).
 * Evaluates conditions and executes appropriate branch.
 */
@Component
public class IfExecutor extends AbstractExecutor {

    private final List<TestExecutor> executors;
    private final Asserter asserter;

    public IfExecutor(VariableResolver variableResolver,
                     ResponseExtractor responseExtractor,
                     List<TestExecutor> executors,
                     Asserter asserter) {
        super(variableResolver, responseExtractor);
        this.executors = executors;
        this.asserter = asserter;
    }

    @Override
    public boolean supports(TestStep step) {
        return step.getType() == StepType.IF && step.getIfConfig() != null;
    }

    @Override
    protected Object doExecute(TestStep step, ExecutionContext context) throws Exception {
        IfConfig config = step.getIfConfig();

        if (config.getCondition() == null || config.getCondition().trim().isEmpty()) {
            throw new Exception("IF step must have a condition");
        }

        // Evaluate main condition
        boolean conditionResult = evaluateCondition(config.getCondition(), context);

        if (context.isVerbose()) {
            System.out.println(String.format("  🔀 IF condition '%s' evaluated to: %s",
                    config.getCondition(), conditionResult));
        }

        List<TestStep> stepsToExecute = null;
        String branchTaken = null;

        if (conditionResult) {
            // Main IF branch
            stepsToExecute = config.getThen();
            branchTaken = "then";
        } else {
            // Check ELSE IF branches
            if (config.getElseIf() != null && !config.getElseIf().isEmpty()) {
                for (int i = 0; i < config.getElseIf().size(); i++) {
                    IfConfig.ElseIfBranch elseIfBranch = config.getElseIf().get(i);
                    boolean elseIfResult = evaluateCondition(elseIfBranch.getCondition(), context);

                    if (context.isVerbose()) {
                        System.out.println(String.format("  🔀 ELSE IF condition '%s' evaluated to: %s",
                                elseIfBranch.getCondition(), elseIfResult));
                    }

                    if (elseIfResult) {
                        stepsToExecute = elseIfBranch.getThen();
                        branchTaken = "elseIf[" + i + "]";
                        break;
                    }
                }
            }

            // ELSE branch
            if (stepsToExecute == null && config.getElseSteps() != null) {
                stepsToExecute = config.getElseSteps();
                branchTaken = "else";
            }
        }

        // Execute selected branch
        if (stepsToExecute == null || stepsToExecute.isEmpty()) {
            if (context.isVerbose()) {
                System.out.println("  ✓ No branch matched, skipping");
            }
            return Map.of(
                    "conditionResult", conditionResult,
                    "branchTaken", "none",
                    "stepsExecuted", 0
            );
        }

        if (context.isVerbose()) {
            System.out.println(String.format("  ▶ Executing '%s' branch (%d steps)",
                    branchTaken, stepsToExecute.size()));
        }

        // Execute steps in selected branch
        int executedCount = 0;
        List<String> errors = new ArrayList<>();

        for (TestStep nestedStep : stepsToExecute) {
            try {
                ExecutionResult result = executeNestedStep(nestedStep, context);

                if (!result.isSuccess()) {
                    String error = String.format("Step '%s' in '%s' branch failed: %s",
                            nestedStep.getName(), branchTaken, result.getErrorMessage());
                    errors.add(error);
                    throw new Exception(error);
                }

                executedCount++;
            } catch (Exception e) {
                String error = String.format("Step '%s' in '%s' branch failed: %s",
                        nestedStep.getName(), branchTaken, e.getMessage());
                errors.add(error);
                throw new Exception(error);
            }
        }

        if (context.isVerbose()) {
            System.out.println(String.format("  ✓ Executed %d steps in '%s' branch",
                    executedCount, branchTaken));
        }

        return Map.of(
                "conditionResult", conditionResult,
                "branchTaken", branchTaken,
                "stepsExecuted", executedCount,
                "errors", errors
        );
    }

    /**
     * Evaluate condition expression.
     * Supports complex expressions with AND/OR/NOT operators.
     */
    private boolean evaluateCondition(String condition, ExecutionContext context) throws Exception {
        if (condition == null || condition.trim().isEmpty()) {
            throw new Exception("Condition cannot be empty");
        }

        // Resolve variables in condition
        String resolved = variableResolver.resolve(condition, context.getVariableContext());

        // Handle logical operators (AND, OR)
        if (resolved.contains(" AND ")) {
            String[] parts = resolved.split(" AND ");
            for (String part : parts) {
                if (!evaluateSimpleCondition(part.trim(), context)) {
                    return false;
                }
            }
            return true;
        }

        if (resolved.contains(" OR ")) {
            String[] parts = resolved.split(" OR ");
            for (String part : parts) {
                if (evaluateSimpleCondition(part.trim(), context)) {
                    return true;
                }
            }
            return false;
        }

        // Handle NOT operator
        if (resolved.trim().startsWith("NOT ")) {
            String innerCondition = resolved.trim().substring(4).trim();
            return !evaluateSimpleCondition(innerCondition, context);
        }

        // Simple condition
        return evaluateSimpleCondition(resolved, context);
    }

    /**
     * Evaluate simple condition (no logical operators).
     */
    private boolean evaluateSimpleCondition(String condition, ExecutionContext context) throws Exception {
        condition = condition.trim();

        // Existence checks
        if (condition.endsWith(" exists")) {
            String varName = condition.substring(0, condition.length() - " exists".length()).trim();
            Object value = context.getVariable(varName);
            return value != null;
        }

        // Null checks
        if (condition.endsWith(" == null")) {
            String left = condition.substring(0, condition.length() - " == null".length()).trim();
            Object value = resolveValue(left, context);
            return value == null;
        }

        if (condition.endsWith(" != null")) {
            String left = condition.substring(0, condition.length() - " != null".length()).trim();
            Object value = resolveValue(left, context);
            return value != null;
        }

        // String operations
        if (condition.contains(" contains ")) {
            return evaluateStringOperation(condition, "contains", context);
        }
        if (condition.contains(" startsWith ")) {
            return evaluateStringOperation(condition, "startsWith", context);
        }
        if (condition.contains(" endsWith ")) {
            return evaluateStringOperation(condition, "endsWith", context);
        }

        // Comparison operators
        if (condition.contains(" == ")) {
            return evaluateComparison(condition, "==", context);
        }
        if (condition.contains(" != ")) {
            return evaluateComparison(condition, "!=", context);
        }
        if (condition.contains(" >= ")) {
            return evaluateComparison(condition, ">=", context);
        }
        if (condition.contains(" <= ")) {
            return evaluateComparison(condition, "<=", context);
        }
        if (condition.contains(" > ")) {
            return evaluateComparison(condition, ">", context);
        }
        if (condition.contains(" < ")) {
            return evaluateComparison(condition, "<", context);
        }

        // Boolean literal
        if ("true".equalsIgnoreCase(condition)) {
            return true;
        }
        if ("false".equalsIgnoreCase(condition)) {
            return false;
        }

        // Single variable (check if truthy)
        Object value = resolveValue(condition, context);
        return isTruthy(value);
    }

    /**
     * Evaluate comparison operation.
     */
    private boolean evaluateComparison(String condition, String operator, ExecutionContext context) throws Exception {
        String[] parts = condition.split(" " + operator.replace(">", "\\>").replace("<", "\\<") + " ", 2);
        if (parts.length != 2) {
            throw new Exception("Invalid comparison: " + condition);
        }

        Object left = resolveValue(parts[0].trim(), context);
        Object right = resolveValue(parts[1].trim(), context);

        switch (operator) {
            case "==":
                return isEqual(left, right);
            case "!=":
                return !isEqual(left, right);
            case ">":
                return compareNumeric(left, right) > 0;
            case "<":
                return compareNumeric(left, right) < 0;
            case ">=":
                return compareNumeric(left, right) >= 0;
            case "<=":
                return compareNumeric(left, right) <= 0;
            default:
                throw new Exception("Unknown operator: " + operator);
        }
    }

    /**
     * Evaluate string operation.
     */
    private boolean evaluateStringOperation(String condition, String operation, ExecutionContext context) throws Exception {
        String[] parts = condition.split(" " + operation + " ", 2);
        if (parts.length != 2) {
            throw new Exception("Invalid string operation: " + condition);
        }

        Object leftObj = resolveValue(parts[0].trim(), context);
        String left = leftObj != null ? leftObj.toString() : "";

        // Remove quotes from right side if present
        String right = parts[1].trim();
        if ((right.startsWith("'") && right.endsWith("'")) || (right.startsWith("\"") && right.endsWith("\""))) {
            right = right.substring(1, right.length() - 1);
        }

        switch (operation) {
            case "contains":
                return left.contains(right);
            case "startsWith":
                return left.startsWith(right);
            case "endsWith":
                return left.endsWith(right);
            default:
                throw new Exception("Unknown string operation: " + operation);
        }
    }

    /**
     * Resolve value from string (variable or literal).
     */
    private Object resolveValue(String value, ExecutionContext context) {
        if (value == null) {
            return null;
        }

        value = value.trim();

        // Remove quotes if string literal
        if ((value.startsWith("'") && value.endsWith("'")) || (value.startsWith("\"") && value.endsWith("\""))) {
            return value.substring(1, value.length() - 1);
        }

        // Try to parse as number
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            } else {
                return Integer.parseInt(value);
            }
        } catch (NumberFormatException e) {
            // Not a number
        }

        // Boolean literals
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }

        // Variable reference
        Object varValue = context.getVariable(value);
        if (varValue != null) {
            return varValue;
        }

        // Return as string literal
        return value;
    }

    /**
     * Check equality of two objects.
     */
    private boolean isEqual(Object left, Object right) {
        if (left == null && right == null) return true;
        if (left == null || right == null) return false;

        // Try numeric comparison
        if (isNumeric(left) && isNumeric(right)) {
            try {
                return compareNumeric(left, right) == 0;
            } catch (Exception e) {
                // Fallback to string comparison
                return left.toString().equals(right.toString());
            }
        }

        // String comparison
        return left.toString().equals(right.toString());
    }

    /**
     * Compare two numeric values.
     */
    private int compareNumeric(Object left, Object right) throws Exception {
        if (!isNumeric(left) || !isNumeric(right)) {
            throw new Exception("Cannot compare non-numeric values: " + left + " and " + right);
        }

        double leftNum = toDouble(left);
        double rightNum = toDouble(right);

        return Double.compare(leftNum, rightNum);
    }

    /**
     * Check if value is numeric.
     */
    private boolean isNumeric(Object value) {
        return value instanceof Number;
    }

    /**
     * Convert to double.
     */
    private double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        throw new IllegalArgumentException("Not a number: " + value);
    }

    /**
     * Check if value is truthy.
     */
    private boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0;
        if (value instanceof String) return !((String) value).isEmpty() && !"false".equalsIgnoreCase((String) value);
        return true;
    }

    /**
     * Execute nested step within IF branch.
     */
    private ExecutionResult executeNestedStep(TestStep step, ExecutionContext context) throws Exception {
        // Find executor
        TestExecutor executor = executors.stream()
                .filter(ex -> ex.supports(step))
                .findFirst()
                .orElse(null);

        if (executor == null) {
            throw new Exception("No executor found for step type: " + step.getType());
        }

        // Execute step
        ExecutionResult result = executor.execute(step, context);

        // Run assertions if defined
        if (step.getExpect() != null) {
            var assertionResult = asserter.assertExpectations(result, step.getExpect(), context);
            if (!assertionResult.isPassed()) {
                return ExecutionResult.builder()
                        .success(false)
                        .stepName(step.getName())
                        .errorMessage("Assertions failed: " + assertionResult.getFailures())
                        .build();
            }
        }

        return result;
    }
}
