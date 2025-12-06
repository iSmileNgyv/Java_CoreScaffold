package com.ismile.argusomnicli.logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ismile.argusomnicli.executor.ExecutionResult;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Test execution logger.
 * Logs all test execution details to JSON file.
 * Follows Single Responsibility - only logs test executions.
 */
@Component
public class TestExecutionLogger {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final ObjectMapper objectMapper;

    public TestExecutionLogger() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Write test execution log to file.
     *
     * @param testSuiteName Test suite name
     * @param results Test execution results
     * @param logDir Directory to write log file
     * @return Log file path
     */
    public String writeLog(String testSuiteName, List<ExecutionResult> results, String logDir) {
        try {
            // Create log directory if not exists
            File logDirectory = new File(logDir);
            if (!logDirectory.exists()) {
                logDirectory.mkdirs();
            }

            // Generate unique filename
            String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
            String sanitizedName = testSuiteName.replaceAll("[^a-zA-Z0-9-_]", "_");
            String filename = String.format("%s_%s.json", sanitizedName, timestamp);
            File logFile = new File(logDirectory, filename);

            // Build log object
            TestExecutionLog log = buildLog(testSuiteName, results);

            // Write to file
            objectMapper.writeValue(logFile, log);

            return logFile.getAbsolutePath();

        } catch (Exception e) {
            System.err.println("Failed to write log: " + e.getMessage());
            return null;
        }
    }

    private TestExecutionLog buildLog(String testSuiteName, List<ExecutionResult> results) {
        TestExecutionLog log = new TestExecutionLog();
        log.setTestSuiteName(testSuiteName);
        log.setExecutionTime(LocalDateTime.now().toString());
        log.setTotalTests(results.size());

        long passedCount = results.stream().filter(ExecutionResult::isSuccess).count();
        long failedCount = results.size() - passedCount;

        log.setPassedTests((int) passedCount);
        log.setFailedTests((int) failedCount);

        long totalDuration = results.stream()
                .mapToLong(ExecutionResult::getDurationMs)
                .sum();
        log.setTotalDurationMs(totalDuration);

        // Add detailed step logs
        List<StepLog> stepLogs = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            ExecutionResult result = results.get(i);
            StepLog stepLog = new StepLog();
            stepLog.setStepNumber(i + 1);
            stepLog.setStepName(result.getStepName());
            stepLog.setSuccess(result.isSuccess());
            stepLog.setDurationMs(result.getDurationMs());
            stepLog.setStatusCode(result.getStatusCode());
            stepLog.setRequest(result.getRequestDetails());
            stepLog.setResponse(result.getResponse());
            stepLog.setExtractedVariables(result.getExtractedVariables());
            stepLog.setErrorMessage(result.getErrorMessage());
            stepLogs.add(stepLog);
        }

        log.setSteps(stepLogs);

        return log;
    }

    @Data
    public static class TestExecutionLog {
        private String testSuiteName;
        private String executionTime;
        private int totalTests;
        private int passedTests;
        private int failedTests;
        private long totalDurationMs;
        private List<StepLog> steps;
    }

    @Data
    public static class StepLog {
        private int stepNumber;
        private String stepName;
        private boolean success;
        private long durationMs;
        private Integer statusCode;
        private ExecutionResult.RequestDetails request;
        private Object response;
        private Object extractedVariables;
        private String errorMessage;
    }
}
