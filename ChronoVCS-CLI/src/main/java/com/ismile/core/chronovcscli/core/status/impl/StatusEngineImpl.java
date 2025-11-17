package com.ismile.core.chronovcscli.core.status.impl;

import com.ismile.core.chronovcscli.core.hash.HashEngine;
import com.ismile.core.chronovcscli.core.ignore.IgnoreEngine;
import com.ismile.core.chronovcscli.core.index.IndexEngine;
import com.ismile.core.chronovcscli.core.objectsStore.ObjectStore;
import com.ismile.core.chronovcscli.core.status.StatusEngine;
import com.ismile.core.chronovcscli.core.status.StatusResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StatusEngineImpl implements StatusEngine {
    private final IgnoreEngine ignoreEngine;
    private final HashEngine hashEngine;
    private final ObjectStore objectStore;
    private final IndexEngine indexEngine;

    @Override
    public StatusResult getStatus(File projectRoot) throws IOException {
        indexEngine.loadIndex(projectRoot);
        Map<String, String> indexMap = indexEngine.getEntries();
        List<String> untracked = new ArrayList<>();
        List<String> modified = new ArrayList<>();
        List<String> deleted  = new ArrayList<>();

        Map<String, String> current = new HashMap<>();

        Files.walk(projectRoot.toPath())
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    File file = path.toFile();
                    String rel = projectRoot.toPath().relativize(path).toString();
                    if(rel.startsWith(".vcs"))
                        return;
                    if(ignoreEngine.isIgnored(projectRoot, file))
                        return;
                    try {
                        String hash = hashEngine.hashFile(file);
                        current.put(rel, hash);

                        if(!indexMap.containsKey(rel)) {
                            untracked.add(rel);
                        } else if(!indexMap.get(rel).equals(hash)) {
                            modified.add(rel);
                        }
                    } catch(IOException e) {
                        throw new RuntimeException("Failed to get status for file: " + file.getAbsolutePath(), e);
                    }
                });

        for(String path: indexMap.keySet()) {
            if(!current.containsKey(path)) {
                deleted.add(path);
            }
        }

        return new StatusResult(untracked, modified, deleted);
    }
}
