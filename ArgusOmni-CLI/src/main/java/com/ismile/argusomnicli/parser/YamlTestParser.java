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
