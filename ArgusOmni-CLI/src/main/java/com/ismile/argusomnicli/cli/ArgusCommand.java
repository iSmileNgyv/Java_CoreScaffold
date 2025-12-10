package com.ismile.argusomnicli.cli;

import com.ismile.argusomnicli.model.TestSuite;
import com.ismile.argusomnicli.parser.TestParser;
import com.ismile.argusomnicli.runner.TestRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.io.File;

/**
 * Main CLI command for ArgusOmni.
 * Entry point for test execution.
 */
@Component
@Command(
        name = "argus",
        mixinStandardHelpOptions = true,
        version = "1.0.0",
        description = "ArgusOmni - Universal Test Orchestrator for REST, gRPC, and File Systems",
        subcommands = {RunCommand.class}
)
public class ArgusCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("ArgusOmni Test Orchestrator v1.0.0");
        System.out.println("Use 'argus run <test-file>' to execute tests");
        System.out.println("Use 'argus --help' for more information");
    }
}

/**
 * Run command - executes test suite.
 */
@Component
@Command(
        name = "run",
        description = "Run a test suite from YAML file"
)
@RequiredArgsConstructor
class RunCommand implements Runnable {

    private final TestParser parser;
    private final TestRunner runner;

    @Parameters(index = "0", description = "Path to test YAML file")
    private File testFile;

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    private boolean verbose;

    @Option(names = {"--env"}, description = "Override environment (e.g., --env=prod)")
    private String environment;

    @Override
    public void run() {
        try {
            // Parse test file
            TestSuite suite = parser.parse(testFile);

            // Override environment if specified
            if (environment != null && suite.getEnv() != null) {
                suite.getEnv().put("environment", environment);
            }

            // Run tests
            int exitCode = runner.run(suite, verbose);

            // Exit with appropriate code
            System.exit(exitCode);

        } catch (Exception e) {
            System.err.println("Failed to run tests: " + e.getMessage());

            // Only show stack trace for unexpected errors (not user errors like invalid YAML)
            if (verbose && !(e instanceof IllegalArgumentException)) {
                e.printStackTrace();
            }

            System.exit(2); // Invalid YAML or setup error
        }
    }
}
