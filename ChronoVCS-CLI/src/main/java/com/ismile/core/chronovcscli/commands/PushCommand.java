package com.ismile.core.chronovcscli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcscli.auth.CredentialsEntry;
import com.ismile.core.chronovcscli.auth.CredentialsService;
import com.ismile.core.chronovcscli.core.commit.CommitModel;
import com.ismile.core.chronovcscli.core.objectsStore.ObjectStore;
import com.ismile.core.chronovcscli.remote.RemoteConfig;
import com.ismile.core.chronovcscli.remote.RemoteConfigService;
import com.ismile.core.chronovcscli.remote.RemoteCloneService;
import com.ismile.core.chronovcscli.remote.RemotePushService;
import com.ismile.core.chronovcscli.remote.dto.CommitSnapshotDto;
import com.ismile.core.chronovcscli.remote.dto.PushRequestDto;
import com.ismile.core.chronovcscli.remote.dto.PushResultDto;
import com.ismile.core.chronovcscli.remote.dto.RefsResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
@Command(
        name = "push",
        description = "Push local HEAD commit to remote ChronoVCS server"
)
@RequiredArgsConstructor
public class PushCommand implements Runnable {

    private final RemoteConfigService remoteConfigService;
    private final CredentialsService credentialsService;
    private final RemotePushService remotePushService;
    private final RemoteCloneService remoteCloneService;
    private final ObjectStore objectStore;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void run() {
        try {
            File projectRoot = new File(".").getCanonicalFile();
            File vcsDir = new File(projectRoot, ".vcs");

            if (!vcsDir.isDirectory()) {
                System.out.println("Not a ChronoVCS repository (no .vcs directory found).");
                System.out.println("Run 'chronovcs init' first.");
                return;
            }

            // 1) Remote config
            RemoteConfig remoteConfig = remoteConfigService.load(projectRoot);

            // 2) Credentials
            CredentialsEntry creds = credentialsService
                    .findForServer(remoteConfig.getBaseUrl())
                    .orElseThrow(() -> new IllegalStateException(
                            "No credentials configured for server " + remoteConfig.getBaseUrl()
                                    + ". Run: chronovcs login --server " + remoteConfig.getBaseUrl()
                                    + " --email <EMAIL>"
                    ));

            // 3) Local HEAD + branch + commit
            HeadInfo headInfo = loadHeadInfo(vcsDir);
            if (headInfo.commitId == null || headInfo.commitId.isBlank()) {
                System.out.println("Nothing to push: no local commits on branch " + headInfo.branch);
                return;
            }

            CommitModel localCommit = loadCommitModel(vcsDir, headInfo.commitId);

            // 4) Commit snapshot DTO
            CommitSnapshotDto snapshot = new CommitSnapshotDto();
            snapshot.setId(localCommit.getId());           // optional
            snapshot.setParent(localCommit.getParent());
            snapshot.setMessage(localCommit.getMessage());
            snapshot.setTimestamp(localCommit.getTimestamp());
            snapshot.setFiles(localCommit.getFiles());

            // 5) Blob-ları topla (hash -> base64(content))
            Map<String, String> blobMap = new HashMap<>();
            if (localCommit.getFiles() != null) {
                for (String blobHash : localCommit.getFiles().values()) {
                    if (blobMap.containsKey(blobHash)) {
                        continue; // eyni hash-i bir dəfə göndər
                    }
                    byte[] content = objectStore.readBlob(blobHash);
                    String base64 = Base64.getEncoder().encodeToString(content);
                    blobMap.put(blobHash, base64);
                }
            }

            // 6) Fetch remote HEAD to determine base commit
            String remoteHead = getRemoteHead(remoteConfig, creds, headInfo.branch);

            // 7) Push request DTO
            PushRequestDto pushRequest = new PushRequestDto();
            pushRequest.setBranch(headInfo.branch);
            pushRequest.setBaseCommitId(remoteHead); // remote HEAD-i göndəririk
            pushRequest.setNewCommit(snapshot);
            pushRequest.setBlobs(blobMap);

            // 8) Remote push
            PushResultDto result = remotePushService.push(remoteConfig, creds, pushRequest);

            System.out.println("✅ Push successful!");
            System.out.println("  Server : " + remoteConfig.getBaseUrl());
            System.out.println("  Repo   : " + remoteConfig.getRepoKey());
            System.out.println("  Branch : " + result.getBranch());
            System.out.println("  New HEAD commit: " + result.getNewHeadCommitId());
            System.out.println("  Fast-forward   : " + result.isFastForward());

        } catch (Exception e) {
            System.out.println("Push failed: " + e.getMessage());
        }
    }

    private static class HeadInfo {
        final String branch;
        final String commitId;

        HeadInfo(String branch, String commitId) {
            this.branch = branch;
            this.commitId = commitId;
        }
    }

    private HeadInfo loadHeadInfo(File vcsDir) throws Exception {
        File headFile = new File(vcsDir, "HEAD");
        if (!headFile.isFile()) {
            throw new IllegalStateException("HEAD file not found in .vcs");
        }

        String headRef = Files.readString(headFile.toPath()).trim(); // "ref: refs/heads/main"
        if (!headRef.startsWith("ref:")) {
            throw new IllegalStateException("Invalid HEAD format: " + headRef);
        }

        String refPath = headRef.replace("ref: ", "").trim(); // "refs/heads/main"
        String branchName = extractBranchName(refPath);

        File refFile = new File(vcsDir, refPath);
        if (!refFile.isFile()) {
            return new HeadInfo(branchName, null); // branch var, commit yoxdur
        }

        String commitId = Files.readString(refFile.toPath()).trim();
        if (commitId.isBlank()) {
            commitId = null;
        }

        return new HeadInfo(branchName, commitId);
    }

    private String extractBranchName(String refPath) {
        if (refPath == null) {
            return "main";
        }
        String prefix = "refs/heads/";
        if (refPath.startsWith(prefix)) {
            return refPath.substring(prefix.length());
        }
        return refPath;
    }

    private CommitModel loadCommitModel(File vcsDir, String commitId) throws Exception {
        File commitFile = new File(vcsDir, "commits/" + commitId + ".json");
        if (!commitFile.isFile()) {
            throw new IllegalStateException("Commit file not found: .vcs/commits/" + commitId + ".json");
        }
        return objectMapper.readValue(commitFile, CommitModel.class);
    }

    private String getRemoteHead(RemoteConfig remoteConfig, CredentialsEntry creds, String branch) {
        try {
            RefsResponseDto refs = remoteCloneService.getRefs(remoteConfig, creds);
            return refs.getBranches().get(branch);
        } catch (Exception e) {
            // Remote branch doesn't exist yet - this is the first push
            return null;
        }
    }
}