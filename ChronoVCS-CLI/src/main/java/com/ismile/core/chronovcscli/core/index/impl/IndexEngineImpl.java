package com.ismile.core.chronovcscli.core.index.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcscli.core.index.IndexEngine;
import com.ismile.core.chronovcscli.core.index.IndexModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@Component
@Slf4j
public class IndexEngineImpl implements IndexEngine {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private IndexModel indexModel = new IndexModel();

    @Override
    public void loadIndex(File projectRoot) throws IOException {
        File indexFile = new File(projectRoot, ".vcs/index.json");

        if(!indexFile.exists()) {
            log.debug("Index file not found, creating empty index");
            indexModel = new IndexModel();
            return;
        }
        indexModel = objectMapper.readValue(indexFile, IndexModel.class);
    }

    @Override
    public void updateFile(String relativePath, String blobHash) {
        indexModel.getFiles().put(relativePath, blobHash);
    }

    @Override
    public void removeFile(String relativePath) {
        indexModel.getFiles().remove(relativePath);
    }

    @Override
    public Map<String, String> getEntries() {
        return Collections.unmodifiableMap(indexModel.getFiles());
    }

    @Override
    public void saveIndex(File projectRoot) throws IOException {
        File indexFile = new File(projectRoot, ".vcs/index.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(indexFile, indexModel);
    }
}
