package com.ismile.core.chronovcscli.core.commit;

import java.io.File;
import java.io.IOException;

public interface CommitEngine {
    String commit(File projectRoot, String message) throws IOException;
}
