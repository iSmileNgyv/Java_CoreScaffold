package com.ismile.core.chronovcscli.commands;

import com.ismile.core.chronovcscli.auth.CredentialsEntry;
import com.ismile.core.chronovcscli.auth.CredentialsService;
import com.ismile.core.chronovcscli.core.checkout.CheckoutService;
import com.ismile.core.chronovcscli.core.pull.PullService;
import com.ismile.core.chronovcscli.remote.RemoteCloneService;
import com.ismile.core.chronovcscli.remote.RemoteConfig;
import com.ismile.core.chronovcscli.remote.RemoteConfigService;
import com.ismile.core.chronovcscli.remote.dto.BatchObjectsResponseDto;
import com.ismile.core.chronovcscli.remote.dto.CommitHistoryResponseDto;
import com.ismile.core.chronovcscli.remote.dto.CommitSnapshotDto;
import com.ismile.core.chronovcscli.remote.dto.RefsResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Files;

@Component
@Command(
        name = "checkout",
        description = "Switch branches or restore files"
)
@RequiredArgsConstructor
@Slf4j
public class CheckoutCommand implements Runnable {

    private final CheckoutService checkoutService;
    private final RemoteConfigService remoteConfigService;
    private final RemoteCloneService remoteCloneService;
    private final CredentialsService credentialsService;
    private final PullService pullService;

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
                // Verify that the commit exists locally
                String branchHead = Files.readString(branchFile.toPath()).trim();
                File commitFile = new File(projectRoot, ".vcs/commits/" + branchHead + ".json");

                if (!commitFile.exists()) {
                    // Commit doesn't exist locally, try to fetch from remote
                    System.out.println("Branch '" + target + "' exists but commit is missing locally.");
                    System.out.println("Attempting to fetch from remote...");

                    if (tryCheckoutRemoteBranch(projectRoot, target)) {
                        return;
                    } else {
                        System.out.println("Error: Failed to fetch branch '" + target + "' from remote");
                        System.out.println("Hint: The branch exists locally but points to a commit that doesn't exist.");
                        System.out.println("Try running 'chronovcs pull' or delete the branch and checkout again.");
                        return;
                    }
                }

                // Checkout branch
                checkoutService.checkoutBranch(projectRoot, target);
                System.out.println("Switched to branch '" + target + "'");
                return;
            }

            // Check if target is a commit hash
            File commitFile = new File(projectRoot, ".vcs/commits/" + target + ".json");
            if (commitFile.exists()) {
                // Checkout commit (detached HEAD)
                checkoutService.checkoutCommit(projectRoot, target);
                System.out.println("HEAD is now at " + target.substring(0, Math.min(7, target.length())) + " (detached)");
                System.out.println("You are in 'detached HEAD' state. You can look around, make experimental");
                System.out.println("changes and commit them. You can discard any commits you make by switching");
                System.out.println("back to a branch with 'chronovcs checkout <branch-name>'");
                return;
            }

            // Check remote branches
            if (tryCheckoutRemoteBranch(projectRoot, target)) {
                return;
            }

            // Target not found
            System.out.println("Error: Branch or commit '" + target + "' not found");
            System.out.println("Hint: Use 'chronovcs branch' to see available local branches");

        } catch (Exception e) {
            log.error("Checkout command failed", e);
            System.out.println("Error: " + e.getMessage());
        }
    }

    /**
     * Try to checkout a branch from remote if it doesn't exist locally
     */
    private boolean tryCheckoutRemoteBranch(File projectRoot, String branchName) {
        try {
            // Load remote config
            RemoteConfig remoteConfig = remoteConfigService.load(projectRoot);

            // Load credentials
            CredentialsEntry creds = credentialsService
                    .findForServer(remoteConfig.getBaseUrl())
                    .orElse(null);

            if (creds == null) {
                log.debug("No credentials found for remote server");
                return false;
            }

            // Fetch remote refs
            System.out.println("Checking remote branches...");
            RefsResponseDto refs = remoteCloneService.getRefs(remoteConfig, creds);

            // Check if branch exists on remote
            String remoteBranchHead = refs.getBranches().get(branchName);
            if (remoteBranchHead == null || remoteBranchHead.isBlank()) {
                log.debug("Branch '{}' not found on remote", branchName);
                return false;
            }

            System.out.println("Branch '" + branchName + "' found on remote. Fetching...");

            // Fetch commits for the branch
            fetchCommitsForBranch(projectRoot, remoteConfig, creds, branchName, remoteBranchHead);

            // Create or update local branch pointing to remote HEAD
            File branchFile = new File(projectRoot, ".vcs/refs/heads/" + branchName);
            branchFile.getParentFile().mkdirs();
            boolean branchExisted = branchFile.exists();
            Files.writeString(branchFile.toPath(), remoteBranchHead);

            // Checkout the branch
            checkoutService.checkoutBranch(projectRoot, branchName);

            if (branchExisted) {
                System.out.println("Branch '" + branchName + "' updated from remote.");
                System.out.println("Switched to branch '" + branchName + "'");
            } else {
                System.out.println("Branch '" + branchName + "' set up to track remote branch.");
                System.out.println("Switched to a new branch '" + branchName + "'");
            }

            return true;

        } catch (Exception e) {
            log.error("Failed to checkout remote branch: {}", branchName, e);
            return false;
        }
    }

    /**
     * Fetch commits for a specific branch from remote
     */
    private void fetchCommitsForBranch(File projectRoot, RemoteConfig remoteConfig,
                                       CredentialsEntry creds, String branchName,
                                       String branchHead) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        // Fetch commit history for the branch
        CommitHistoryResponseDto history = remoteCloneService.getCommitHistory(
                remoteConfig, creds, branchName, null
        );

        if (history.getCommits() == null || history.getCommits().isEmpty()) {
            log.warn("No commits found for branch: {}", branchName);
            return;
        }

        // Collect all blob hashes from commits
        java.util.Set<String> blobHashes = new java.util.HashSet<>();
        for (CommitSnapshotDto commit : history.getCommits()) {
            if (commit.getFiles() != null) {
                blobHashes.addAll(commit.getFiles().values());
            }
        }

        // Filter out blobs that already exist locally
        java.util.Set<String> blobsToDownload = new java.util.HashSet<>();
        for (String hash : blobHashes) {
            File blobFile = getBlobFile(projectRoot, hash);
            if (!blobFile.exists()) {
                blobsToDownload.add(hash);
            }
        }

        // Download blobs in batches
        if (!blobsToDownload.isEmpty()) {
            java.util.List<String> hashList = new java.util.ArrayList<>(blobsToDownload);
            int batchSize = 50;

            for (int i = 0; i < hashList.size(); i += batchSize) {
                int end = Math.min(i + batchSize, hashList.size());
                java.util.List<String> batch = hashList.subList(i, end);

                BatchObjectsResponseDto batchResponse = remoteCloneService.getBatchObjects(
                        remoteConfig, creds, batch
                );

                // Write blobs to local storage
                for (java.util.Map.Entry<String, String> entry : batchResponse.getObjects().entrySet()) {
                    String hash = entry.getKey();
                    String base64Content = entry.getValue();

                    byte[] content = java.util.Base64.getDecoder().decode(base64Content);

                    File blobFile = getBlobFile(projectRoot, hash);
                    blobFile.getParentFile().mkdirs();
                    Files.write(blobFile.toPath(), content);
                }
            }
        }

        // Write commits to local storage
        for (CommitSnapshotDto commit : history.getCommits()) {
            File commitFile = new File(projectRoot, ".vcs/commits/" + commit.getId() + ".json");
            commitFile.getParentFile().mkdirs();
            String commitJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(commit);
            Files.writeString(commitFile.toPath(), commitJson);
        }

        log.info("Fetched {} commits and {} blobs for branch '{}'",
                history.getCommits().size(), blobsToDownload.size(), branchName);
    }

    private File getBlobFile(File projectRoot, String hash) {
        String prefix = hash.substring(0, 2);
        String suffix = hash.substring(2);
        return new File(projectRoot, ".vcs/objects/" + prefix + "/" + suffix);
    }
}
