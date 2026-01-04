package com.ismile.core.chronovcscli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcscli.auth.CredentialsEntry;
import com.ismile.core.chronovcscli.auth.CredentialsService;
import com.ismile.core.chronovcscli.core.checkout.CheckoutService;
import com.ismile.core.chronovcscli.core.pull.LocalCommitReader;
import com.ismile.core.chronovcscli.core.pull.PullResult;
import com.ismile.core.chronovcscli.core.pull.PullService;
import com.ismile.core.chronovcscli.remote.RemoteConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Files;
import java.util.Optional;

@Component
@Command(
        name = "pull",
        description = "Fetch and integrate changes from remote repository"
)
@RequiredArgsConstructor
@Slf4j
public class PullCommand implements Runnable {

    private final PullService pullService;
    private final CredentialsService credentialsService;
    private final LocalCommitReader localCommitReader;
    private final CheckoutService checkoutService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Option(names = {"--release"}, description = "Release version to pull (use 'latest')")
    private String releaseVersion;

    @Option(names = {"--branch"}, description = "Branch to pull (switches temporarily if needed)")
    private String pullBranch;

    @Override
    public void run() {
        try {
            File projectRoot = new File(".").getAbsoluteFile();

            // 1. Check if .vcs exists
            File vcsDir = new File(projectRoot, ".vcs");
            if (!vcsDir.exists()) {
                System.out.println("Error: Not a ChronoVCS repository");
                System.out.println("Run 'chronovcs init' or 'chronovcs clone' first");
                return;
            }

            // 2. Load remote config
            File remoteFile = new File(projectRoot, ".vcs/remote.json");
            if (!remoteFile.exists()) {
                System.out.println("Error: No remote configured");
                System.out.println("Run 'chronovcs remote-config <url> <repo-key>' first");
                return;
            }

            String remoteJson = Files.readString(remoteFile.toPath());
            RemoteConfig remoteConfig = objectMapper.readValue(remoteJson, RemoteConfig.class);

            // 3. Load credentials
            Optional<CredentialsEntry> credsOpt = credentialsService.findForServer(remoteConfig.getBaseUrl());
            if (credsOpt.isEmpty()) {
                System.out.println("Error: No credentials found for " + remoteConfig.getBaseUrl());
                System.out.println("Run 'chronovcs login' first");
                return;
            }

            CredentialsEntry credentials = credsOpt.get();

            // 4. Execute pull
            System.out.println("Pulling from " + remoteConfig.getBaseUrl() + "/" + remoteConfig.getRepoKey() + "...");

            if (releaseVersion != null && pullBranch != null) {
                System.out.println("Error: --release and --branch cannot be used together");
                return;
            }

            String originalBranch = localCommitReader.getCurrentBranch(projectRoot);
            boolean switched = false;

            if (pullBranch != null && !pullBranch.isBlank() && !pullBranch.equals(originalBranch)) {
                File branchFile = new File(projectRoot, ".vcs/refs/heads/" + pullBranch);
                if (!branchFile.exists()) {
                    System.out.println("Error: Branch '" + pullBranch + "' not found locally");
                    System.out.println("Hint: Run 'chronovcs fetch' then 'chronovcs checkout " + pullBranch + "' to create it.");
                    return;
                }

                checkoutService.checkoutBranch(projectRoot, pullBranch);
                switched = true;
            }

            PullResult result = releaseVersion != null
                    ? pullService.pullRelease(projectRoot, remoteConfig, credentials, releaseVersion)
                    : pullService.pull(projectRoot, remoteConfig, credentials);

            // 5. Display result
            if (result.isSuccess()) {
                System.out.println(result.getMessage());
                if (result.getCommitsDownloaded() > 0) {
                    System.out.println("Downloaded " + result.getCommitsDownloaded() + " new commit(s)");
                }

                // Display changed files as IDE-clickable links
                if (result.getChangedFiles() != null && !result.getChangedFiles().isEmpty()) {
                    System.out.println("\nChanged files:");
                    for (String file : result.getChangedFiles()) {
                        // Format: file:1 (IDE-clickable)
                        System.out.println("  " + file + ":1");
                    }
                }

                System.out.println("\nPull successful!");

                if (switched) {
                    checkoutService.checkoutBranch(projectRoot, originalBranch);
                    System.out.println("Returned to branch '" + originalBranch + "'");
                }
            } else {
                System.out.println("Pull failed:");
                System.out.println(result.getMessage());

                if (switched) {
                    System.out.println("Staying on branch '" + pullBranch + "' due to pull failure");
                }
            }

        } catch (Exception e) {
            log.error("Pull command failed", e);
            System.out.println("Error: Pull failed - " + e.getMessage());
        }
    }
}
