package com.ismile.core.chronovcscli.core.objectsStore.impl;

import com.ismile.core.chronovcscli.core.hash.HashEngine;
import com.ismile.core.chronovcscli.core.ignore.IgnoreEngine;
import com.ismile.core.chronovcscli.core.objectsStore.ObjectStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;

@Component
@Slf4j
public class ObjectStoreImpl implements ObjectStore {
    private final HashEngine hashEngine;
    private final IgnoreEngine ignoreEngine;
    private File projectRoot;

    // For testing
    ObjectStoreImpl(HashEngine hashEngine, IgnoreEngine ignoreEngine, File projectRoot) {
        this.hashEngine = hashEngine;
        this.ignoreEngine = ignoreEngine;
        this.projectRoot = projectRoot;
    }

    @Autowired
    public ObjectStoreImpl(HashEngine hashEngine, IgnoreEngine ignoreEngine) {
        this.hashEngine = hashEngine;
        this.ignoreEngine = ignoreEngine;
        this.projectRoot = null;
    }

    @Override
    public String writeBlob(File file) throws IOException {
        File writeProjectRoot = findProjectRoot(file.getAbsoluteFile().getParentFile());

        // skip .vcs files
        String relative = writeProjectRoot.toPath().relativize(file.toPath()).toString();
        if(relative.startsWith(".vcs" + File.separator) || relative.equals(".vcs")) {
            log.debug("Skipping internal vcs files {}", relative);
            return null;
        }

        // is ignore ?
        if (ignoreEngine.isIgnored(writeProjectRoot, file)) {
            log.debug("Ignoring file: {}", file.getName());
            return null;
        }

        // calculate hash
        String hash = hashEngine.hashFile(file);

        // object paths
        String prefix = hash.substring(0, 2);
        String suffix = hash.substring(2);

        File objectsDir = new File(writeProjectRoot, ".vcs/objects");
        File prefixDir = new File(objectsDir, prefix);
        File blobFile = new File(prefixDir, suffix);

        // check if already exists
        if (blobFile.exists()) {
            return hash;
        }

        // create directories
        if (!prefixDir.exists()) {
            prefixDir.mkdirs();
        }

        // write content
        byte[] content = Files.readAllBytes(file.toPath());
        Files.write(blobFile.toPath(), content);

        return hash;
    }

    @Override
    public byte[] readBlob(String hash) throws IOException {
        String prefix = hash.substring(0, 2);
        String suffix = hash.substring(2);

        File file = new File(projectRoot, ".vcs/objects/" + prefix + "/" + suffix);

        if (!file.exists()) {
            throw new FileNotFoundException("Blob not found for hash: " + hash);
        }
        return Files.readAllBytes(file.toPath());
    }

    @Override
    public boolean exists(String hash) {
        String prefix = hash.substring(0, 2);
        String suffix = hash.substring(2);

        File file = new File(projectRoot, ".vcs/objects/" + prefix + "/" + suffix);

        return file.exists();
    }

    private File findProjectRoot(File currentDir) {
        if (currentDir == null) {
            throw new IllegalStateException("Not a ChronoVCS repository (or any of the parent directories): .vcs");
        }
        File vcsDir = new File(currentDir, ".vcs");
        if (vcsDir.exists() && vcsDir.isDirectory()) {
            return currentDir;
        }
        return findProjectRoot(currentDir.getParentFile());
    }
}
