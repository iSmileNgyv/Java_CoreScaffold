package com.ismile.core.chronovcscli.commands;

import com.ismile.core.chronovcscli.core.merge.MergeEngine;
import com.ismile.core.chronovcscli.core.merge.MergeResult;
import com.ismile.core.chronovcscli.core.merge.MergeState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;

/**
 * Merge command - handle merge operations
 *
 * Usage:
 *   chronovcs merge               - Show merge status
 *   chronovcs merge --continue    - Continue merge after resolving conflicts
 *   chronovcs merge --abort       - Abort ongoing merge
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Command(
        name = "merge",
        description = "Manage merge operations",
        mixinStandardHelpOptions = true
)
public class MergeCommand implements Runnable {

    private final MergeEngine mergeEngine;

    @Option(names = {"--continue"}, description = "Continue merge after resolving conflicts")
    private boolean continueMode;

    @Option(names = {"--abort"}, description = "Abort ongoing merge")
    private boolean abortMode;

    @Override
    public void run() {
        File projectRoot = new File(System.getProperty("user.dir"));

        // Check if .vcs directory exists
        if (!new File(projectRoot, ".vcs").exists()) {
            System.err.println("Error: Not a ChronoVCS repository");
            System.err.println("Run 'chronovcs init' first");
            return;
        }

        if (continueMode) {
            handleContinue(projectRoot);
        } else if (abortMode) {
            handleAbort(projectRoot);
        } else {
            handleStatus(projectRoot);
        }
    }

    private void handleContinue(File projectRoot) {
        System.out.println("Continuing merge...");

        MergeResult result = mergeEngine.continueMerge(projectRoot);

        if (result.isSuccess()) {
            System.out.println("✓ " + result.getMessage());
            System.out.println("Merge commit: " + result.getMergeCommitId());
        } else {
            System.err.println("✗ " + result.getMessage());
            if (result.hasConflicts()) {
                System.err.println("\nConflicted files:");
                for (String file : result.getConflictedFiles()) {
                    System.err.println("  - " + file);
                }
                System.err.println("\nResolve conflicts and run 'chronovcs add <file>' then 'chronovcs merge --continue'");
            }
        }
    }

    private void handleAbort(File projectRoot) {
        if (!mergeEngine.isMergeInProgress(projectRoot)) {
            System.err.println("Error: No merge in progress");
            return;
        }

        System.out.println("Aborting merge...");

        if (mergeEngine.abortMerge(projectRoot)) {
            System.out.println("✓ Merge aborted successfully");
            System.out.println("HEAD reset to previous state");
        } else {
            System.err.println("✗ Failed to abort merge");
        }
    }

    private void handleStatus(File projectRoot) {
        MergeState state = mergeEngine.getMergeState(projectRoot);

        if (state == null || !state.isInProgress()) {
            System.out.println("No merge in progress");
            return;
        }

        System.out.println("Merge in progress:");
        System.out.println("  Branch: " + state.getBranch());
        System.out.println("  Local:  " + state.getLocalCommitId());
        System.out.println("  Remote: " + state.getRemoteCommitId());
        System.out.println("  Base:   " + state.getBaseCommitId());
        System.out.println();

        if (state.getConflictedFiles().isEmpty()) {
            System.out.println("All conflicts resolved!");
            System.out.println("Run 'chronovcs merge --continue' to complete the merge");
        } else {
            System.out.println("Conflicted files (" + state.getConflictedFiles().size() + "):");
            for (String file : state.getConflictedFiles()) {
                System.out.println("  - " + file);
            }
            System.out.println();
            System.out.println("To resolve conflicts:");
            System.out.println("  1. Edit conflicted files and resolve markers (<<<<<<, ======, >>>>>>)");
            System.out.println("  2. Run 'chronovcs add <file>' for each resolved file");
            System.out.println("  3. Run 'chronovcs merge --continue' to complete merge");
            System.out.println();
            System.out.println("Or run 'chronovcs merge --abort' to cancel the merge");
        }
    }
}
