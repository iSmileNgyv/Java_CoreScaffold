package com.ismile.core.chronovcscli.core.index;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public interface IndexEngine {
    void loadIndex(File projectRoot) throws IOException;
    void updateFile(String relativePath, String blobHash);
    void removeFile(String relativePath);
    Map<String, String> getEntries();
    void saveIndex(File projectRoot) throws IOException;
}
