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
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
            background: linear-gradient(135deg, #0f172a 0%%, #1e293b 100%%);
            min-height: 100vh;
            padding: 40px 20px;
            color: #e2e8f0;
        }

        .container {
            max-width: 1400px;
            margin: 0 auto;
        }

        .header {
            background: linear-gradient(135deg, #8b5cf6 0%%, #ec4899 100%%);
            border-radius: 20px;
            padding: 40px;
            margin-bottom: 30px;
            box-shadow: 0 20px 60px rgba(139, 92, 246, 0.3);
            position: relative;
            overflow: hidden;
        }

        .header::before {
            content: '';
            position: absolute;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background: url('data:image/svg+xml,<svg width="100" height="100" xmlns="http://www.w3.org/2000/svg"><defs><pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse"><path d="M 40 0 L 0 0 0 40" fill="none" stroke="rgba(255,255,255,0.1)" stroke-width="1"/></pattern></defs><rect width="100" height="100" fill="url(%%23grid)"/></svg>');
            opacity: 0.3;
        }

        .header-content { position: relative; z-index: 1; }
        .header h1 {
            font-size: 42px;
            margin-bottom: 10px;
            font-weight: 800;
            color: white;
            text-shadow: 0 2px 10px rgba(0,0,0,0.2);
        }
        .header .meta {
            opacity: 0.95;
            font-size: 16px;
            color: rgba(255,255,255,0.9);
        }

        .stats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
            gap: 24px;
            margin-bottom: 30px;
        }

        .stat-card {
            background: rgba(30, 41, 59, 0.8);
            backdrop-filter: blur(10px);
            border-radius: 16px;
            padding: 28px;
            border: 1px solid rgba(148, 163, 184, 0.1);
            transition: all 0.3s ease;
            position: relative;
            overflow: hidden;
        }

        .stat-card::before {
            content: '';
            position: absolute;
            top: 0;
            left: 0;
            right: 0;
            height: 4px;
            background: linear-gradient(90deg, var(--accent-color), transparent);
        }

        .stat-card:hover {
            transform: translateY(-4px);
            box-shadow: 0 12px 40px rgba(0,0,0,0.3);
            border-color: rgba(148, 163, 184, 0.3);
        }

        .stat-card.total { --accent-color: #3b82f6; }
        .stat-card.success { --accent-color: #10b981; }
        .stat-card.failed { --accent-color: #ef4444; }
        .stat-card.duration { --accent-color: #f59e0b; }

        .stat-icon {
            width: 48px;
            height: 48px;
            border-radius: 12px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 24px;
            margin-bottom: 16px;
        }

        .stat-card.total .stat-icon { background: rgba(59, 130, 246, 0.1); }
        .stat-card.success .stat-icon { background: rgba(16, 185, 129, 0.1); }
        .stat-card.failed .stat-icon { background: rgba(239, 68, 68, 0.1); }
        .stat-card.duration .stat-icon { background: rgba(245, 158, 11, 0.1); }

        .stat-value {
            font-size: 48px;
            font-weight: 800;
            line-height: 1;
            margin-bottom: 8px;
            background: linear-gradient(135deg, var(--accent-color), rgba(255,255,255,0.8));
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
        }

        .stat-label {
            color: #94a3b8;
            font-size: 14px;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 1px;
        }

        .chart-card {
            background: rgba(30, 41, 59, 0.8);
            backdrop-filter: blur(10px);
            border-radius: 16px;
            padding: 32px;
            margin-bottom: 30px;
            border: 1px solid rgba(148, 163, 184, 0.1);
        }

        .chart-card h2 {
            font-size: 24px;
            margin-bottom: 24px;
            color: #e2e8f0;
            font-weight: 700;
        }

        .progress-container {
            background: rgba(15, 23, 42, 0.5);
            border-radius: 12px;
            padding: 4px;
            margin-bottom: 20px;
        }

        .progress-bar {
            height: 40px;
            background: transparent;
            border-radius: 8px;
            overflow: hidden;
            display: flex;
        }

        .progress-segment {
            display: flex;
            align-items: center;
            justify-content: center;
            color: white;
            font-weight: 700;
            font-size: 14px;
            transition: all 0.5s ease;
        }

        .progress-passed {
            background: linear-gradient(135deg, #10b981, #059669);
        }

        .progress-failed {
            background: linear-gradient(135deg, #ef4444, #dc2626);
        }

        .test-list {
            background: rgba(30, 41, 59, 0.8);
            backdrop-filter: blur(10px);
            border-radius: 16px;
            border: 1px solid rgba(148, 163, 184, 0.1);
            overflow: hidden;
        }

        .test-list-header {
            padding: 24px 32px;
            background: rgba(15, 23, 42, 0.5);
            border-bottom: 1px solid rgba(148, 163, 184, 0.1);
        }

        .test-list-header h2 {
            font-size: 24px;
            color: #e2e8f0;
            font-weight: 700;
        }

        .test-item-wrapper {
            border-bottom: 1px solid rgba(148, 163, 184, 0.1);
            transition: all 0.3s ease;
        }

        .test-item-wrapper:last-child { border-bottom: none; }

        .test-item-wrapper:hover {
            background: rgba(51, 65, 85, 0.3);
        }

        .test-item {
            padding: 24px 32px;
            display: flex;
            align-items: center;
            gap: 20px;
            cursor: pointer;
        }

        .test-status {
            width: 40px;
            height: 40px;
            border-radius: 50%%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: bold;
            font-size: 20px;
            flex-shrink: 0;
        }

        .test-status.pass {
            background: linear-gradient(135deg, #10b981, #059669);
            box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);
        }

        .test-status.fail {
            background: linear-gradient(135deg, #ef4444, #dc2626);
            box-shadow: 0 4px 12px rgba(239, 68, 68, 0.3);
        }

        .test-info { flex: 1; }

        .test-name {
            font-weight: 700;
            font-size: 16px;
            color: #e2e8f0;
            margin-bottom: 6px;
        }

        .test-meta {
            font-size: 14px;
            color: #94a3b8;
            display: flex;
            gap: 16px;
            align-items: center;
        }

        .test-duration-box {
            display: flex;
            align-items: center;
            gap: 12px;
            flex-shrink: 0;
        }

        .duration {
            font-size: 28px;
            font-weight: 800;
            color: #f59e0b;
        }

        .duration-label {
            font-size: 12px;
            color: #94a3b8;
            text-transform: uppercase;
        }

        .expand-btn {
            width: 32px;
            height: 32px;
            border-radius: 8px;
            background: rgba(139, 92, 246, 0.1);
            display: flex;
            align-items: center;
            justify-content: center;
            transition: all 0.3s ease;
            flex-shrink: 0;
        }

        .expand-btn:hover {
            background: rgba(139, 92, 246, 0.2);
        }

        .expand-icon {
            color: #8b5cf6;
            font-size: 16px;
            transition: transform 0.3s ease;
        }

        .test-item-wrapper.expanded .expand-icon {
            transform: rotate(180deg);
        }

        .test-details-content {
            max-height: 0;
            overflow: hidden;
            transition: max-height 0.4s ease;
            background: rgba(15, 23, 42, 0.5);
            width: 100%%;
            max-width: 100%%;
        }

        .test-item-wrapper.expanded .test-details-content {
            max-height: 8000px;
            padding: 24px 32px;
            overflow-x: hidden;
            overflow-y: auto;
        }

        .detail-card {
            background: rgba(30, 41, 59, 0.6);
            border-radius: 12px;
            padding: 24px;
            margin-bottom: 20px;
            border: 1px solid rgba(148, 163, 184, 0.1);
            max-width: 100%%;
            overflow: hidden;
        }

        .detail-card:last-child { margin-bottom: 0; }

        .detail-card h3 {
            font-size: 18px;
            font-weight: 700;
            color: #e2e8f0;
            margin-bottom: 20px;
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .detail-grid {
            display: grid;
            gap: 16px;
            width: 100%%;
            max-width: 100%%;
        }

        .detail-item {
            display: flex;
            flex-direction: column;
            gap: 8px;
            min-width: 0;
            max-width: 100%%;
        }

        .detail-label {
            font-size: 11px;
            font-weight: 700;
            color: #64748b;
            text-transform: uppercase;
            letter-spacing: 1px;
        }

        .detail-value {
            background: rgba(15, 23, 42, 0.8);
            padding: 12px 16px;
            border-radius: 8px;
            font-family: 'JetBrains Mono', 'Courier New', monospace;
            font-size: 13px;
            color: #cbd5e1;
            border: 1px solid rgba(148, 163, 184, 0.1);
            max-height: 300px;
            max-width: 100%%;
            overflow: auto;
            word-wrap: break-word;
            word-break: break-all;
            white-space: pre-wrap;
        }

        .code-block {
            background: #0f172a;
            padding: 20px;
            border-radius: 12px;
            font-family: 'JetBrains Mono', 'Courier New', monospace;
            font-size: 13px;
            line-height: 1.6;
            color: #e2e8f0;
            border: 1px solid rgba(148, 163, 184, 0.1);
            max-height: 500px;
            max-width: 100%%;
            overflow: auto;
            position: relative;
            word-wrap: break-word;
            white-space: pre-wrap;
        }

        .code-block::-webkit-scrollbar,
        .detail-value::-webkit-scrollbar {
            width: 10px;
            height: 10px;
        }

        .code-block::-webkit-scrollbar-track,
        .detail-value::-webkit-scrollbar-track {
            background: rgba(15, 23, 42, 0.5);
            border-radius: 5px;
        }

        .code-block::-webkit-scrollbar-thumb,
        .detail-value::-webkit-scrollbar-thumb {
            background: rgba(139, 92, 246, 0.5);
            border-radius: 5px;
        }

        .code-block::-webkit-scrollbar-thumb:hover,
        .detail-value::-webkit-scrollbar-thumb:hover {
            background: rgba(139, 92, 246, 0.7);
        }

        .method-badge {
            padding: 6px 12px;
            border-radius: 6px;
            font-size: 12px;
            font-weight: 700;
            text-transform: uppercase;
        }

        .method-get { background: rgba(59, 130, 246, 0.2); color: #60a5fa; }
        .method-post { background: rgba(16, 185, 129, 0.2); color: #34d399; }
        .method-put { background: rgba(245, 158, 11, 0.2); color: #fbbf24; }
        .method-patch { background: rgba(139, 92, 246, 0.2); color: #a78bfa; }
        .method-delete { background: rgba(239, 68, 68, 0.2); color: #f87171; }

        .status-badge {
            padding: 8px 16px;
            border-radius: 8px;
            font-size: 14px;
            font-weight: 700;
        }

        .status-badge.status-success {
            background: rgba(16, 185, 129, 0.2);
            color: #34d399;
        }

        .status-badge.status-error {
            background: rgba(239, 68, 68, 0.2);
            color: #f87171;
        }

        .error-message {
            background: rgba(239, 68, 68, 0.1);
            border-left: 4px solid #ef4444;
            padding: 16px;
            border-radius: 8px;
            margin-top: 12px;
            font-family: 'JetBrains Mono', monospace;
            font-size: 13px;
            color: #fca5a5;
        }

        .footer {
            text-align: center;
            padding: 40px;
            color: #64748b;
            font-size: 14px;
            margin-top: 40px;
        }

        @keyframes fadeIn {
            from { opacity: 0; transform: translateY(20px); }
            to { opacity: 1; transform: translateY(0); }
        }

        .stat-card, .chart-card, .test-list {
            animation: fadeIn 0.6s ease forwards;
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
            <div class="header-content">
                <h1>%s</h1>
                <div class="meta">📅 %s | ⏱️  Total Duration: %.2fs</div>
            </div>
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
        <div class="stats-grid">
            <div class="stat-card total">
                <div class="stat-icon">📊</div>
                <div class="stat-value">%d</div>
                <div class="stat-label">Total Tests</div>
            </div>
            <div class="stat-card success">
                <div class="stat-icon">✓</div>
                <div class="stat-value">%d</div>
                <div class="stat-label">Passed</div>
            </div>
            <div class="stat-card failed">
                <div class="stat-icon">✗</div>
                <div class="stat-value">%d</div>
                <div class="stat-label">Failed</div>
            </div>
            <div class="stat-card duration">
                <div class="stat-icon">⚡</div>
                <div class="stat-value">%.0f</div>
                <div class="stat-label">Avg Duration (ms)</div>
            </div>
        </div>

        <div class="chart-card">
            <h2>📈 Test Results Overview</h2>
            <div class="progress-container">
                <div class="progress-bar">
                    <div class="progress-segment progress-passed" style="width: %.1f%%%%;">
                        %.1f%%%% Passed
                    </div>
                    <div class="progress-segment progress-failed" style="width: %.1f%%%%;">
                        %.1f%%%% Failed
                    </div>
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
        <div class="chart-card">
            <h2>⚡ Performance Metrics</h2>
            <div class="stats-grid">
                <div class="stat-card total">
                    <div class="stat-icon">🔽</div>
                    <div class="stat-value" style="font-size: 36px;">%d</div>
                    <div class="stat-label">Min Duration (ms)</div>
                </div>
                <div class="stat-card failed">
                    <div class="stat-icon">🔼</div>
                    <div class="stat-value" style="font-size: 36px;">%d</div>
                    <div class="stat-label">Max Duration (ms)</div>
                </div>
                <div class="stat-card duration">
                    <div class="stat-icon">📊</div>
                    <div class="stat-value" style="font-size: 36px;">%.0f</div>
                    <div class="stat-label">Avg Duration (ms)</div>
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
        details.append("        <div class=\"test-list\">\n");
        details.append("            <div class=\"test-list-header\">\n");
        details.append("                <h2>🔍 Test Details</h2>\n");
        details.append("            </div>\n");

        int stepNumber = 1;
        for (ExecutionResult result : data.getResults()) {
            String statusClass = result.isSuccess() ? "pass" : "fail";
            String statusSymbol = result.isSuccess() ? "✓" : "✗";

            details.append("""
                    <div class="test-item-wrapper">
                        <div class="test-item" onclick="toggleDetails('test-%d')">
                            <span class="test-status %s">%s</span>
                            <div class="test-info">
                                <div class="test-name">%d. %s</div>
                                <div class="test-meta">
                                    <span>Status: %d</span>
                                    <span>•</span>
                                    <span>Method: %s</span>
                                </div>
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
            details.append("                            <div class=\"test-duration-box\">\n");
            details.append("                                <div>\n");
            details.append("                                    <div class=\"duration\">")
                    .append(result.getDurationMs())
                    .append("ms</div>\n");
            details.append("                                    <div class=\"duration-label\">Duration</div>\n");
            details.append("                                </div>\n");
            details.append("                            </div>\n");
            details.append("                            <div class=\"expand-btn\">\n");
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
        html.append("                            <div class=\"detail-card\">\n");
        html.append("                                <h3>📤 Request</h3>\n");
        html.append("                                <div class=\"detail-grid\">\n");

        // Method and URL in same row
        if (request.getMethod() != null) {
            html.append("                                    <div class=\"detail-item\">\n");
            html.append("                                        <span class=\"detail-label\">Method</span>\n");
            html.append("                                        <span class=\"method-badge method-")
                    .append(request.getMethod().toLowerCase())
                    .append("\">")
                    .append(request.getMethod())
                    .append("</span>\n");
            html.append("                                    </div>\n");
        }

        if (request.getUrl() != null) {
            html.append("                                    <div class=\"detail-item\">\n");
            html.append("                                        <span class=\"detail-label\">URL</span>\n");
            html.append("                                        <code class=\"detail-value\">")
                    .append(escapeHtml(request.getUrl()))
                    .append("</code>\n");
            html.append("                                    </div>\n");
        }

        // Headers
        if (request.getHeaders() != null && !request.getHeaders().isEmpty()) {
            html.append("                                    <div class=\"detail-item\">\n");
            html.append("                                        <span class=\"detail-label\">Headers</span>\n");
            html.append("                                        <pre class=\"code-block\">");
            html.append(formatJson(request.getHeaders()));
            html.append("</pre>\n");
            html.append("                                    </div>\n");
        }

        // Cookies
        if (request.getCookies() != null && !request.getCookies().isEmpty()) {
            html.append("                                    <div class=\"detail-item\">\n");
            html.append("                                        <span class=\"detail-label\">Cookies</span>\n");
            html.append("                                        <pre class=\"code-block\">");
            html.append(formatJson(request.getCookies()));
            html.append("</pre>\n");
            html.append("                                    </div>\n");
        }

        // Body
        if (request.getBody() != null) {
            html.append("                                    <div class=\"detail-item\">\n");
            html.append("                                        <span class=\"detail-label\">Body</span>\n");
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
        html.append("                            <div class=\"detail-card\">\n");
        html.append("                                <h3>📥 Response</h3>\n");
        html.append("                                <div class=\"detail-grid\">\n");

        // Status Code
        if (result.getStatusCode() != null) {
            String statusClass = result.getStatusCode() >= 200 && result.getStatusCode() < 300 ? "success" : "error";
            html.append("                                    <div class=\"detail-item\">\n");
            html.append("                                        <span class=\"detail-label\">Status Code</span>\n");
            html.append("                                        <span class=\"status-badge status-")
                    .append(statusClass)
                    .append("\">")
                    .append(result.getStatusCode())
                    .append("</span>\n");
            html.append("                                    </div>\n");
        }

        // Response Body
        if (result.getResponse() != null) {
            html.append("                                    <div class=\"detail-item\">\n");
            html.append("                                        <span class=\"detail-label\">Body</span>\n");
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
        html.append("                            <div class=\"detail-card\">\n");
        html.append("                                <h3>🔧 Extracted Variables</h3>\n");
        html.append("                                <div class=\"detail-grid\">\n");

        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            html.append("                                    <div class=\"detail-item\">\n");
            html.append("                                        <span class=\"detail-label\">")
                    .append(escapeHtml(entry.getKey()))
                    .append("</span>\n");
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
            <p>⚡ Powered by ArgusOmni Test Orchestrator</p>
            <p style="margin-top: 8px; font-size: 12px;">Modern API Testing & Automation Platform</p>
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
