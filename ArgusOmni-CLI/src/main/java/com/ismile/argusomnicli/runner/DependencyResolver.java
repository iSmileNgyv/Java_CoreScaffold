package com.ismile.argusomnicli.runner;

import com.ismile.argusomnicli.model.TestStep;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Resolves test dependencies and creates execution plan.
 * Performs topological sort to determine execution levels.
 */
@Component
public class DependencyResolver {

    /**
     * Resolve dependencies and create execution plan.
     * Returns tests grouped by dependency levels.
     * Tests in the same level can execute in parallel.
     */
    public ExecutionPlan resolve(List<TestStep> tests) throws Exception {
        // Validate and prepare tests
        validateTests(tests);
        assignDefaultIds(tests);

        // Build dependency graph
        Map<String, TestNode> graph = buildGraph(tests);

        // Detect cycles
        detectCycles(graph);

        // Calculate levels using topological sort
        List<List<TestStep>> levels = calculateLevels(graph);

        return new ExecutionPlan(levels);
    }

    /**
     * Validate test configuration.
     */
    private void validateTests(List<TestStep> tests) throws Exception {
        if (tests == null || tests.isEmpty()) {
            throw new Exception("Test list cannot be empty");
        }

        // Check for duplicate IDs
        Set<String> ids = new HashSet<>();
        for (TestStep test : tests) {
            if (test.getId() != null) {
                if (!ids.add(test.getId())) {
                    throw new Exception("Duplicate test ID: " + test.getId());
                }
            }
        }
    }

    /**
     * Assign default IDs to tests without explicit IDs.
     */
    private void assignDefaultIds(List<TestStep> tests) {
        int counter = 1;
        for (TestStep test : tests) {
            if (test.getId() == null || test.getId().trim().isEmpty()) {
                test.setId("test_" + counter);
                counter++;
            }
        }
    }

    /**
     * Build dependency graph.
     */
    private Map<String, TestNode> buildGraph(List<TestStep> tests) throws Exception {
        Map<String, TestNode> graph = new HashMap<>();

        // Create nodes
        for (TestStep test : tests) {
            graph.put(test.getId(), new TestNode(test));
        }

        // Add edges (dependencies)
        for (TestStep test : tests) {
            if (test.getDependsOn() != null && !test.getDependsOn().isEmpty()) {
                TestNode node = graph.get(test.getId());

                for (String depId : test.getDependsOn()) {
                    TestNode depNode = graph.get(depId);
                    if (depNode == null) {
                        throw new Exception(String.format(
                            "Test '%s' depends on non-existent test '%s'",
                            test.getId(), depId
                        ));
                    }
                    node.dependencies.add(depNode);
                    depNode.dependents.add(node);
                }
            }
        }

        return graph;
    }

    /**
     * Detect circular dependencies using DFS.
     */
    private void detectCycles(Map<String, TestNode> graph) throws Exception {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (TestNode node : graph.values()) {
            if (!visited.contains(node.test.getId())) {
                if (hasCycle(node, visited, recursionStack, new ArrayList<>())) {
                    throw new Exception("Circular dependency detected in tests");
                }
            }
        }
    }

    /**
     * DFS cycle detection helper.
     */
    private boolean hasCycle(TestNode node, Set<String> visited,
                            Set<String> recursionStack, List<String> path) {
        String id = node.test.getId();
        visited.add(id);
        recursionStack.add(id);
        path.add(id);

        for (TestNode dep : node.dependencies) {
            String depId = dep.test.getId();

            if (!visited.contains(depId)) {
                if (hasCycle(dep, visited, recursionStack, path)) {
                    return true;
                }
            } else if (recursionStack.contains(depId)) {
                // Cycle detected
                path.add(depId);
                System.err.println("Circular dependency: " + String.join(" -> ", path));
                return true;
            }
        }

        recursionStack.remove(id);
        path.remove(path.size() - 1);
        return false;
    }

    /**
     * Calculate execution levels using topological sort.
     * Level 0: Tests with no dependencies
     * Level N: Tests that depend only on tests in levels 0..N-1
     */
    private List<List<TestStep>> calculateLevels(Map<String, TestNode> graph) {
        List<List<TestStep>> levels = new ArrayList<>();
        Set<String> completed = new HashSet<>();

        while (completed.size() < graph.size()) {
            List<TestStep> currentLevel = new ArrayList<>();

            // Find tests whose dependencies are all completed
            for (TestNode node : graph.values()) {
                if (!completed.contains(node.test.getId())) {
                    boolean allDependenciesCompleted = node.dependencies.stream()
                        .allMatch(dep -> completed.contains(dep.test.getId()));

                    if (allDependenciesCompleted) {
                        currentLevel.add(node.test);
                    }
                }
            }

            if (currentLevel.isEmpty()) {
                // This should not happen if cycle detection worked
                break;
            }

            // Mark current level as completed
            for (TestStep test : currentLevel) {
                completed.add(test.getId());
            }

            levels.add(currentLevel);
        }

        return levels;
    }

    /**
     * Internal node for dependency graph.
     */
    @Data
    private static class TestNode {
        private final TestStep test;
        private final List<TestNode> dependencies = new ArrayList<>();
        private final List<TestNode> dependents = new ArrayList<>();
    }

    /**
     * Execution plan with tests grouped by dependency levels.
     */
    @Data
    public static class ExecutionPlan {
        private final List<List<TestStep>> levels;

        public int getTotalLevels() {
            return levels.size();
        }

        public int getTotalTests() {
            return levels.stream().mapToInt(List::size).sum();
        }

        public List<TestStep> getLevel(int level) {
            if (level >= 0 && level < levels.size()) {
                return levels.get(level);
            }
            return Collections.emptyList();
        }

        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Execution Plan: %d levels, %d tests\n", getTotalLevels(), getTotalTests()));

            for (int i = 0; i < levels.size(); i++) {
                List<TestStep> level = levels.get(i);
                sb.append(String.format("  Level %d (%d tests): %s\n",
                    i,
                    level.size(),
                    level.stream().map(TestStep::getId).collect(Collectors.joining(", "))
                ));
            }

            return sb.toString();
        }
    }
}
