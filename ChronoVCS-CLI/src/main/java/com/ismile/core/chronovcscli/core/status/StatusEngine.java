package com.ismile.core.chronovcscli.core.status;

import java.io.File;
import java.io.IOException;

public interface StatusEngine {
    StatusResult getStatus(File projectRoot) throws IOException;
}
