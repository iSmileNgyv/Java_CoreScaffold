package com.ismile.core.chronovcscli.core.hash;

import java.io.File;
import java.io.IOException;

public interface HashEngine {
    String hashBytes(byte[] data);
    String hashString(String text);
    String hashFile(File file) throws IOException;
}
