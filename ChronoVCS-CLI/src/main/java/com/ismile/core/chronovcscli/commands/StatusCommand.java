package com.ismile.core.chronovcscli.commands;

import com.ismile.core.chronovcscli.core.status.StatusEngine;
import com.ismile.core.chronovcscli.core.status.StatusResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.io.File;
import java.nio.file.Files;

@Command(
        name = "status",
        description = "Show changes in working directory"
)
@Component
@RequiredArgsConstructor
public class StatusCommand implements Runnable{
    private final StatusEngine statusEngine;
    @Override
    public void run() {
        try {
            File root = new File(System.getProperty("user.dir"));

            // Display current branch
            displayCurrentBranch(root);

            StatusResult result = statusEngine.getStatus(root);
            System.out.println("Untracked:");
            result.getUntracked().forEach(f -> System.out.println("  " + f));

            System.out.println("\nModified:");
            result.getModified().forEach(f -> System.out.println("  " + f));

            System.out.println("\nDeleted:");
            result.getDeleted().forEach(f -> System.out.println("  " + f));
        } catch(Exception e) {
            System.err.println("Status failed: " + e.getMessage());
        }
    }

    private void displayCurrentBranch(File projectRoot) {
        try {
            File headFile = new File(projectRoot, ".vcs/HEAD");
            if (!headFile.exists()) {
                System.out.println("On branch (unknown)");
                System.out.println();
                return;
            }

            String headContent = Files.readString(headFile.toPath()).trim();

            if (headContent.startsWith("ref: refs/heads/")) {
                String branchName = headContent.substring("ref: refs/heads/".length());
                System.out.println("On branch " + branchName);
            } else {
                // Detached HEAD
                String shortCommit = headContent.length() >= 7
                    ? headContent.substring(0, 7)
                    : headContent;
                System.out.println("HEAD detached at " + shortCommit);
            }
            System.out.println();
        } catch (Exception e) {
            System.out.println("On branch (unknown)");
            System.out.println();
        }
    }
}
