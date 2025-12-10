package com.ismile.argusomnicli.model;

import lombok.Data;

/**
 * Bash script execution configuration.
 * Allows running shell commands/scripts as part of tests.
 */
@Data
public class BashConfig {
    /**
     * The bash command or script to execute.
     * Can use variables: "psql -U user -c 'DELETE FROM {{tableName}}'"
     */
    private String command;

    /**
     * Optional: Path to a bash script file to execute
     * If both command and script are provided, command takes precedence
     */
    private String script;

    /**
     * Working directory for the command execution
     * Default: current directory
     */
    private String workingDir;

    /**
     * Timeout in milliseconds (default: 30000ms = 30s)
     */
    private Integer timeout;

    /**
     * Whether to ignore non-zero exit codes
     * Default: false (will fail test on non-zero exit)
     */
    private Boolean ignoreExitCode;

    /**
     * Expected exit code (default: 0)
     * Test will fail if actual exit code doesn't match
     */
    private Integer expectedExitCode;
}
