package com.ismile.core.chronovcscli.commands;

import com.ismile.core.chronovcscli.core.branch.BranchInfo;
import com.ismile.core.chronovcscli.core.branch.BranchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.List;

@Component
@Command(
        name = "branch",
        description = "List, create, or delete branches"
)
@RequiredArgsConstructor
@Slf4j
public class BranchCommand implements Runnable {

    private final BranchService branchService;

    @Parameters(index = "0", arity = "0..1", description = "Branch name (for create/rename)")
    private String branchName;

    @Parameters(index = "1", arity = "0..1", description = "New branch name (for rename)")
    private String newBranchName;

    @Option(names = {"-d", "--delete"}, description = "Delete branch")
    private boolean delete;

    @Option(names = {"-D", "--force-delete"}, description = "Force delete branch")
    private boolean forceDelete;

    @Option(names = {"-m", "--move"}, description = "Rename branch")
    private boolean rename;

    @Option(names = {"-r", "--remotes"}, description = "List remote branches")
    private boolean showRemotes;

    @Option(names = {"-a", "--all"}, description = "List both local and remote branches")
    private boolean showAll;

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

            // Delete branch
            if (delete || forceDelete) {
                if (branchName == null) {
                    System.out.println("Error: Please specify branch name to delete");
                    return;
                }

                branchService.deleteBranch(projectRoot, branchName, forceDelete);
                System.out.println("Deleted branch '" + branchName + "'");
                return;
            }

            // Rename branch
            if (rename) {
                if (branchName == null || newBranchName == null) {
                    System.out.println("Error: Please specify old and new branch names");
                    System.out.println("Usage: chronovcs branch -m <old-name> <new-name>");
                    return;
                }

                branchService.renameBranch(projectRoot, branchName, newBranchName);
                System.out.println("Renamed branch '" + branchName + "' to '" + newBranchName + "'");
                return;
            }

            // Create branch
            if (branchName != null) {
                branchService.createBranch(projectRoot, branchName);
                System.out.println("Created branch '" + branchName + "'");
                return;
            }

            // List branches (default)
            if (showRemotes || showAll) {
                listRemoteBranches(projectRoot, showAll);
            } else {
                List<BranchInfo> branches = branchService.listBranches(projectRoot);

                if (branches.isEmpty()) {
                    System.out.println("No branches found");
                    return;
                }

                for (BranchInfo branch : branches) {
                    String marker = branch.isCurrent() ? "* " : "  ";
                    String commitInfo = branch.getCommitHash() != null
                            ? " -> " + branch.getCommitHash().substring(0, Math.min(7, branch.getCommitHash().length()))
                            : "";

                    System.out.println(marker + branch.getName() + commitInfo);
                }
            }

        } catch (Exception e) {
            log.error("Branch command failed", e);
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void listRemoteBranches(File projectRoot, boolean showAll) throws Exception {
        // List local branches if showAll
        if (showAll) {
            List<BranchInfo> localBranches = branchService.listBranches(projectRoot);
            for (BranchInfo branch : localBranches) {
                String marker = branch.isCurrent() ? "* " : "  ";
                String commitInfo = branch.getCommitHash() != null
                        ? " -> " + branch.getCommitHash().substring(0, Math.min(7, branch.getCommitHash().length()))
                        : "";

                System.out.println(marker + branch.getName() + commitInfo);
            }
        }

        // List remote branches
        File remotesDir = new File(projectRoot, ".vcs/refs/remotes/origin");
        if (!remotesDir.exists() || !remotesDir.isDirectory()) {
            if (!showAll) {
                System.out.println("No remote branches found.");
                System.out.println("Run 'chronovcs fetch' to fetch remote branches.");
            }
            return;
        }

        File[] remoteRefs = remotesDir.listFiles();
        if (remoteRefs == null || remoteRefs.length == 0) {
            if (!showAll) {
                System.out.println("No remote branches found.");
            }
            return;
        }

        for (File refFile : remoteRefs) {
            if (refFile.isFile()) {
                String branchName = refFile.getName();
                String commitHash = java.nio.file.Files.readString(refFile.toPath()).trim();
                String commitInfo = commitHash.length() >= 7
                        ? " -> " + commitHash.substring(0, 7)
                        : " -> " + commitHash;

                System.out.println("  remotes/origin/" + branchName + commitInfo);
            }
        }
    }
}
