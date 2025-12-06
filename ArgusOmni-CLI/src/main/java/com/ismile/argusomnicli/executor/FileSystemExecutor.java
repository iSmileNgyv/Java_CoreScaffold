package com.ismile.argusomnicli.executor;

import com.ismile.argusomnicli.extractor.ResponseExtractor;
import com.ismile.argusomnicli.model.FileSystemConfig;
import com.ismile.argusomnicli.model.StepType;
import com.ismile.argusomnicli.model.TestStep;
import com.ismile.argusomnicli.runner.ExecutionContext;
import com.ismile.argusomnicli.variable.VariableResolver;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

/**
 * File System executor implementation.
 * Follows Open/Closed Principle - extends AbstractExecutor without modifying it.
 * Follows Single Responsibility - only executes FS operations.
 */
@Component
public class FileSystemExecutor extends AbstractExecutor {

    public FileSystemExecutor(VariableResolver variableResolver,
                            ResponseExtractor responseExtractor) {
        super(variableResolver, responseExtractor);
    }

    @Override
    public boolean supports(TestStep step) {
        return step.getType() == StepType.FS && step.getFs() != null;
    }

    @Override
    protected Object doExecute(TestStep step, ExecutionContext context) throws Exception {
        FileSystemConfig config = step.getFs();
        Map<String, Object> result = new HashMap<>();

        // Check exists
        if (config.getExists() != null) {
            String path = variableResolver.resolve(config.getExists(), context.getVariableContext());
            File file = new File(path);
            boolean exists = file.exists();
            result.put("exists", exists);
            if (!exists) {
                throw new Exception("File does not exist: " + path);
            }
        }

        // Check not exists
        if (config.getNotExists() != null) {
            String path = variableResolver.resolve(config.getNotExists(), context.getVariableContext());
            File file = new File(path);
            boolean exists = file.exists();
            result.put("notExists", !exists);
            if (exists) {
                throw new Exception("File exists but should not: " + path);
            }
        }

        // Check contains
        if (config.getContains() != null) {
            String path = variableResolver.resolve(config.getContains(), context.getVariableContext());
            File file = new File(path);
            if (!file.exists()) {
                throw new Exception("File does not exist: " + path);
            }
            String content = Files.readString(file.toPath());
            result.put("content", content);
        }

        // Check size
        if (config.getSize() != null) {
            String path = variableResolver.resolve(config.getSize(), context.getVariableContext());
            File file = new File(path);
            if (!file.exists()) {
                throw new Exception("File does not exist: " + path);
            }
            long size = file.length();
            result.put("size", size);
        }

        // Check is directory
        if (config.getIsDirectory() != null) {
            String path = variableResolver.resolve(config.getIsDirectory(), context.getVariableContext());
            File file = new File(path);
            boolean isDir = file.isDirectory();
            result.put("isDirectory", isDir);
            if (!isDir) {
                throw new Exception("Path is not a directory: " + path);
            }
        }

        // Read file
        if (config.getRead() != null) {
            String path = variableResolver.resolve(config.getRead(), context.getVariableContext());
            File file = new File(path);
            if (!file.exists()) {
                throw new Exception("File does not exist: " + path);
            }
            String content = Files.readString(file.toPath());
            result.put("content", content);
        }

        // Create directory
        if (config.getCreateDir() != null) {
            String path = variableResolver.resolve(config.getCreateDir(), context.getVariableContext());
            File dir = new File(path);
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (!created) {
                    throw new Exception("Failed to create directory: " + path);
                }
                result.put("created", true);
            } else {
                result.put("created", false);
                result.put("message", "Directory already exists");
            }
        }

        // Delete directory
        if (config.getDeleteDir() != null) {
            String path = variableResolver.resolve(config.getDeleteDir(), context.getVariableContext());
            File dir = new File(path);
            if (dir.exists()) {
                boolean deleted = deleteDirectory(dir);
                if (!deleted) {
                    throw new Exception("Failed to delete directory: " + path);
                }
                result.put("deleted", true);
            } else {
                result.put("deleted", false);
                result.put("message", "Directory does not exist");
            }
        }

        // Write file
        if (config.getWrite() != null) {
            Map<String, String> writeConfig = config.getWrite();
            String path = variableResolver.resolve(writeConfig.get("path"), context.getVariableContext());
            String content = variableResolver.resolve(writeConfig.get("content"), context.getVariableContext());

            File file = new File(path);
            // Create parent directories if needed
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            Files.writeString(file.toPath(), content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            result.put("written", true);
            result.put("path", path);
        }

        return result;
    }

    /**
     * Recursively delete a directory.
     */
    private boolean deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        return directory.delete();
    }
}
