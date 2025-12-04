package com.ismile.argusomnicli.model;

import lombok.Data;

import java.util.Map;

/**
 * Set variable configuration.
 * Allows explicit variable setting.
 */
@Data
public class SetConfig {
    private Map<String, Object> variables;
}
