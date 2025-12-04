package com.ismile.argusomnicli.model;

import lombok.Data;

import java.util.Map;

/**
 * Expectation/Assertion configuration.
 * Defines what outcomes to validate.
 */
@Data
public class ExpectConfig {
    private Integer status;
    private Map<String, Object> json;
    private Map<String, Object> jsonContains;
    private Map<String, Object> jsonNotContains;
    private String fsExists;
    private String fsSize;
    private Map<String, Object> equals;
}
