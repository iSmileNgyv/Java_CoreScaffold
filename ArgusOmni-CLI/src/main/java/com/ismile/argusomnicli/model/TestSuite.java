package com.ismile.argusomnicli.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Represents the entire test suite loaded from YAML.
 * Encapsulates test metadata and steps.
 */
@Data
public class TestSuite {
    private Map<String, String> env;
    private Map<String, Object> variables;
    private List<TestStep> tests;
}
