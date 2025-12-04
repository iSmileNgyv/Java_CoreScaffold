package com.ismile.argusomnicli.model;

import lombok.Data;

/**
 * Variable transformation configuration.
 * Applies functions to existing variables.
 */
@Data
public class TransformConfig {
    private String input;
    private String function;
    private String output;
}
