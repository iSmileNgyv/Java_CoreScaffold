package com.ismile.core.chronovcscli.commands;

import com.ismile.core.chronovcscli.core.diff.DiffEngine;
import com.ismile.core.chronovcscli.core.diff.DiffResult;
import com.ismile.core.chronovcscli.core.diff.FileDiff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;

@Command(
        name = "diff",
        description = "Show changes between commits, commit and working tree, etc"
)
@Component
@RequiredArgsConstructor
public class DiffCommand implements Runnable {
    private final DiffEngine diffEngine;

    @Option(names = {"--staged", "--cached"}, description = "Show diff between staged and HEAD")
    private boolean staged;

    @Override
    public void run() {
        try {
            File root = new File(System.getProperty("user.dir"));

            DiffResult result = staged
                ? diffEngine.diffStagedVsHead(root)
                : diffEngine.diffWorkingVsStaged(root);

            if (result.getFileDiffs().isEmpty()) {
                System.out.println("No changes");
                return;
            }

            for (FileDiff fileDiff : result.getFileDiffs()) {
                System.out.println("\n" + getColoredChangeType(fileDiff.getChangeType()) + " " + fileDiff.getFilePath());
                for (String hunk : fileDiff.getHunks()) {
                    System.out.println(getColoredHunk(hunk));
                }
            }
        } catch (Exception e) {
            System.err.println("Diff failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getColoredChangeType(FileDiff.ChangeType type) {
        return switch (type) {
            case ADDED -> "\u001B[32m[ADDED]\u001B[0m";
            case MODIFIED -> "\u001B[33m[MODIFIED]\u001B[0m";
            case DELETED -> "\u001B[31m[DELETED]\u001B[0m";
        };
    }

    private String getColoredHunk(String hunk) {
        if (hunk.startsWith("+ ")) {
            return "\u001B[32m" + hunk + "\u001B[0m";
        } else if (hunk.startsWith("- ")) {
            return "\u001B[31m" + hunk + "\u001B[0m";
        }
        return hunk;
    }
}
