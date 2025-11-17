package com.ismile.core.chronovcscli.core.add.impl;

import com.ismile.core.chronovcscli.core.add.AddEngine;
import com.ismile.core.chronovcscli.core.hash.HashEngine;
import com.ismile.core.chronovcscli.core.ignore.IgnoreEngine;
import com.ismile.core.chronovcscli.core.index.IndexEngine;
import com.ismile.core.chronovcscli.core.objectsStore.ObjectStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Slf4j
@Component
@RequiredArgsConstructor
public class AddEngineImpl implements AddEngine {
    private final IndexEngine indexEngine;
    private final IgnoreEngine ignoreEngine;
    private final HashEngine hashEngine;
    private final ObjectStore objectStore;

    @Override
    public void add(File projectRoot, String path) throws IOException {
        indexEngine.loadIndex(projectRoot);
        File target = new File(projectRoot, path);
        if(!target.exists()) {
            throw new RuntimeException("Path does not exist: " + path);
        }

        if(target.isDirectory()) {
            addDirectory(projectRoot, target);
        } else {
            addFile(projectRoot, target);
        }

        indexEngine.saveIndex(projectRoot);

    }

    private void addFile(File root, File file) throws IOException {
        if(ignoreEngine.isIgnored(root, file))
            return;
        String relative = root.toPath().relativize(file.toPath()).toString();

        if(relative.startsWith(".vcs"))
            return;

        // calculate hash
        String hash = hashEngine.hashFile(file);

        // write blob
        objectStore.writeBlob(file);

        // update index
        indexEngine.updateFile(relative, hash);

        log.debug("Added to index: {} ({})", relative, hash);
    }

    private void addDirectory(File root, File dir) throws IOException {
        Files.walk(dir.toPath())
                .filter(Files::isRegularFile)
                .forEach(p -> {
                    try {
                        String relative = root.toPath().relativize(p).toString();
                        if(relative.startsWith(".vcs"))
                            return;
                        addFile(root, p.toFile());
                    } catch(IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
