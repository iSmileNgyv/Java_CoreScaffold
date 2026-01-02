package com.ismile.core.chronovcscli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcscli.auth.CredentialsEntry;
import com.ismile.core.chronovcscli.auth.CredentialsService;
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
        name = "revert",
        description = "Revert working tree to a previous release snapshot"
)
@RequiredArgsConstructor
@Slf4j
public class RevertCommand implements Runnable {

    private final PullService pullService;
    private final CredentialsService credentialsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Option(names = {"--release"}, required = true, description = "Release version to revert to (use 'latest')")
    private String releaseVersion;

    @Override
    public void run() {
        try {
            File projectRoot = new File(".").getAbsoluteFile();

            File vcsDir = new File(projectRoot, ".vcs");
            if (!vcsDir.exists()) {
                System.out.println("Error: Not a ChronoVCS repository");
                System.out.println("Run 'chronovcs init' or 'chronovcs clone' first");
                return;
            }

            File remoteFile = new File(projectRoot, ".vcs/remote.json");
            if (!remoteFile.exists()) {
                System.out.println("Error: No remote configured");
                System.out.println("Run 'chronovcs remote-config <url> <repo-key>' first");
                return;
            }

            String remoteJson = Files.readString(remoteFile.toPath());
            RemoteConfig remoteConfig = objectMapper.readValue(remoteJson, RemoteConfig.class);

            Optional<CredentialsEntry> credsOpt = credentialsService.findForServer(remoteConfig.getBaseUrl());
            if (credsOpt.isEmpty()) {
                System.out.println("Error: No credentials found for " + remoteConfig.getBaseUrl());
                System.out.println("Run 'chronovcs login' first");
                return;
            }

            CredentialsEntry credentials = credsOpt.get();

            System.out.println("Reverting to release " + releaseVersion + "...");

            PullResult result = pullService.revertRelease(projectRoot, remoteConfig, credentials, releaseVersion);

            if (result.isSuccess()) {
                System.out.println(result.getMessage());
                System.out.println("Revert successful!");
            } else {
                System.out.println("Revert failed:");
                System.out.println(result.getMessage());
            }

        } catch (Exception e) {
            log.error("Revert command failed", e);
            System.out.println("Error: Revert failed - " + e.getMessage());
        }
    }
}
