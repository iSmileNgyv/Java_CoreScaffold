package com.ismile.core.chronovcscli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcscli.auth.CredentialsEntry;
import com.ismile.core.chronovcscli.auth.CredentialsService;
import com.ismile.core.chronovcscli.core.checkout.CheckoutService;
import com.ismile.core.chronovcscli.core.pull.LocalCommitReader;
import com.ismile.core.chronovcscli.remote.RemoteCloneService;
import com.ismile.core.chronovcscli.remote.RemoteConfig;
import com.ismile.core.chronovcscli.remote.RemoteConfigService;
import com.ismile.core.chronovcscli.remote.dto.BatchObjectsResponseDto;
import com.ismile.core.chronovcscli.remote.dto.CommitSnapshotDto;
import com.ismile.core.chronovcscli.remote.dto.RefsResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

@Component
@Command(
        name = "reset",
        description = "Reset current branch to a commit or remote branch"
)
@RequiredArgsConstructor
@Slf4j
public class ResetCommand implements Runnable {

    private final RemoteConfigService remoteConfigService;
    private final CredentialsService credentialsService;
    private final RemoteCloneService remoteCloneService;
    private final LocalCommitReader localCommitReader;
    private final CheckoutService checkoutService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Option(names = {"--hard"}, description = "Discard local changes and reset working tree")
    private boolean hard;

    @Parameters(index = "0", arity = "0..1",
            description = "Target commit or remote branch (origin/<branch>)")
    private String target;

    @Override
    public void run() {
        try {
            File projectRoot = new File(".").getCanonicalFile();
            File vcsDir = new File(projectRoot, ".vcs");

            if (!vcsDir.isDirectory()) {
                System.out.println("Not a ChronoVCS repository (no .vcs directory found).");
                return;
            }

            if (!hard) {
                System.out.println("Error: Only --hard reset is supported");
                System.out.println("Usage: chronovcs reset --hard origin/<branch>");
                return;
            }

            if (target == null || target.isBlank()) {
                System.out.println("Error: Please specify a target commit or origin/<branch>");
                System.out.println("Usage: chronovcs reset --hard origin/<branch>");
                return;
            }

            if (checkoutService.isDetachedHead(projectRoot)) {
                System.out.println("Error: Cannot reset in detached HEAD state");
                System.out.println("Hint: Run 'chronovcs checkout <branch>' first");
                return;
            }

            String currentBranch = localCommitReader.getCurrentBranch(projectRoot);
            String commitId = resolveTargetCommit(projectRoot, target);

            if (commitId == null || commitId.isBlank()) {
                System.out.println("Error: Target not found: " + target);
                return;
            }

            ensureCommitAvailable(projectRoot, commitId);

            File branchRefFile = new File(projectRoot, ".vcs/refs/heads/" + currentBranch);
            branchRefFile.getParentFile().mkdirs();
            Files.writeString(branchRefFile.toPath(), commitId);

            checkoutService.checkoutBranchHard(projectRoot, currentBranch);

            System.out.println("Hard reset '" + currentBranch + "' to " + target);
            System.out.println("New HEAD: " + shortHash(commitId));

        } catch (Exception e) {
            log.error("Reset failed", e);
            System.out.println("Reset failed: " + e.getMessage());
        }
    }

    private String resolveTargetCommit(File projectRoot, String targetRef) {
            if (targetRef.startsWith("origin/")) {
                String branchName = targetRef.substring("origin/".length());
            try {
                RemoteConfig remoteConfig = remoteConfigService.load(projectRoot);
                CredentialsEntry creds = credentialsService
                        .findForServer(remoteConfig.getBaseUrl())
                        .orElseThrow(() -> new IllegalStateException(
                                "No credentials configured for server " + remoteConfig.getBaseUrl()
                        ));

                RefsResponseDto refs = remoteCloneService.getRefs(remoteConfig, creds);
                return refs.getBranches().get(branchName);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to resolve origin/" + branchName + ": " + e.getMessage(), e);
            }
        }

        return targetRef;
    }

    private void ensureCommitAvailable(File projectRoot, String commitId) throws Exception {
        File commitFile = new File(projectRoot, ".vcs/commits/" + commitId + ".json");
        if (commitFile.exists()) {
            return;
        }

        RemoteConfig remoteConfig = remoteConfigService.load(projectRoot);
        CredentialsEntry creds = credentialsService
                .findForServer(remoteConfig.getBaseUrl())
                .orElseThrow(() -> new IllegalStateException(
                        "No credentials configured for server " + remoteConfig.getBaseUrl()
                ));

        CommitSnapshotDto snapshot = remoteCloneService.getCommit(remoteConfig, creds, commitId);
        if (snapshot == null) {
            throw new IllegalStateException("Remote commit not found: " + commitId);
        }

        downloadMissingBlobs(projectRoot, remoteConfig, creds, snapshot);

        commitFile.getParentFile().mkdirs();
        String commitJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(snapshot);
        Files.writeString(commitFile.toPath(), commitJson);
    }

    private void downloadMissingBlobs(File projectRoot,
                                      RemoteConfig remoteConfig,
                                      CredentialsEntry creds,
                                      CommitSnapshotDto snapshot) throws Exception {
        if (snapshot.getFiles() == null || snapshot.getFiles().isEmpty()) {
            return;
        }

        Set<String> blobHashes = new HashSet<>(snapshot.getFiles().values());
        List<String> missing = new ArrayList<>();

        for (String hash : blobHashes) {
            File blobFile = getBlobFile(projectRoot, hash);
            if (!blobFile.exists()) {
                missing.add(hash);
            }
        }

        if (missing.isEmpty()) {
            return;
        }

        int batchSize = 50;
        for (int i = 0; i < missing.size(); i += batchSize) {
            int end = Math.min(i + batchSize, missing.size());
            List<String> batch = missing.subList(i, end);

            BatchObjectsResponseDto batchResponse = remoteCloneService.getBatchObjects(
                    remoteConfig, creds, batch
            );

            for (Map.Entry<String, String> entry : batchResponse.getObjects().entrySet()) {
                String hash = entry.getKey();
                String base64Content = entry.getValue();
                byte[] content = Base64.getDecoder().decode(base64Content);

                File blobFile = getBlobFile(projectRoot, hash);
                blobFile.getParentFile().mkdirs();
                Files.write(blobFile.toPath(), content);
            }
        }
    }

    private File getBlobFile(File projectRoot, String hash) {
        String prefix = hash.substring(0, 2);
        String suffix = hash.substring(2);
        return new File(projectRoot, ".vcs/objects/" + prefix + "/" + suffix);
    }

    private String shortHash(String hash) {
        if (hash == null) {
            return "";
        }
        return hash.substring(0, Math.min(7, hash.length()));
    }
}
