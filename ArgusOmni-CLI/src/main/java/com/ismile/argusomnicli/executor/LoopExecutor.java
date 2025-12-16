package com.ismile.argusomnicli.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.argusomnicli.assertion.Asserter;
import com.ismile.argusomnicli.extractor.ResponseExtractor;
import com.ismile.argusomnicli.model.LoopConfig;
import com.ismile.argusomnicli.model.StepType;
import com.ismile.argusomnicli.model.TestStep;
import com.ismile.argusomnicli.runner.ExecutionContext;
import com.ismile.argusomnicli.variable.VariableResolver;
import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loop/iteration executor for data-driven testing.
 * Executes nested steps for each item in a collection.
 */
@Component
@Slf4j
public class LoopExecutor extends AbstractExecutor {

    private final List<TestExecutor> executors;
    private final Asserter asserter;
    private final ObjectMapper objectMapper;

    public LoopExecutor(VariableResolver variableResolver,
                       ResponseExtractor responseExtractor,
                       List<TestExecutor> executors,
                       Asserter asserter,
                       ObjectMapper objectMapper) {
        super(variableResolver, responseExtractor);
        this.executors = executors;
        this.asserter = asserter;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(TestStep step) {
        return step.getType() == StepType.LOOP && step.getLoop() != null;
    }

    @Override
    protected Object doExecute(TestStep step, ExecutionContext context) throws Exception {
        LoopConfig config = step.getLoop();

        // Get items to iterate over
        List<Object> items = getItems(config, context);

        if (items == null || items.isEmpty()) {
            return "Loop completed: 0 items";
        }

        // Get configuration
        String itemVariable = config.getVariable() != null ? config.getVariable() : "item";
        String indexVariable = config.getIndexVariable() != null ? config.getIndexVariable() : "index";
        boolean continueOnError = config.getContinueOnError() != null && config.getContinueOnError();
        int maxIterations = config.getMaxIterations() != null ? config.getMaxIterations() : 1000;

        // Safety check
        if (items.size() > maxIterations) {
            throw new Exception("Loop items exceed max iterations limit: " + items.size() + " > " + maxIterations);
        }

        // Validate nested steps exist
        if (config.getSteps() == null || config.getSteps().isEmpty()) {
            throw new Exception("Loop must have at least one step in 'steps' field");
        }

        int successCount = 0;
        int failureCount = 0;
        List<String> errors = new ArrayList<>();

        // Execute loop
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);

            if (context.isVerbose()) {
                System.out.println(String.format("  🔁 Loop iteration %d/%d (item: %s)",
                        i + 1, items.size(), item));
            }

            // Each iteration gets its own scope so iteration variables don't leak
            context.getVariableContext().pushScope();
            boolean iterationSuccess = true;

            try {
                // Set loop variables
                context.setVariable(itemVariable, item);
                context.setVariable(indexVariable, i);

                // Execute nested steps
                for (TestStep nestedStep : config.getSteps()) {
                    try {
                        ExecutionResult result = executeNestedStep(nestedStep, context);

                        if (!result.isSuccess()) {
                            iterationSuccess = false;
                            String error = String.format("Iteration %d failed at step '%s': %s",
                                    i, nestedStep.getName(), result.getErrorMessage());
                            errors.add(error);

                            if (!continueOnError) {
                                throw new Exception(error);
                            }
                        }
                    } catch (Exception e) {
                        iterationSuccess = false;
                        String error = String.format("Iteration %d failed at step '%s': %s",
                                i, nestedStep.getName(), e.getMessage());
                        errors.add(error);

                        if (!continueOnError) {
                            throw new Exception(error);
                        }
                    }
                }
            } finally {
                Map<String, Object> scopedVars = context.getVariableContext().popScope();
                // Remove loop-specific helper variables so they don't escape
                scopedVars.remove(itemVariable);
                scopedVars.remove(indexVariable);
                scopedVars.forEach(context::setVariable);
            }

            if (iterationSuccess) {
                successCount++;
            } else {
                failureCount++;
            }
        }

        // Build result summary
        String summary = String.format("Loop completed: %d/%d iterations successful",
                successCount, items.size());

        if (failureCount > 0) {
            summary += String.format(" (%d failed)", failureCount);
            if (!continueOnError) {
                throw new Exception(summary + ". Errors: " + errors);
            }
        }

        if (context.isVerbose()) {
            System.out.println("  ✓ " + summary);
        }

        return Map.of(
                "totalIterations", items.size(),
                "successCount", successCount,
                "failureCount", failureCount,
                "summary", summary,
                "errors", errors
        );
    }

    /**
     * Get items to iterate over from various sources.
     */
    private List<Object> getItems(LoopConfig config, ExecutionContext context) throws Exception {
        // Source 1: Inline items
        if (config.getItems() != null && !config.getItems().isEmpty()) {
            return resolveItems(config.getItems(), context);
        }

        // Source 2: Variable reference
        if (config.getItemsFrom() != null) {
            Object itemsObj = context.getVariable(config.getItemsFrom());
            if (itemsObj == null) {
                throw new Exception("Variable '" + config.getItemsFrom() + "' not found in context");
            }
            if (itemsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) itemsObj;
                return list;
            } else {
                throw new Exception("Variable '" + config.getItemsFrom() + "' is not a list");
            }
        }

        // Source 3: Range
        if (config.getRange() != null) {
            return generateRange(config.getRange());
        }

        // Source 4: Data source (CSV, JSON) - will implement later
        if (config.getDataSource() != null) {
            return loadDataSource(config.getDataSource(), context);
        }

        throw new Exception("Loop must specify items, itemsFrom, range, or dataSource");
    }

    /**
     * Resolve items (handle variable interpolation).
     */
    private List<Object> resolveItems(List<Object> items, ExecutionContext context) {
        List<Object> resolved = new ArrayList<>();
        for (Object item : items) {
            Object resolvedItem = variableResolver.resolveObject(item, context.getVariableContext());
            resolved.add(resolvedItem);
        }
        return resolved;
    }

    /**
     * Generate range of numbers.
     */
    private List<Object> generateRange(LoopConfig.RangeConfig range) {
        int start = range.getStart() != null ? range.getStart() : 0;
        int end = range.getEnd() != null ? range.getEnd() : 10;
        int step = range.getStep() != null ? range.getStep() : 1;

        List<Object> items = new ArrayList<>();
        for (int i = start; i <= end; i += step) {
            items.add(i);
        }
        return items;
    }

    /**
     * Load data from external source (CSV, JSON).
     * TODO: Implement CSV and JSON loaders
     */
    private List<Object> loadDataSource(LoopConfig.DataSource dataSource, ExecutionContext context) throws Exception {
        String type = dataSource.getType();
        String filePath = variableResolver.resolve(dataSource.getFile(), context.getVariableContext());

        if ("JSON".equalsIgnoreCase(type)) {
            return loadJsonData(filePath, dataSource.getPath());
        } else if ("CSV".equalsIgnoreCase(type)) {
            return loadCsvData(filePath, dataSource.getHeaders());
        } else {
            throw new Exception("Unsupported data source type: " + type);
        }
    }

    /**
     * Load JSON data from file.
     */
    private List<Object> loadJsonData(String filePath, String jsonPath) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new Exception("JSON file not found: " + filePath);
        }

        String content = Files.readString(file.toPath());

        if (jsonPath != null && !jsonPath.isEmpty()) {
            // Extract array using JSONPath
            Object result = JsonPath.read(content, jsonPath);
            if (result instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) result;
                return list;
            } else {
                throw new Exception("JSONPath did not return an array: " + jsonPath);
            }
        } else {
            // Parse entire file as array
            Object parsed = objectMapper.readValue(content, List.class);
            if (parsed instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) parsed;
                return list;
            } else {
                throw new Exception("JSON file does not contain an array");
            }
        }
    }

    /**
     * Load CSV data from file.
     * Returns list of maps (each row as map).
     */
    private List<Object> loadCsvData(String filePath, Boolean useHeaders) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new Exception("CSV file not found: " + filePath);
        }

        List<String> lines = Files.readAllLines(file.toPath());
        if (lines.isEmpty()) {
            return new ArrayList<>();
        }

        boolean hasHeaders = useHeaders == null || useHeaders;
        List<Object> items = new ArrayList<>();

        if (hasHeaders) {
            // First row is headers
            String[] headers = lines.get(0).split(",");

            // Parse remaining rows
            for (int i = 1; i < lines.size(); i++) {
                String[] values = lines.get(i).split(",");
                Map<String, String> row = new HashMap<>();

                for (int j = 0; j < Math.min(headers.length, values.length); j++) {
                    row.put(headers[j].trim(), values[j].trim());
                }

                items.add(row);
            }
        } else {
            // No headers, return rows as arrays
            for (int i = 0; i < lines.size(); i++) {
                String[] values = lines.get(i).split(",");
                List<String> row = new ArrayList<>();
                for (String value : values) {
                    row.add(value.trim());
                }
                items.add(row);
            }
        }

        return items;
    }

    /**
     * Execute nested step within loop.
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
