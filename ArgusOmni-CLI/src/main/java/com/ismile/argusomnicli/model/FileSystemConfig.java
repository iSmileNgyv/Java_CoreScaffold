package com.ismile.argusomnicli.model;

import lombok.Data;

/**
 * File System validation configuration.
 * Encapsulates FS-specific checks.
 */
@Data
public class FileSystemConfig {
    private String exists;
    private String notExists;
    private String contains;
    private String size;
    private String isDirectory;
}
