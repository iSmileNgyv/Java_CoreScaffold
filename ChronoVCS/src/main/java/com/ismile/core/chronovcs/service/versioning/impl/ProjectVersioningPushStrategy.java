package com.ismile.core.chronovcs.service.versioning.impl;

import com.ismile.core.chronovcs.dto.push.CommitSnapshotDto;
import com.ismile.core.chronovcs.dto.push.PushRequestDto;
import com.ismile.core.chronovcs.dto.push.PushResultDto;
import com.ismile.core.chronovcs.entity.RepositoryEntity;
import com.ismile.core.chronovcs.entity.VersioningMode;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import com.ismile.core.chronovcs.service.storage.BlobStorageService;
import com.ismile.core.chronovcs.service.storage.CommitStorage;
import com.ismile.core.chronovcs.service.versioning.VersioningPushStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ProjectVersioningPushStrategy implements VersioningPushStrategy {

    private final BlobStorageService blobStorageService;
    private final CommitStorage commitStorage;

    @Override
    public VersioningMode getSupportedMode() {
        return VersioningMode.PROJECT;
    }

    @Override
    public PushResultDto handlePush(
            AuthenticatedUser user,
            RepositoryEntity repo,
            PushRequestDto request
    ) {
        // 1) Validate input
        if (request.getBranch() == null || request.getBranch().isBlank()) {
            throw new IllegalArgumentException("Branch is required for push");
        }

        CommitSnapshotDto newCommit = request.getNewCommit();
        if (newCommit == null) {
            throw new IllegalArgumentException("newCommit is required for push");
        }
        if (newCommit.getId() == null || newCommit.getId().isBlank()) {
            throw new IllegalArgumentException("newCommit.id is required");
        }

        String branch = request.getBranch();

        // 2) Existing HEAD
        String currentHead = commitStorage.getBranchHead(repo, branch);

        boolean fastForward;
        if (request.getBaseCommitId() == null || request.getBaseCommitId().isBlank()) {
            fastForward = (currentHead == null || currentHead.isBlank());
        } else {
            fastForward = request.getBaseCommitId().equals(currentHead);
        }

        // 3) Save blobs (hash → base64 content)
        if (request.getBlobs() != null && !request.getBlobs().isEmpty()) {
            for (Map.Entry<String, String> entry : request.getBlobs().entrySet()) {
                String blobHash = entry.getKey();
                String base64Content = entry.getValue();

                if (blobHash == null || blobHash.isBlank()) {
                    continue;
                }
                if (base64Content == null) {
                    base64Content = "";
                }

                byte[] contentBytes = Base64.getDecoder().decode(
                        base64Content.getBytes(StandardCharsets.UTF_8)
                );

                blobStorageService.saveBlob(
                        repo,
                        blobHash,
                        contentBytes,
                        "application/octet-stream"
                );
            }
        }

        // 4) Ensure timestamp
        if (newCommit.getTimestamp() == null || newCommit.getTimestamp().isBlank()) {
            newCommit.setTimestamp(Instant.now().toString());
        }

        // 5) Save commit metadata
        commitStorage.saveCommit(repo, branch, newCommit);

        // 6) Update HEAD if fast-forward
        if (fastForward) {
            commitStorage.updateBranchHead(repo, branch, newCommit.getId());
        }

        String newHead = fastForward ? newCommit.getId() : currentHead;

        // 7) Result
        return PushResultDto.builder()
                .branch(branch)
                .newHeadCommitId(newHead)
                .fastForward(fastForward)
                .build();
    }
}