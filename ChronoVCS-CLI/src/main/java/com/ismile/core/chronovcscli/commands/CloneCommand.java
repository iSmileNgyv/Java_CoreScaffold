package com.ismile.core.chronovcscli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcscli.auth.CredentialsEntry;
import com.ismile.core.chronovcscli.auth.CredentialsService;
import com.ismile.core.chronovcscli.remote.RemoteCloneService;
import com.ismile.core.chronovcscli.remote.RemoteConfig;
import com.ismile.core.chronovcscli.remote.dto.BatchObjectsResponseDto;
import com.ismile.core.chronovcscli.remote.dto.CommitHistoryResponseDto;
import com.ismile.core.chronovcscli.remote.dto.CommitSnapshotDto;
import com.ismile.core.chronovcscli.remote.dto.RefsResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

@Component
@Command(
        name = "clone",
        description = "Clone a repository from remote server"
)
@RequiredArgsConstructor
@Slf4j
public class CloneCommand implements Runnable {

    private final RemoteCloneService remoteCloneService;
    private final CredentialsService credentialsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Parameters(index = "0", description = "Remote URL (e.g., http://localhost:8080)")
    private String remoteUrl;

    @Parameters(index = "1", description = "Repository key (e.g., test-repo)")
    private String repoKey;

    @Parameters(index = "2", description = "Target directory", defaultValue = ".")
    private String targetDir;

    @Override
    public void run() {
        try {
            System.out.println("Cloning repository '" + repoKey + "' from " + remoteUrl + "...");

            Optional<CredentialsEntry> credsOpt = credentialsService.findForServer(remoteUrl);
            if (credsOpt.isEmpty()) {
                System.out.println("Error: No credentials found for " + remoteUrl);
                System.out.println("Please run 'chronovcs login' first.");
                return;
            }

            CredentialsEntry creds = credsOpt.get();

            RemoteConfig config = new RemoteConfig();
            config.setBaseUrl(remoteUrl);
            config.setRepoKey(repoKey);

            File targetDirectory = new File(targetDir);
            if (!targetDirectory.exists()) {
                targetDirectory.mkdirs();
            }

            File vcsDir = new File(targetDirectory, ".vcs");
            if (vcsDir.exists()) {
                System.out.println("Error: .vcs directory already exists in " + targetDir);
                return;
            }

            System.out.println("Fetching repository information...");
            RefsResponseDto refs = remoteCloneService.getRefs(config, creds);

            if (refs.getBranches().isEmpty()) {
                System.out.println("Warning: Repository is empty (no branches)");
                initEmptyRepository(targetDirectory, refs.getDefaultBranch(), config);
                return;
            }

            String defaultBranch = refs.getDefaultBranch();
            System.out.println("Default branch: " + defaultBranch);

            System.out.println("Fetching commit history for branch '" + defaultBranch + "'...");
            CommitHistoryResponseDto history = remoteCloneService.getCommitHistory(config, creds, defaultBranch, null);

            if (history.getCommits().isEmpty()) {
                System.out.println("Warning: Branch '" + defaultBranch + "' has no commits");
                initEmptyRepository(targetDirectory, defaultBranch, config);
                return;
            }

            System.out.println("Found " + history.getCommits().size() + " commits");

            Set<String> allBlobHashes = new HashSet<>();
            for (CommitSnapshotDto commit : history.getCommits()) {
                if (commit.getFiles() != null) {
                    allBlobHashes.addAll(commit.getFiles().values());
                }
            }

            System.out.println("Downloading " + allBlobHashes.size() + " objects...");
            Map<String, String> allObjects = new HashMap<>();

            List<String> hashList = new ArrayList<>(allBlobHashes);
            int batchSize = 50;
            for (int i = 0; i < hashList.size(); i += batchSize) {
                int end = Math.min(i + batchSize, hashList.size());
                List<String> batch = hashList.subList(i, end);

                BatchObjectsResponseDto batchResponse = remoteCloneService.getBatchObjects(config, creds, batch);
                allObjects.putAll(batchResponse.getObjects());

                System.out.println("Downloaded " + allObjects.size() + "/" + allBlobHashes.size() + " objects");
            }

            System.out.println("Setting up local repository...");
            setupLocalRepository(targetDirectory, defaultBranch, refs, history, allObjects, config);

            System.out.println("Clone completed successfully!");
            System.out.println("Repository cloned to: " + targetDirectory.getAbsolutePath());

        } catch (Exception e) {
            log.error("Clone failed", e);
            System.out.println("Error: Clone failed - " + e.getMessage());
        }
    }

    private void initEmptyRepository(File targetDir, String defaultBranch, RemoteConfig config) throws Exception {
        new File(targetDir, ".vcs").mkdir();
        new File(targetDir, ".vcs/objects").mkdirs();
        new File(targetDir, ".vcs/commits").mkdirs();
        new File(targetDir, ".vcs/refs/heads").mkdirs();

        File mainRef = new File(targetDir, ".vcs/refs/heads/" + defaultBranch);
        mainRef.createNewFile();

        File head = new File(targetDir, ".vcs/HEAD");
        Files.writeString(head.toPath(), "ref: refs/heads/" + defaultBranch);

        File configFile = new File(targetDir, ".vcs/config");
        String configContent = String.format("""
                [repository]
                default_branch=%s
                versioning_mode=project
                """, defaultBranch);
        Files.writeString(configFile.toPath(), configContent);

        File remoteFile = new File(targetDir, ".vcs/remote.json");
        String remoteJson = objectMapper.writeValueAsString(config);
        Files.writeString(remoteFile.toPath(), remoteJson);

        System.out.println("Initialized empty ChronoVCS repository in .vcs/");
    }

    private void setupLocalRepository(File targetDir,
                                       String defaultBranch,
                                       RefsResponseDto refs,
                                       CommitHistoryResponseDto history,
                                       Map<String, String> objects,
                                       RemoteConfig config) throws Exception {

        new File(targetDir, ".vcs").mkdir();
        new File(targetDir, ".vcs/objects").mkdirs();
        new File(targetDir, ".vcs/commits").mkdirs();
        new File(targetDir, ".vcs/refs/heads").mkdirs();

        for (Map.Entry<String, String> entry : objects.entrySet()) {
            String hash = entry.getKey();
            String base64Content = entry.getValue();

            byte[] content = Base64.getDecoder().decode(base64Content);

            String prefix = hash.substring(0, 2);
            String suffix = hash.substring(2);

            File prefixDir = new File(targetDir, ".vcs/objects/" + prefix);
            if (!prefixDir.exists()) {
                prefixDir.mkdirs();
            }

            File blobFile = new File(prefixDir, suffix);
            Files.write(blobFile.toPath(), content);
        }

        for (CommitSnapshotDto commit : history.getCommits()) {
            String commitHash = commit.getId();
            File commitFile = new File(targetDir, ".vcs/commits/" + commitHash);

            String commitJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(commit);
            Files.writeString(commitFile.toPath(), commitJson);
        }

        for (Map.Entry<String, String> branch : refs.getBranches().entrySet()) {
            String branchName = branch.getKey();
            String headCommit = branch.getValue();

            File branchRef = new File(targetDir, ".vcs/refs/heads/" + branchName);
            branchRef.getParentFile().mkdirs();
            Files.writeString(branchRef.toPath(), headCommit);
        }

        File head = new File(targetDir, ".vcs/HEAD");
        Files.writeString(head.toPath(), "ref: refs/heads/" + defaultBranch);

        File configFile = new File(targetDir, ".vcs/config");
        String configContent = String.format("""
                [repository]
                default_branch=%s
                versioning_mode=project
                """, defaultBranch);
        Files.writeString(configFile.toPath(), configContent);

        File remoteFile = new File(targetDir, ".vcs/remote.json");
        String remoteJson = objectMapper.writeValueAsString(config);
        Files.writeString(remoteFile.toPath(), remoteJson);

        if (!history.getCommits().isEmpty()) {
            CommitSnapshotDto latestCommit = history.getCommits().get(0);
            checkoutCommit(targetDir, latestCommit, objects);
        }
    }

    private void checkoutCommit(File targetDir, CommitSnapshotDto commit, Map<String, String> objects) throws Exception {
        if (commit.getFiles() == null || commit.getFiles().isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : commit.getFiles().entrySet()) {
            String filePath = entry.getKey();
            String blobHash = entry.getValue();

            String base64Content = objects.get(blobHash);
            if (base64Content == null) {
                log.warn("Missing blob for file: {} (hash: {})", filePath, blobHash);
                continue;
            }

            byte[] content = Base64.getDecoder().decode(base64Content);

            File file = new File(targetDir, filePath);
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), content);
        }

        System.out.println("Checked out " + commit.getFiles().size() + " files");
    }
}
