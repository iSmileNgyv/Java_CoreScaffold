package com.ismile.core.chronovcscli.core.ignore;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class IgnoreParser {
    public List<IgnoreRule> parseFile(File ignoreFile) {
        List<IgnoreRule> rules = new ArrayList<>();
        if(!ignoreFile.exists()) {
            return rules;
        }

        try(BufferedReader reader = new BufferedReader(new FileReader(ignoreFile))) {
            String line;
            while((line = reader.readLine()) != null) {
                line = line.trim();
                if(line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                boolean isDirRule = line.endsWith("/");
                rules.add(new IgnoreRule(line, isDirRule));
            }
            return rules;
        } catch(IOException e) {
            throw new RuntimeException("Failed to parse ignore file", e);
        }
    }
}
