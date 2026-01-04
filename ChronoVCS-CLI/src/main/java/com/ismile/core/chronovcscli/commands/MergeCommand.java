package com.ismile.core.chronovcscli.commands;

import com.ismile.core.chronovcscli.core.checkout.CheckoutService;
import com.ismile.core.chronovcscli.core.merge.MergeEngine;
import com.ismile.core.chronovcscli.core.merge.MergeResult;
import com.ismile.core.chronovcscli.core.merge.MergeState;
import com.ismile.core.chronovcscli.core.merge.MergeStrategy;
import com.ismile.core.chronovcscli.core.pull.LocalCommitReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Files;

/**
 * Merge command - handle merge operations
 *
 * Usage:
 *   chronovcs merge               - Show merge status
 *   chronovcs merge <branch>      - Merge branch into current branch
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
    private final LocalCommitReader localCommitReader;
    private final CheckoutService checkoutService;

    @Parameters(index = "0", arity = "0..1", description = "Branch name to merge into current branch")
    private String mergeBranch;

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

        if (continueMode && abortMode) {
            System.err.println("Error: Cannot use --continue and --abort together");
            return;
        }

        if ((continueMode || abortMode) && mergeBranch != null) {
            System.err.println("Error: Cannot use --continue/--abort with a branch name");
            return;
        }

        if (continueMode) {
            handleContinue(projectRoot);
        } else if (abortMode) {
            handleAbort(projectRoot);
        } else if (mergeBranch != null) {
            handleMerge(projectRoot, mergeBranch);
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

    private void handleMerge(File projectRoot, String branchName) {
        try {
            if (mergeEngine.isMergeInProgress(projectRoot)) {
                System.err.println("Error: Merge in progress. Resolve conflicts then run 'chronovcs merge --continue'");
                return;
            }

            String currentBranch = localCommitReader.getCurrentBranch(projectRoot);
            String currentHead = localCommitReader.getCurrentHead(projectRoot);

            if (branchName.equals(currentBranch)) {
                System.err.println("Error: Cannot merge a branch into itself");
                return;
            }

            File mergeBranchFile = new File(projectRoot, ".vcs/refs/heads/" + branchName);
            if (!mergeBranchFile.exists()) {
                System.err.println("Error: Branch '" + branchName + "' not found");
                return;
            }

            String mergeHead = Files.readString(mergeBranchFile.toPath()).trim();
            if (mergeHead.isEmpty()) {
                System.err.println("Error: Branch '" + branchName + "' has no commits");
                return;
            }

            if (!localCommitReader.commitExists(projectRoot, mergeHead)) {
                System.err.println("Error: Commit for branch '" + branchName + "' is missing locally");
                System.err.println("Hint: Run 'chronovcs pull' to update local commits.");
                return;
            }

            if (currentHead == null || currentHead.isEmpty()) {
                fastForward(projectRoot, currentBranch, branchName, mergeHead, currentHead);
                return;
            }

            System.out.println("Merging '" + branchName + "' into '" + currentBranch + "'...");
            MergeResult result = mergeEngine.merge(projectRoot, currentHead, mergeHead, currentBranch, branchName);

            if (result.isSuccess()) {
                if (result.getStrategy() == MergeStrategy.UP_TO_DATE) {
                    System.out.println("Already up to date.");
                    return;
                }

                if (result.getStrategy() == MergeStrategy.FAST_FORWARD) {
                    fastForward(projectRoot, currentBranch, branchName, mergeHead, currentHead);
                    return;
                }

                System.out.println("✓ " + result.getMessage());
                if (result.getMergeCommitId() != null) {
                    System.out.println("Merge commit: " + result.getMergeCommitId());
                }
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
        } catch (Exception e) {
            log.error("Merge failed", e);
            System.err.println("Error: " + e.getMessage());
        }
    }

    private void fastForward(File projectRoot,
                             String currentBranch,
                             String mergeBranch,
                             String targetHead,
                             String previousHead) throws Exception {
        File currentBranchFile = new File(projectRoot, ".vcs/refs/heads/" + currentBranch);
        currentBranchFile.getParentFile().mkdirs();

        try {
            Files.writeString(currentBranchFile.toPath(), targetHead);
            checkoutService.checkoutBranch(projectRoot, currentBranch);
            System.out.println("✓ Fast-forwarded '" + currentBranch + "' to '" + mergeBranch + "'");
            System.out.println("New HEAD: " + shortHash(targetHead));
        } catch (Exception e) {
            Files.writeString(currentBranchFile.toPath(), previousHead != null ? previousHead : "");
            throw e;
        }
    }

    private String shortHash(String hash) {
        if (hash == null) {
            return "";
        }
        return hash.substring(0, Math.min(7, hash.length()));
    }
}
