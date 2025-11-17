package com.ismile.core.chronovcscli.core.ignore;

import java.io.File;

public interface IgnoreEngine {
    boolean isIgnored(File projectRoot, File file);
}
