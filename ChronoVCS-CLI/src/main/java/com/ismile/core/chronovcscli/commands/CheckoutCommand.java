package com.ismile.core.chronovcscli.commands;

import com.ismile.core.chronovcscli.core.checkout.CheckoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;

@Component
@Command(
        name = "checkout",
        description = "Switch branches or restore files"
)
@RequiredArgsConstructor
@Slf4j
public class CheckoutCommand implements Runnable {

    private final CheckoutService checkoutService;

    @Parameters(index = "0", arity = "0..1", description = "Branch name or commit hash")
    private String target;

    @Parameters(index = "1", arity = "0..1", description = "File path (when using --)")
    private String filePath;

    @Option(names = {"-b"}, description = "Create new branch and switch to it")
    private String createBranch;

    @Override
    public void run() {
        try {
            File projectRoot = new File(".").getAbsoluteFile();

            // Check if .vcs exists
            File vcsDir = new File(projectRoot, ".vcs");
            if (!vcsDir.exists()) {
                System.out.println("Error: Not a ChronoVCS repository");
                return;
            }

            // Create new branch and checkout
            if (createBranch != null) {
                // First create branch (using BranchService would be better, but for simplicity:)
                File branchFile = new File(projectRoot, ".vcs/refs/heads/" + createBranch);
                if (branchFile.exists()) {
                    System.out.println("Error: Branch '" + createBranch + "' already exists");
                    return;
                }

                // Get current HEAD
                File headFile = new File(projectRoot, ".vcs/HEAD");
                String headContent = java.nio.file.Files.readString(headFile.toPath()).trim();

                String currentCommit;
                if (headContent.startsWith("ref:")) {
                    String refPath = headContent.substring(4).trim();
                    File refFile = new File(projectRoot, ".vcs/" + refPath);
                    currentCommit = java.nio.file.Files.readString(refFile.toPath()).trim();
                } else {
                    currentCommit = headContent;
                }

                // Create new branch
                branchFile.getParentFile().mkdirs();
                java.nio.file.Files.writeString(branchFile.toPath(), currentCommit);

                // Switch to new branch
                checkoutService.checkoutBranch(projectRoot, createBranch);
                System.out.println("Switched to a new branch '" + createBranch + "'");
                return;
            }

            // Restore file (checkout -- file.txt)
            if (target != null && target.equals("--") && filePath != null) {
                checkoutService.restoreFile(projectRoot, filePath);
                System.out.println("Restored file: " + filePath);
                return;
            }

            // No target specified
            if (target == null) {
                System.out.println("Error: Please specify branch name or commit hash");
                System.out.println("Usage:");
                System.out.println("  chronovcs checkout <branch-name>");
                System.out.println("  chronovcs checkout <commit-hash>");
                System.out.println("  chronovcs checkout -b <new-branch>");
                System.out.println("  chronovcs checkout -- <file-path>");
                return;
            }

            // Check if target is a branch
            File branchFile = new File(projectRoot, ".vcs/refs/heads/" + target);
            if (branchFile.exists()) {
                // Checkout branch
                checkoutService.checkoutBranch(projectRoot, target);
                System.out.println("Switched to branch '" + target + "'");
                return;
            }

            // Check if target is a commit hash
            File commitFile = new File(projectRoot, ".vcs/commits/" + target);
            if (commitFile.exists()) {
                // Checkout commit (detached HEAD)
                checkoutService.checkoutCommit(projectRoot, target);
                System.out.println("HEAD is now at " + target.substring(0, Math.min(7, target.length())) + " (detached)");
                System.out.println("You are in 'detached HEAD' state. You can look around, make experimental");
                System.out.println("changes and commit them. You can discard any commits you make by switching");
                System.out.println("back to a branch with 'chronovcs checkout <branch-name>'");
                return;
            }

            // Target not found
            System.out.println("Error: Branch or commit '" + target + "' not found");

        } catch (Exception e) {
            log.error("Checkout command failed", e);
            System.out.println("Error: " + e.getMessage());
        }
    }
}
