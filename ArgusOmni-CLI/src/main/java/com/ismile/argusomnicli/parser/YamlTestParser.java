package com.ismile.argusomnicli.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ismile.argusomnicli.model.*;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Map;

/**
 * YAML test parser implementation.
 * Follows Single Responsibility - only parses YAML files.
 * Follows Open/Closed - can be extended with different formats.
 */
@Component
public class YamlTestParser implements TestParser {

    private final ObjectMapper yamlMapper;

    public YamlTestParser() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    @Override
    public TestSuite parse(File testFile) throws Exception {
        if (!testFile.exists()) {
            throw new IllegalArgumentException("Test file not found: " + testFile.getAbsolutePath());
        }

        try {
            // Parse YAML to generic map first
            @SuppressWarnings("unchecked")
            Map<String, Object> rawData = yamlMapper.readValue(testFile, Map.class);

            // Convert to TestSuite model
            TestSuite suite = yamlMapper.convertValue(rawData, TestSuite.class);

            // Infer step types from config
            if (suite.getTests() != null) {
                suite.getTests().forEach(this::inferStepType);
            }

            return suite;
        } catch (Exception e) {
            // Check if it's an unrecognized property exception (including wrapped ones)
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException) {
                    com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException upe =
                        (com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException) cause;

                    String fieldName = upe.getPropertyName();
                    String location = upe.getPath().isEmpty() ? "root" :
                        upe.getPath().stream()
                            .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                            .reduce((a, b) -> a + " -> " + b)
                            .orElse("unknown");

                    throw new IllegalArgumentException(
                        String.format("Invalid YAML structure:\n" +
                            "  Unknown field: '%s'\n" +
                            "  Location: %s\n" +
                            "  File: %s\n\n" +
                            "Common issues:\n" +
                            "  - 'steps' field is not supported. Each item in 'tests' is a single step.\n" +
                            "  - Check field names match the documentation (case-sensitive).\n" +
                            "  - Nested test steps are not supported.",
                            fieldName, location, testFile.getName())
                    );
                } else if (cause instanceof com.fasterxml.jackson.core.JsonParseException) {
                    com.fasterxml.jackson.core.JsonParseException jpe =
                        (com.fasterxml.jackson.core.JsonParseException) cause;
                    throw new IllegalArgumentException(
                        String.format("YAML syntax error in '%s':\n  %s\n  Line: %d, Column: %d",
                            testFile.getName(), jpe.getOriginalMessage(),
                            jpe.getLocation().getLineNr(), jpe.getLocation().getColumnNr())
                    );
                }
                cause = cause.getCause();
            }

            // Generic error if we couldn't identify the specific type
            throw new IllegalArgumentException(
                String.format("Failed to parse YAML file '%s': %s",
                    testFile.getName(), e.getMessage())
            );
        }
    }

    private void inferStepType(TestStep step) {
        // Infer type from which config is present
        if (step.getType() == null) {
            if (step.getRest() != null) {
                step.setType(StepType.REST);
            } else if (step.getGrpc() != null) {
                step.setType(StepType.GRPC);
            } else if (step.getFs() != null) {
                step.setType(StepType.FS);
            } else if (step.getResolvePath() != null) {
                step.setType(StepType.RESOLVE_PATH);
            } else if (step.getSet() != null) {
                step.setType(StepType.SET);
            } else if (step.getTransform() != null) {
                step.setType(StepType.TRANSFORM);
            }
        }
    }
}
