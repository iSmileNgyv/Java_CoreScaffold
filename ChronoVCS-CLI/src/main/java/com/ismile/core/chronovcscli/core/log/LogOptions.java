package com.ismile.core.chronovcscli.core.log;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Options for log command
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogOptions {

    /**
     * Maximum number of commits to display (null = all)
     */
    private Integer limit;

    /**
     * Output format
     */
    @Builder.Default
    private LogFormat format = LogFormat.FULL;

    /**
     * Show graph visualization
     */
    @Builder.Default
    private boolean showGraph = false;

    /**
     * Filter by specific file path
     */
    private String filePath;

    /**
     * Filter by branch (null = current branch)
     */
    private String branch;

    /**
     * Show file changes in each commit
     */
    @Builder.Default
    private boolean showFiles = false;

    public enum LogFormat {
        /**
         * Full format with all details
         */
        FULL,

        /**
         * One line per commit (commit hash + message)
         */
        ONELINE,

        /**
         * Short format (hash, date, message)
         */
        SHORT
    }
}
