package com.ismile.argusomnicli.model;

import lombok.Data;
import java.util.Map;

/**
 * File System configuration.
 * Supports both read/validation and write operations.
 */
@Data
public class FileSystemConfig {
    // Read/Validation operations
    private String exists;
    private String notExists;
    private String contains;
    private String size;
    private String isDirectory;
    private String read;

    // Write operations
    private String createDir;
    private String deleteDir;
    private Map<String, String> write; // { "path": "...", "content": "..." }
}
