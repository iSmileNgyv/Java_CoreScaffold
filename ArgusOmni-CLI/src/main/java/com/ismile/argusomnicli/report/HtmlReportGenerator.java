package com.ismile.argusomnicli.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ismile.argusomnicli.executor.ExecutionResult;
import com.ismile.argusomnicli.model.PerformanceMetrics;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * HTML Report Generator implementation.
 * Follows Single Responsibility Principle - only generates HTML reports.
 * Follows Open/Closed Principle - implements ReportGenerator interface.
 */
@Component
public class HtmlReportGenerator implements ReportGenerator {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String generateReport(String testSuiteName, List<ExecutionResult> results, String outputDir) {
        try {
            // Build report data
            ReportData reportData = buildReportData(testSuiteName, results);

            // Generate HTML content
            String htmlContent = generateHtmlContent(reportData);

            // Write to file
            String fileName = generateFileName(testSuiteName);
            String filePath = writeToFile(htmlContent, outputDir, fileName);

            return filePath;

        } catch (Exception e) {
            System.err.println("Failed to generate HTML report: " + e.getMessage());
            return null;
        }
    }

    @Override
    public String getReportFormat() {
        return "HTML";
    }

    /**
     * Build report data from execution results.
     * Follows Single Responsibility Principle.
     */
    private ReportData buildReportData(String testSuiteName, List<ExecutionResult> results) {
        int totalTests = results.size();
        long passedTests = results.stream().filter(ExecutionResult::isSuccess).count();
        long failedTests = totalTests - passedTests;

        long totalDuration = results.stream()
                .mapToLong(ExecutionResult::getDurationMs)
                .sum();

        long minDuration = results.stream()
                .mapToLong(ExecutionResult::getDurationMs)
                .min()
                .orElse(0);

        long maxDuration = results.stream()
                .mapToLong(ExecutionResult::getDurationMs)
                .max()
                .orElse(0);

        double avgDuration = totalTests > 0 ? (double) totalDuration / totalTests : 0.0;

        return ReportData.builder()
                .testSuiteName(testSuiteName)
                .executionTime(LocalDateTime.now())
                .totalTests(totalTests)
                .passedTests((int) passedTests)
                .failedTests((int) failedTests)
                .totalDurationMs(totalDuration)
                .minDurationMs(minDuration)
                .maxDurationMs(maxDuration)
                .avgDurationMs(avgDuration)
                .results(results)
                .build();
    }

    /**
     * Generate HTML content.
     * Follows Single Responsibility Principle.
     */
    private String generateHtmlContent(ReportData data) {
        StringBuilder html = new StringBuilder();

        // HTML structure
        html.append(generateHtmlHeader(data));
        html.append(generateSummarySection(data));
        html.append(generatePerformanceChartSection(data));
        html.append(generateTestDetailsSection(data));
        html.append(generateHtmlFooter());

        return html.toString();
    }

    private String generateHtmlHeader(ReportData data) {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>%s - Test Report</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
            background: #f5f7fa;
            padding: 20px;
            line-height: 1.6;
        }
        .container { max-width: 1200px; margin: 0 auto; }
        .header {
            background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
            color: white;
            padding: 30px;
            border-radius: 10px;
            margin-bottom: 30px;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
        }
        .header h1 { font-size: 32px; margin-bottom: 10px; }
        .header .meta { opacity: 0.9; font-size: 14px; }
        .summary {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }
        .summary-card {
            background: white;
            padding: 25px;
            border-radius: 10px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            border-left: 4px solid #667eea;
        }
        .summary-card.success { border-left-color: #10b981; }
        .summary-card.failed { border-left-color: #ef4444; }
        .summary-card.performance { border-left-color: #f59e0b; }
        .summary-card .value {
            font-size: 36px;
            font-weight: bold;
            margin: 10px 0;
        }
        .summary-card .label {
            color: #6b7280;
            font-size: 14px;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        .chart-section {
            background: white;
            padding: 30px;
            border-radius: 10px;
            margin-bottom: 30px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        .chart-section h2 {
            margin-bottom: 20px;
            color: #1f2937;
        }
        .progress-bar {
            height: 30px;
            background: #e5e7eb;
            border-radius: 15px;
            overflow: hidden;
            display: flex;
        }
        .progress-passed {
            background: linear-gradient(90deg, #10b981, #059669);
            display: flex;
            align-items: center;
            justify-content: center;
            color: white;
            font-size: 12px;
            font-weight: bold;
        }
        .progress-failed {
            background: linear-gradient(90deg, #ef4444, #dc2626);
            display: flex;
            align-items: center;
            justify-content: center;
            color: white;
            font-size: 12px;
            font-weight: bold;
        }
        .test-details {
            background: white;
            border-radius: 10px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            overflow: hidden;
        }
        .test-details h2 {
            padding: 20px 30px;
            background: #f9fafb;
            border-bottom: 1px solid #e5e7eb;
            color: #1f2937;
        }
        .test-item-wrapper {
            border-bottom: 1px solid #e5e7eb;
        }
        .test-item-wrapper:last-child { border-bottom: none; }
        .test-item {
            padding: 20px 30px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            cursor: pointer;
            transition: background 0.2s;
        }
        .test-item:hover { background: #f9fafb; }
        .test-status {
            width: 24px;
            height: 24px;
            border-radius: 50%%;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            font-weight: bold;
            font-size: 14px;
            margin-right: 15px;
        }
        .test-status.pass {
            background: #10b981;
            color: white;
        }
        .test-status.fail {
            background: #ef4444;
            color: white;
        }
        .test-info { flex: 1; }
        .test-name {
            font-weight: 600;
            color: #1f2937;
            margin-bottom: 5px;
        }
        .test-meta {
            font-size: 13px;
            color: #6b7280;
        }
        .test-performance {
            text-align: right;
            min-width: 120px;
        }
        .duration {
            font-size: 20px;
            font-weight: 600;
            color: #1f2937;
        }
        .performance-badge {
            display: inline-block;
            padding: 4px 12px;
            border-radius: 12px;
            font-size: 11px;
            font-weight: 600;
            text-transform: uppercase;
            margin-top: 5px;
        }
        .badge-excellent { background: #d1fae5; color: #065f46; }
        .badge-good { background: #dbeafe; color: #1e40af; }
        .badge-acceptable { background: #fef3c7; color: #92400e; }
        .badge-slow { background: #fee2e2; color: #991b1b; }
        .error-message {
            margin-top: 10px;
            padding: 10px 15px;
            background: #fef2f2;
            border-left: 3px solid #ef4444;
            border-radius: 4px;
            font-family: 'Courier New', monospace;
            font-size: 13px;
            color: #991b1b;
        }
        .footer {
            text-align: center;
            padding: 30px;
            color: #6b7280;
            font-size: 14px;
        }
        .expand-icon {
            margin-left: 10px;
            font-size: 12px;
            color: #6b7280;
            transition: transform 0.3s;
        }
        .test-item-wrapper.expanded .expand-icon {
            transform: rotate(180deg);
        }
        .test-details-content {
            max-height: 0;
            overflow: hidden;
            transition: max-height 0.3s ease-out;
            background: #f9fafb;
        }
        .test-item-wrapper.expanded .test-details-content {
            max-height: 5000px;
            padding: 20px 30px;
        }
        .detail-section {
            margin-bottom: 20px;
            background: white;
            border-radius: 8px;
            padding: 20px;
            box-shadow: 0 1px 3px rgba(0,0,0,0.1);
        }
        .detail-section:last-child {
            margin-bottom: 0;
        }
        .detail-section h3 {
            margin-bottom: 15px;
            color: #1f2937;
            font-size: 16px;
            font-weight: 600;
        }
        .detail-grid {
            display: grid;
            gap: 15px;
        }
        .detail-item {
            display: flex;
            flex-direction: column;
            gap: 5px;
        }
        .detail-item.full-width {
            grid-column: 1 / -1;
        }
        .detail-label {
            font-size: 12px;
            font-weight: 600;
            color: #6b7280;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        .detail-value {
            background: #f3f4f6;
            padding: 8px 12px;
            border-radius: 6px;
            font-family: 'Courier New', monospace;
            font-size: 13px;
            color: #1f2937;
            word-break: break-all;
        }
        .code-block {
            background: #1f2937;
            color: #e5e7eb;
            padding: 15px;
            border-radius: 6px;
            font-family: 'Courier New', monospace;
            font-size: 13px;
            line-height: 1.5;
            overflow-x: auto;
            margin: 0;
        }
        .method-badge {
            display: inline-block;
            padding: 4px 12px;
            border-radius: 4px;
            font-size: 12px;
            font-weight: 600;
            text-transform: uppercase;
        }
        .method-get { background: #dbeafe; color: #1e40af; }
        .method-post { background: #d1fae5; color: #065f46; }
        .method-put { background: #fef3c7; color: #92400e; }
        .method-patch { background: #e0e7ff; color: #3730a3; }
        .method-delete { background: #fee2e2; color: #991b1b; }
        .status-badge {
            display: inline-block;
            padding: 6px 14px;
            border-radius: 6px;
            font-size: 14px;
            font-weight: 600;
        }
        .status-badge.status-success {
            background: #d1fae5;
            color: #065f46;
        }
        .status-badge.status-error {
            background: #fee2e2;
            color: #991b1b;
        }
    </style>
    <script>
        function toggleDetails(testId) {
            const wrapper = document.getElementById(testId).parentElement;
            wrapper.classList.toggle('expanded');
        }
    </script>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>%s</h1>
            <div class="meta">Generated: %s | Total Duration: %.2fs</div>
        </div>
""".formatted(
                data.getTestSuiteName(),
                data.getTestSuiteName(),
                data.getExecutionTime().format(DATE_FORMATTER),
                data.getTotalDurationMs() / 1000.0
        );
    }

    private String generateSummarySection(ReportData data) {
        return """
        <div class="summary">
            <div class="summary-card">
                <div class="label">Total Tests</div>
                <div class="value">%d</div>
            </div>
            <div class="summary-card success">
                <div class="label">Passed</div>
                <div class="value" style="color: #10b981;">%d</div>
            </div>
            <div class="summary-card failed">
                <div class="label">Failed</div>
                <div class="value" style="color: #ef4444;">%d</div>
            </div>
            <div class="summary-card performance">
                <div class="label">Avg Duration</div>
                <div class="value" style="color: #f59e0b;">%.0fms</div>
            </div>
        </div>

        <div class="chart-section">
            <h2>Test Results Overview</h2>
            <div class="progress-bar">
                <div class="progress-passed" style="width: %.1f%%%%;">
                    %.1f%%%% Passed
                </div>
                <div class="progress-failed" style="width: %.1f%%%%;">
                    %.1f%%%% Failed
                </div>
            </div>
        </div>
""".formatted(
                data.getTotalTests(),
                data.getPassedTests(),
                data.getFailedTests(),
                data.getAvgDurationMs(),
                data.getPassRate(),
                data.getPassRate(),
                data.getFailRate(),
                data.getFailRate()
        );
    }

    private String generatePerformanceChartSection(ReportData data) {
        StringBuilder chart = new StringBuilder();
        chart.append("""
        <div class="chart-section">
            <h2>Performance Metrics</h2>
            <div class="summary">
                <div class="summary-card">
                    <div class="label">Min Duration</div>
                    <div class="value" style="font-size: 24px;">%dms</div>
                </div>
                <div class="summary-card">
                    <div class="label">Max Duration</div>
                    <div class="value" style="font-size: 24px;">%dms</div>
                </div>
                <div class="summary-card">
                    <div class="label">Avg Duration</div>
                    <div class="value" style="font-size: 24px;">%.0fms</div>
                </div>
            </div>
        </div>
""".formatted(
                data.getMinDurationMs(),
                data.getMaxDurationMs(),
                data.getAvgDurationMs()
        ));
        return chart.toString();
    }

    private String generateTestDetailsSection(ReportData data) {
        StringBuilder details = new StringBuilder();
        details.append("        <div class=\"test-details\">\n");
        details.append("            <h2>Test Details</h2>\n");

        int stepNumber = 1;
        for (ExecutionResult result : data.getResults()) {
            String statusClass = result.isSuccess() ? "pass" : "fail";
            String statusSymbol = result.isSuccess() ? "✓" : "✗";

            PerformanceMetrics perf = result.getPerformanceMetrics();
            String perfBadge = "";
            if (perf != null) {
                String perfStatus = perf.getPerformanceStatus(1000); // 1000ms as default threshold
                perfBadge = switch (perfStatus) {
                    case "EXCELLENT" -> "<span class=\"performance-badge badge-excellent\">Excellent</span>";
                    case "GOOD" -> "<span class=\"performance-badge badge-good\">Good</span>";
                    case "ACCEPTABLE" -> "<span class=\"performance-badge badge-acceptable\">Acceptable</span>";
                    default -> "<span class=\"performance-badge badge-slow\">Slow</span>";
                };
            }

            details.append("""
                    <div class="test-item-wrapper">
                        <div class="test-item" onclick="toggleDetails('test-%d')">
                            <span class="test-status %s">%s</span>
                            <div class="test-info">
                                <div class="test-name">%d. %s</div>
                                <div class="test-meta">Status: %d | Method: %s</div>
""".formatted(
                    stepNumber,
                    statusClass,
                    statusSymbol,
                    stepNumber,
                    result.getStepName(),
                    result.getStatusCode() != null ? result.getStatusCode() : 0,
                    result.getRequestDetails() != null ? result.getRequestDetails().getMethod() : "N/A"
            ));

            if (!result.isSuccess() && result.getErrorMessage() != null) {
                details.append("                                <div class=\"error-message\">")
                        .append(escapeHtml(result.getErrorMessage()))
                        .append("</div>\n");
            }

            details.append("                            </div>\n");
            details.append("                            <div class=\"test-performance\">\n");
            details.append("                                <div class=\"duration\">")
                    .append(result.getDurationMs())
                    .append("ms</div>\n");
            if (!perfBadge.isEmpty()) {
                details.append("                                ").append(perfBadge).append("\n");
            }
            details.append("                                <span class=\"expand-icon\">▼</span>\n");
            details.append("                            </div>\n");
            details.append("                        </div>\n");

            // Detailed information section (collapsible)
            details.append("                        <div class=\"test-details-content\" id=\"test-")
                    .append(stepNumber)
                    .append("\">\n");

            // Request Details
            if (result.getRequestDetails() != null) {
                details.append(generateRequestDetailsHtml(result.getRequestDetails()));
            }

            // Response Details
            if (result.getResponse() != null || result.getStatusCode() != null) {
                details.append(generateResponseDetailsHtml(result));
            }

            // Extracted Variables
            if (result.getExtractedVariables() != null && !result.getExtractedVariables().isEmpty()) {
                details.append(generateExtractedVariablesHtml(result.getExtractedVariables()));
            }

            details.append("                        </div>\n");
            details.append("                    </div>\n");

            stepNumber++;
        }

        details.append("        </div>\n");
        return details.toString();
    }

    private String generateRequestDetailsHtml(ExecutionResult.RequestDetails request) {
        StringBuilder html = new StringBuilder();
        html.append("                            <div class=\"detail-section\">\n");
        html.append("                                <h3>📤 Request</h3>\n");
        html.append("                                <div class=\"detail-grid\">\n");

        // URL
        if (request.getUrl() != null) {
            html.append("                                    <div class=\"detail-item\">\n");
            html.append("                                        <span class=\"detail-label\">URL:</span>\n");
            html.append("                                        <code class=\"detail-value\">")
                    .append(escapeHtml(request.getUrl()))
                    .append("</code>\n");
            html.append("                                    </div>\n");
        }

        // Method
        if (request.getMethod() != null) {
            html.append("                                    <div class=\"detail-item\">\n");
            html.append("                                        <span class=\"detail-label\">Method:</span>\n");
            html.append("                                        <span class=\"method-badge method-")
                    .append(request.getMethod().toLowerCase())
                    .append("\">")
                    .append(request.getMethod())
                    .append("</span>\n");
            html.append("                                    </div>\n");
        }

        // Headers
        if (request.getHeaders() != null && !request.getHeaders().isEmpty()) {
            html.append("                                    <div class=\"detail-item full-width\">\n");
            html.append("                                        <span class=\"detail-label\">Headers:</span>\n");
            html.append("                                        <pre class=\"code-block\">");
            html.append(formatJson(request.getHeaders()));
            html.append("</pre>\n");
            html.append("                                    </div>\n");
        }

        // Cookies
        if (request.getCookies() != null && !request.getCookies().isEmpty()) {
            html.append("                                    <div class=\"detail-item full-width\">\n");
            html.append("                                        <span class=\"detail-label\">Cookies:</span>\n");
            html.append("                                        <pre class=\"code-block\">");
            html.append(formatJson(request.getCookies()));
            html.append("</pre>\n");
            html.append("                                    </div>\n");
        }

        // Body
        if (request.getBody() != null) {
            html.append("                                    <div class=\"detail-item full-width\">\n");
            html.append("                                        <span class=\"detail-label\">Body:</span>\n");
            html.append("                                        <pre class=\"code-block\">");
            html.append(formatJson(request.getBody()));
            html.append("</pre>\n");
            html.append("                                    </div>\n");
        }

        html.append("                                </div>\n");
        html.append("                            </div>\n");
        return html.toString();
    }

    private String generateResponseDetailsHtml(ExecutionResult result) {
        StringBuilder html = new StringBuilder();
        html.append("                            <div class=\"detail-section\">\n");
        html.append("                                <h3>📥 Response</h3>\n");
        html.append("                                <div class=\"detail-grid\">\n");

        // Status Code
        if (result.getStatusCode() != null) {
            String statusClass = result.getStatusCode() >= 200 && result.getStatusCode() < 300 ? "success" : "error";
            html.append("                                    <div class=\"detail-item\">\n");
            html.append("                                        <span class=\"detail-label\">Status Code:</span>\n");
            html.append("                                        <span class=\"status-badge status-")
                    .append(statusClass)
                    .append("\">")
                    .append(result.getStatusCode())
                    .append("</span>\n");
            html.append("                                    </div>\n");
        }

        // Response Body
        if (result.getResponse() != null) {
            html.append("                                    <div class=\"detail-item full-width\">\n");
            html.append("                                        <span class=\"detail-label\">Body:</span>\n");
            html.append("                                        <pre class=\"code-block\">");
            html.append(formatJson(result.getResponse()));
            html.append("</pre>\n");
            html.append("                                    </div>\n");
        }

        html.append("                                </div>\n");
        html.append("                            </div>\n");
        return html.toString();
    }

    private String generateExtractedVariablesHtml(Map<String, Object> variables) {
        StringBuilder html = new StringBuilder();
        html.append("                            <div class=\"detail-section\">\n");
        html.append("                                <h3>🔧 Extracted Variables</h3>\n");
        html.append("                                <div class=\"detail-grid\">\n");

        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            html.append("                                    <div class=\"detail-item full-width\">\n");
            html.append("                                        <span class=\"detail-label\">")
                    .append(escapeHtml(entry.getKey()))
                    .append(":</span>\n");
            html.append("                                        <code class=\"detail-value\">")
                    .append(escapeHtml(String.valueOf(entry.getValue())))
                    .append("</code>\n");
            html.append("                                    </div>\n");
        }

        html.append("                                </div>\n");
        html.append("                            </div>\n");
        return html.toString();
    }

    private String formatJson(Object obj) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            String json = mapper.writeValueAsString(obj);
            return escapeHtml(json);
        } catch (Exception e) {
            return escapeHtml(String.valueOf(obj));
        }
    }

    private String generateHtmlFooter() {
        return """
        <div class="footer">
            <p>Generated by ArgusOmni Test Orchestrator</p>
        </div>
    </div>
</body>
</html>
""";
    }

    private String generateFileName(String testSuiteName) {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String sanitizedName = testSuiteName.replaceAll("[^a-zA-Z0-9-_]", "_");
        return sanitizedName + "_" + timestamp + ".html";
    }

    private String writeToFile(String content, String outputDir, String fileName) throws IOException {
        File directory = new File(outputDir);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        File reportFile = new File(directory, fileName);
        try (FileWriter writer = new FileWriter(reportFile)) {
            writer.write(content);
        }

        return reportFile.getAbsolutePath();
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
