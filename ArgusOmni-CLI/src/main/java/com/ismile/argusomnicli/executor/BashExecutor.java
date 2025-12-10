package com.ismile.argusomnicli.executor;

import com.ismile.argusomnicli.model.BashConfig;
import com.ismile.argusomnicli.model.StepType;
import com.ismile.argusomnicli.model.TestStep;
import com.ismile.argusomnicli.runner.ExecutionContext;
import com.ismile.argusomnicli.variable.VariableResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Executor for running Bash commands and scripts.
 * Allows test suites to execute shell commands for setup/teardown operations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BashExecutor implements TestExecutor {

    private final VariableResolver variableResolver;

    @Override
    public boolean supports(TestStep step) {
        return step.getType() == StepType.BASH && step.getBash() != null;
    }

    @Override
    public ExecutionResult execute(TestStep step, ExecutionContext context) throws Exception {
        BashConfig config = step.getBash();
        long startTime = System.currentTimeMillis();

        try {
            // Determine what to execute
            String commandToExecute;
            if (config.getCommand() != null && !config.getCommand().trim().isEmpty()) {
                // Direct command execution
                commandToExecute = variableResolver.resolve(config.getCommand(), context.getVariableContext());
            } else if (config.getScript() != null && !config.getScript().trim().isEmpty()) {
                // Script file execution
                String scriptPath = variableResolver.resolve(config.getScript(), context.getVariableContext());
                if (!Files.exists(Paths.get(scriptPath))) {
                    throw new RuntimeException("Script file not found: " + scriptPath);
                }
                commandToExecute = "bash " + scriptPath;
            } else {
                throw new RuntimeException("Either 'command' or 'script' must be provided");
            }

            // Setup working directory
            File workingDirectory = null;
            if (config.getWorkingDir() != null) {
                String resolvedDir = variableResolver.resolve(config.getWorkingDir(), context.getVariableContext());
                workingDirectory = new File(resolvedDir);
                if (!workingDirectory.exists()) {
                    throw new RuntimeException("Working directory not found: " + resolvedDir);
                }
            }

            // Build process
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("bash", "-c", commandToExecute);

            if (workingDirectory != null) {
                processBuilder.directory(workingDirectory);
            }

            processBuilder.redirectErrorStream(true);

            // Execute command
            log.info("Executing bash command: {}", commandToExecute);
            Process process = processBuilder.start();

            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // Wait for completion with timeout
            int timeout = config.getTimeout() != null ? config.getTimeout() : 30000;
            boolean finished = process.waitFor(timeout, TimeUnit.MILLISECONDS);

            if (!finished) {
                process.destroy();
                throw new RuntimeException("Command timed out after " + timeout + "ms");
            }

            int exitCode = process.exitValue();
            long duration = System.currentTimeMillis() - startTime;

            // Check exit code
            int expectedExitCode = config.getExpectedExitCode() != null ? config.getExpectedExitCode() : 0;
            boolean ignoreExitCode = config.getIgnoreExitCode() != null && config.getIgnoreExitCode();

            boolean success = ignoreExitCode || (exitCode == expectedExitCode);

            // Extract variables from output if needed
            Map<String, Object> extracted = new HashMap<>();
            if (step.getExtract() != null && !step.getExtract().isEmpty()) {
                extracted = extractVariables(output.toString(), step.getExtract(), context);
            }

            // Build result
            ExecutionResult.ExecutionResultBuilder resultBuilder = ExecutionResult.builder()
                    .success(success)
                    .stepName(step.getName())
                    .response(output.toString())
                    .statusCode(exitCode)
                    .durationMs(duration)
                    .extractedVariables(extracted)
                    .continueOnError(step.isContinueOnError());

            if (!success) {
                String errorMsg = String.format(
                    "Bash command failed with exit code %d (expected %d)\nOutput:\n%s",
                    exitCode, expectedExitCode, output.toString()
                );
                resultBuilder.errorMessage(errorMsg);
                log.error(errorMsg);
            } else {
                log.info("Bash command completed successfully with exit code {}", exitCode);
                if (output.length() > 0) {
                    log.debug("Output:\n{}", output.toString());
                }
            }

            return resultBuilder.build();

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Bash execution failed", e);

            return ExecutionResult.builder()
                    .success(false)
                    .stepName(step.getName())
                    .errorMessage("Bash execution error: " + e.getMessage())
                    .durationMs(duration)
                    .continueOnError(step.isContinueOnError())
                    .build();
        }
    }

    /**
     * Extract variables from bash output using simple pattern matching
     */
    private Map<String, Object> extractVariables(String output, Map<String, String> extractConfig, ExecutionContext context) {
        Map<String, Object> extracted = new HashMap<>();

        for (Map.Entry<String, String> entry : extractConfig.entrySet()) {
            String varName = entry.getKey();
            String pattern = entry.getValue();

            // For bash output, we support:
            // 1. "line:N" - extract Nth line (0-indexed)
            // 2. "last" - extract last line
            // 3. "all" - extract all output
            // 4. A regex pattern for more complex extraction

            String value;
            if (pattern.startsWith("line:")) {
                int lineNum = Integer.parseInt(pattern.substring(5));
                String[] lines = output.split("\n");
                value = lineNum < lines.length ? lines[lineNum] : "";
            } else if ("last".equals(pattern)) {
                String[] lines = output.split("\n");
                value = lines.length > 0 ? lines[lines.length - 1] : "";
            } else if ("all".equals(pattern)) {
                value = output;
            } else {
                // Try regex extraction
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
                java.util.regex.Matcher m = p.matcher(output);
                value = m.find() ? (m.groupCount() > 0 ? m.group(1) : m.group(0)) : "";
            }

            extracted.put(varName, value.trim());
        }

        return extracted;
    }
}
