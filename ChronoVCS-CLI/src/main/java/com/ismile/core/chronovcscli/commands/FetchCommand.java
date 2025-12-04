package com.ismile.core.chronovcscli.commands;

import com.ismile.core.chronovcscli.auth.CredentialsEntry;
import com.ismile.core.chronovcscli.auth.CredentialsService;
import com.ismile.core.chronovcscli.remote.RemoteCloneService;
import com.ismile.core.chronovcscli.remote.RemoteConfig;
import com.ismile.core.chronovcscli.remote.RemoteConfigService;
import com.ismile.core.chronovcscli.remote.dto.RefsResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

@Component
@Command(
        name = "fetch",
        description = "Fetch remote branches and refs without merging"
)
@RequiredArgsConstructor
@Slf4j
public class FetchCommand implements Runnable {

    private final RemoteConfigService remoteConfigService;
    private final RemoteCloneService remoteCloneService;
    private final CredentialsService credentialsService;

    @Override
    public void run() {
        try {
            File projectRoot = new File(".").getCanonicalFile();
            File vcsDir = new File(projectRoot, ".vcs");

            if (!vcsDir.isDirectory()) {
                System.out.println("Not a ChronoVCS repository (no .vcs directory found).");
                return;
            }

            // Load remote config
            RemoteConfig remoteConfig = remoteConfigService.load(projectRoot);

            // Load credentials
            CredentialsEntry creds = credentialsService
                    .findForServer(remoteConfig.getBaseUrl())
                    .orElseThrow(() -> new IllegalStateException(
                            "No credentials configured for server " + remoteConfig.getBaseUrl()
                    ));

            System.out.println("Fetching from " + remoteConfig.getBaseUrl() + "...");

            // Fetch remote refs
            RefsResponseDto refs = remoteCloneService.getRefs(remoteConfig, creds);

            // Store remote refs in .vcs/refs/remotes/origin/
            File remotesDir = new File(projectRoot, ".vcs/refs/remotes/origin");
            remotesDir.mkdirs();

            int fetchedCount = 0;
            for (Map.Entry<String, String> branch : refs.getBranches().entrySet()) {
                String branchName = branch.getKey();
                String commitHash = branch.getValue();

                File remoteRefFile = new File(remotesDir, branchName);
                String oldHash = null;

                if (remoteRefFile.exists()) {
                    oldHash = Files.readString(remoteRefFile.toPath()).trim();
                }

                Files.writeString(remoteRefFile.toPath(), commitHash);

                if (oldHash == null) {
                    System.out.println(" * [new branch]      " + branchName + " -> origin/" + branchName);
                } else if (!oldHash.equals(commitHash)) {
                    System.out.println("   " + oldHash.substring(0, 7) + ".." + commitHash.substring(0, 7) +
                                     "  " + branchName + " -> origin/" + branchName);
                }

                fetchedCount++;
            }

            if (fetchedCount == 0) {
                System.out.println("No branches found on remote.");
            } else {
                System.out.println("\nFetched " + fetchedCount + " branch(es) from origin.");
            }

        } catch (Exception e) {
            log.error("Fetch failed", e);
            System.out.println("Fetch failed: " + e.getMessage());
        }
    }
}
