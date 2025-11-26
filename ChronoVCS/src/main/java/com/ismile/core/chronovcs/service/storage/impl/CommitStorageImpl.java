package com.ismile.core.chronovcs.service.storage.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcs.dto.push.CommitSnapshotDto;
import com.ismile.core.chronovcs.entity.BranchHeadEntity;
import com.ismile.core.chronovcs.entity.CommitEntity;
import com.ismile.core.chronovcs.entity.RepositoryEntity;
import com.ismile.core.chronovcs.repository.BranchHeadRepository;
import com.ismile.core.chronovcs.repository.CommitRepository;
import com.ismile.core.chronovcs.service.storage.CommitStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommitStorageImpl implements CommitStorage {

    private final CommitRepository commitRepository;
    private final BranchHeadRepository branchHeadRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void saveCommit(RepositoryEntity repo, String branch, CommitSnapshotDto commit) {
        if (commit.getId() == null || commit.getId().isBlank()) {
            throw new IllegalArgumentException("Commit id is required");
        }

        // already exists?
        boolean exists = commitRepository
                .findByRepositoryAndCommitId(repo, commit.getId())
                .isPresent();

        if (exists) {
            // idempotent – nothing to do
            return;
        }

        String filesJson;
        try {
            filesJson = objectMapper.writeValueAsString(
                    commit.getFiles() != null ? commit.getFiles() : java.util.Map.of()
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize commit files to JSON", e);
        }

        CommitEntity entity = CommitEntity.builder()
                .repository(repo)
                .commitId(commit.getId())
                .parentCommitId(commit.getParent())
                .branch(branch)
                .message(commit.getMessage())
                .timestamp(commit.getTimestamp())
                .filesJson(filesJson)
                .build();

        commitRepository.save(entity);
    }

    @Override
    public String getBranchHead(RepositoryEntity repo, String branch) {
        return branchHeadRepository
                .findByRepositoryAndBranch(repo, branch)
                .map(BranchHeadEntity::getHeadCommitId)
                .orElse(null);
    }

    @Override
    public void updateBranchHead(RepositoryEntity repo, String branch, String commitId) {
        BranchHeadEntity entity = branchHeadRepository
                .findByRepositoryAndBranch(repo, branch)
                .orElseGet(() -> BranchHeadEntity.builder()
                        .repository(repo)
                        .branch(branch)
                        .build()
                );

        entity.setHeadCommitId(commitId);
        branchHeadRepository.save(entity);
    }
}