package com.ismile.core.chronovcscli.commands;

import com.ismile.core.chronovcscli.core.log.LogEngine;
import com.ismile.core.chronovcscli.core.log.LogEntry;
import com.ismile.core.chronovcscli.core.log.LogOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.List;

/**
 * Log command - display commit history
 *
 * Usage:
 *   chronovcs log                    - Show full commit history
 *   chronovcs log -n 10              - Show last 10 commits
 *   chronovcs log --oneline          - Compact one-line format
 *   chronovcs log --short            - Short format
 *   chronovcs log <file>             - Show commits for specific file
 *   chronovcs log --stat             - Show file changes
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Command(
        name = "log",
        description = "Show commit history",
        mixinStandardHelpOptions = true
)
public class LogCommand implements Runnable {

    private final LogEngine logEngine;

    @Option(names = {"-n", "--max-count"}, description = "Limit number of commits to display")
    private Integer limit;

    @Option(names = {"--oneline"}, description = "Show each commit on a single line")
    private boolean oneline;

    @Option(names = {"--short"}, description = "Show short format (hash, date, message)")
    private boolean shortFormat;

    @Option(names = {"--stat"}, description = "Show file changes for each commit")
    private boolean showStat;

    @Option(names = {"--graph"}, description = "Show commit graph (ASCII visualization)")
    private boolean showGraph;

    @Parameters(index = "0", arity = "0..1", description = "File path to show history for")
    private String filePath;

    @Override
    public void run() {
        File projectRoot = new File(System.getProperty("user.dir"));

        // Check if .vcs directory exists
        if (!new File(projectRoot, ".vcs").exists()) {
            System.err.println("Error: Not a ChronoVCS repository");
            System.err.println("Run 'chronovcs init' first");
            return;
        }

        try {
            // Determine format
            LogOptions.LogFormat format = LogOptions.LogFormat.FULL;
            if (oneline) {
                format = LogOptions.LogFormat.ONELINE;
            } else if (shortFormat) {
                format = LogOptions.LogFormat.SHORT;
            }

            // Build options
            LogOptions options = LogOptions.builder()
                    .limit(limit)
                    .format(format)
                    .showGraph(showGraph)
                    .filePath(filePath)
                    .showFiles(showStat)
                    .build();

            // Get log entries
            List<LogEntry> entries = logEngine.getLog(projectRoot, options);

            if (entries.isEmpty()) {
                System.out.println("No commits yet.");
                return;
            }

            // Format and display
            if (showGraph) {
                displayWithGraph(entries, format);
            } else if (showStat) {
                displayWithStat(entries, format);
            } else {
                String output = logEngine.formatLog(entries, format);
                System.out.print(output);
            }

        } catch (Exception e) {
            log.error("Log command failed", e);
            System.err.println("Error: " + e.getMessage());
        }
    }

    private void displayWithGraph(List<LogEntry> entries, LogOptions.LogFormat format) {
        for (int i = 0; i < entries.size(); i++) {
            LogEntry entry = entries.get(i);

            // Simple graph visualization
            if (entry.isMergeCommit()) {
                System.out.print("*   ");
            } else {
                System.out.print("* ");
            }

            // Format based on selected format
            String formattedEntry = formatSingleEntry(entry, format);
            System.out.println(formattedEntry);

            // Show graph lines
            if (i < entries.size() - 1) {
                LogEntry next = entries.get(i + 1);
                if (entry.isMergeCommit()) {
                    System.out.println("|\\  ");
                } else {
                    System.out.println("|   ");
                }
            }
        }
    }

    private void displayWithStat(List<LogEntry> entries, LogOptions.LogFormat format) {
        for (LogEntry entry : entries) {
            // Print commit info
            String formattedEntry = formatSingleEntry(entry, LogOptions.LogFormat.FULL);
            System.out.println(formattedEntry);

            // Print file stats
            if (entry.getFiles() != null && !entry.getFiles().isEmpty()) {
                System.out.println("Files changed: " + entry.getFiles().size());
                for (String file : entry.getFiles().keySet()) {
                    System.out.println("    " + file);
                }
                System.out.println();
            }
        }
    }

    private String formatSingleEntry(LogEntry entry, LogOptions.LogFormat format) {
        return switch (format) {
            case ONELINE -> formatOneLine(entry);
            case SHORT -> formatShort(entry);
            case FULL -> formatFull(entry);
        };
    }

    private String formatFull(LogEntry entry) {
        StringBuilder sb = new StringBuilder();

        sb.append("commit ").append(entry.getCommitId());

        if (entry.isHead() || !entry.getBranches().isEmpty()) {
            sb.append(" (");
            if (entry.isHead()) sb.append("HEAD");
            if (entry.isHead() && !entry.getBranches().isEmpty()) sb.append(" -> ");
            sb.append(String.join(", ", entry.getBranches()));
            sb.append(")");
        }

        if (entry.isMergeCommit()) {
            sb.append("\nMerge: ")
              .append(entry.getParentId().substring(0, 8))
              .append(" ")
              .append(entry.getMergeParentId().substring(0, 8));
        }

        sb.append("\nDate:   ").append(entry.getTimestamp());
        sb.append("\n\n    ").append(entry.getMessage());

        return sb.toString();
    }

    private String formatShort(LogEntry entry) {
        return entry.getShortCommitId() + " " +
               (entry.isHead() ? "(HEAD) " : "") +
               entry.getTimestamp() + " " +
               entry.getMessage();
    }

    private String formatOneLine(LogEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append(entry.getShortCommitId()).append(" ");

        if (entry.isHead() || !entry.getBranches().isEmpty()) {
            sb.append("(");
            if (entry.isHead()) sb.append("HEAD");
            if (entry.isHead() && !entry.getBranches().isEmpty()) sb.append(" -> ");
            sb.append(String.join(", ", entry.getBranches()));
            sb.append(") ");
        }

        sb.append(entry.getMessage());
        return sb.toString();
    }
}
