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
                throw new AssertionError("File does not exist: " + path);
            }
        }

        // Check not exists
        if (config.getNotExists() != null) {
            String path = variableResolver.resolve(config.getNotExists(), context.getVariableContext());
            File file = new File(path);
            boolean exists = file.exists();
            result.put("notExists", !exists);
            if (exists) {
                throw new AssertionError("File exists but should not: " + path);
            }
        }

        // Check contains
        if (config.getContains() != null) {
            String path = variableResolver.resolve(config.getContains(), context.getVariableContext());
            File file = new File(path);
            if (!file.exists()) {
                throw new AssertionError("File does not exist: " + path);
            }
            String content = Files.readString(file.toPath());
            result.put("content", content);
        }

        // Check size
        if (config.getSize() != null) {
            String path = variableResolver.resolve(config.getSize(), context.getVariableContext());
            File file = new File(path);
            if (!file.exists()) {
                throw new AssertionError("File does not exist: " + path);
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
                throw new AssertionError("Path is not a directory: " + path);
            }
        }

        return result;
    }
}
