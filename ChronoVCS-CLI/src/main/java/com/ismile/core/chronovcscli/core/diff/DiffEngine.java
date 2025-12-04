package com.ismile.core.chronovcscli.core.diff;

import java.io.File;
import java.io.IOException;

public interface DiffEngine {
    DiffResult diffWorkingVsStaged(File projectRoot) throws IOException;
    DiffResult diffStagedVsHead(File projectRoot) throws IOException;
}
