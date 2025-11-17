package com.ismile.core.chronovcscli.core.objectsStore;

import java.io.File;
import java.io.IOException;

public interface ObjectStore {
    String writeBlob(File file) throws IOException;
    byte[] readBlob(String hash) throws IOException;
    boolean exists(String hash);
}
