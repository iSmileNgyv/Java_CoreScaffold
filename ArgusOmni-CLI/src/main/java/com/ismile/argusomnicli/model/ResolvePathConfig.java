package com.ismile.argusomnicli.model;

import lombok.Data;

/**
 * Path resolution configuration.
 * Maps logical paths to physical filesystem paths.
 */
@Data
public class ResolvePathConfig {
    private String repoId;
    private String logicalPath;
    private String output;
}
