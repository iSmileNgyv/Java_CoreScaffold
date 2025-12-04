package com.ismile.core.chronovcscli.core.ignore.impl;

import com.ismile.core.chronovcscli.core.ignore.IgnoreEngine;
import com.ismile.core.chronovcscli.core.ignore.IgnoreParser;
import com.ismile.core.chronovcscli.core.ignore.IgnoreRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
@Component
@Slf4j
@RequiredArgsConstructor
public class IgnoreEngineImpl implements IgnoreEngine {
    private final IgnoreParser ignoreParser;
    private List<IgnoreRule> cached;

    @Override
    public boolean isIgnored(File projectRoot, File file) {
        loadRules(projectRoot);
        Path rootPath = projectRoot.toPath();
        Path filePath = file.toPath();

        String relative = rootPath.relativize(filePath).toString().replace("\\", "/");

        for(IgnoreRule rule : cached) {
            if(matches(rule, relative)) {
                return true;
            }
        }
        return false;
    }

    private void loadRules(File projectRoot) {
        if(cached != null)
            return;
        File ignoreFile = new File(projectRoot, ".chronoignore");
        cached = ignoreParser.parseFile(ignoreFile);
    }

    private boolean matches(IgnoreRule rule, String relativePath) {
        String pattern = rule.getPattern();

        // directory rule: "node_modules/" or ".gradle"
        if(rule.isDirectoryRule()) {
            // Remove trailing slash
            String dirName = pattern.substring(0, pattern.length() - 1);
            return relativePath.equals(dirName) || relativePath.startsWith(dirName + "/");
        }

        // Check if pattern is a directory name without trailing slash (e.g., ".gradle", "build")
        // Should match both the directory itself and all files/subdirectories under it
        if(!pattern.contains("*") && !pattern.contains(".")) {
            // Pattern like "build" or ".gradle" - directory name
            return relativePath.equals(pattern) || relativePath.startsWith(pattern + "/");
        }

        // For patterns with dot but no extension wildcard (e.g., ".gradle", ".idea")
        if(!pattern.contains("*") && pattern.startsWith(".") && !pattern.substring(1).contains(".")) {
            // Hidden directory like ".gradle", ".idea"
            return relativePath.equals(pattern) || relativePath.startsWith(pattern + "/");
        }

        // wildcard "*.log"
        if(pattern.contains("*")) {
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            return relativePath.matches(regex);
        }

        // exact match (for specific files)
        return relativePath.equals(pattern);
    }
}
