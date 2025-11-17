package com.ismile.core.chronovcscli.core.commit.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcscli.core.commit.CommitEngine;
import com.ismile.core.chronovcscli.core.commit.CommitModel;
import com.ismile.core.chronovcscli.core.hash.HashEngine;
import com.ismile.core.chronovcscli.core.index.IndexEngine;
import com.ismile.core.chronovcscli.core.objectsStore.ObjectStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommitEngineImpl implements CommitEngine {
    private final IndexEngine indexEngine;
    private final ObjectStore objectStore;
    private final HashEngine hashEngine;
    private final ObjectMapper mapper = new ObjectMapper();
    @Override
    public String commit(File projectRoot, String message) throws IOException {
        // load index
        indexEngine.loadIndex(projectRoot);
        Map<String, String> entries = indexEngine.getEntries();

        // is empty commit?
        if(entries.isEmpty()) {
            throw new RuntimeException("Empty commit");
        }

        // write snapshot to object store (if blobs exist)
        for(Map.Entry<String, String> e : entries.entrySet()) {
            File f = new File(projectRoot, e.getKey());
            if(f.exists()) {
                objectStore.writeBlob(f); // ensure blob exists
            }
        }

        // prepare commit model
        CommitModel commit = new CommitModel();
        commit.setMessage(message);
        commit.setTimestamp(Instant.now().toString());
        commit.setFiles(entries);

        // parent commit
        String parent = getCurrentHead(projectRoot);
        commit.setParent(parent);

        // commit ID = hash of JSON model
        String modelJson = mapper.writeValueAsString(commit);
        String commitId = hashEngine.hashString(modelJson);
        commit.setId(commitId);

        // save commit file
        File commitFile = new File(projectRoot, ".vcs/commits/" + commitId + ".json");
        commitFile.getParentFile().mkdirs();
        mapper.writerWithDefaultPrettyPrinter().writeValue(commitFile, commit);

        // update branch pointer
        updateBranchHead(projectRoot, commitId);

        return commitId;
    }

    private String getCurrentHead(File root) throws IOException {
        File head = new File(root, ".vcs/HEAD");
        String ref = java.nio.file.Files.readString(head.toPath()).trim();
        if(!ref.startsWith("ref:"))
            return null;
        String refPath = ref.replace("ref: ", "");
        File refFile = new File(root, ".vcs/" + refPath);

        if(!refFile.exists())
            return null;

        return java.nio.file.Files.readString(refFile.toPath()).trim();
    }

    private void updateBranchHead(File root, String commitId) throws IOException {
        File head = new File(root, ".vcs/HEAD");
        String ref = java.nio.file.Files.readString(head.toPath()).trim().replace("ref: ", "");
        File refFile = new File(root, ".vcs/" + ref);

        java.nio.file.Files.writeString(refFile.toPath(), commitId);
    }
}
