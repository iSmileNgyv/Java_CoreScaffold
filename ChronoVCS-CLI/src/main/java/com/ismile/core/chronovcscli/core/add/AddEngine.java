package com.ismile.core.chronovcscli.core.add;

import java.io.File;
import java.io.IOException;

public interface AddEngine {
    void add(File projectRoot, String path) throws IOException;
}
