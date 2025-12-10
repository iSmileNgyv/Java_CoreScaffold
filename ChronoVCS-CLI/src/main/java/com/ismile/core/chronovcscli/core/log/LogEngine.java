package com.ismile.core.chronovcscli.core.log;

import java.io.File;
import java.util.List;

/**
 * Engine for reading and displaying commit history
 */
public interface LogEngine {

    /**
     * Get commit history with options
     *
     * @param projectRoot project root directory
     * @param options     log options
     * @return list of log entries
     */
    List<LogEntry> getLog(File projectRoot, LogOptions options);

    /**
     * Get commit history for current branch with default options
     *
     * @param projectRoot project root directory
     * @return list of log entries
     */
    List<LogEntry> getLog(File projectRoot);

    /**
     * Format log entries for display
     *
     * @param entries log entries
     * @param format  output format
     * @return formatted string
     */
    String formatLog(List<LogEntry> entries, LogOptions.LogFormat format);
}
