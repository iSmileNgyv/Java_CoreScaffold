package com.ismile.core.chronovcs.service.clone;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcs.dto.clone.BatchObjectsResponseDto;
import com.ismile.core.chronovcs.dto.clone.CommitHistoryResponseDto;
import com.ismile.core.chronovcs.dto.clone.RefsResponseDto;
import com.ismile.core.chronovcs.dto.push.CommitSnapshotDto;
import com.ismile.core.chronovcs.entity.BlobEntity;
import com.ismile.core.chronovcs.entity.BranchHeadEntity;
import com.ismile.core.chronovcs.entity.CommitEntity;
import com.ismile.core.chronovcs.entity.RepositoryEntity;
import com.ismile.core.chronovcs.repository.BlobRepository;
import com.ismile.core.chronovcs.repository.BranchHeadRepository;
import com.ismile.core.chronovcs.repository.CommitRepository;
import com.ismile.core.chronovcs.repository.RepositoryRepository;
import com.ismile.core.chronovcs.service.storage.BlobStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloneService {

    private final RepositoryRepository repositoryRepository;
    private final BranchHeadRepository branchHeadRepository;
    private final CommitRepository commitRepository;
    private final BlobRepository blobRepository;
    private final BlobStorageService blobStorageService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public RefsResponseDto getRefs(String repoKey) {
        RepositoryEntity repo = repositoryRepository.findByRepoKey(repoKey)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repoKey));

        List<BranchHeadEntity> branches = branchHeadRepository.findAllByRepository(repo);

        Map<String, String> branchMap = new HashMap<>();
        for (BranchHeadEntity branch : branches) {
            if (branch.getHeadCommitId() != null) {
                branchMap.put(branch.getBranch(), branch.getHeadCommitId());
            }
        }

        return RefsResponseDto.builder()
                .defaultBranch(repo.getDefaultBranch())
                .branches(branchMap)
                .build();
    }

    @Transactional(readOnly = true)
    public CommitSnapshotDto getCommit(String repoKey, String commitHash) {
        RepositoryEntity repo = repositoryRepository.findByRepoKey(repoKey)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repoKey));

        CommitEntity commit = commitRepository.findByRepositoryAndCommitId(repo, commitHash)
                .orElseThrow(() -> new IllegalArgumentException("Commit not found: " + commitHash));

        return mapToDto(commit);
    }

    @Transactional(readOnly = true)
    public CommitHistoryResponseDto getCommitHistory(String repoKey, String branch, Integer limit, String fromCommit) {
        RepositoryEntity repo = repositoryRepository.findByRepoKey(repoKey)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repoKey));

        String startCommitId;
        if (fromCommit != null) {
            startCommitId = fromCommit;
        } else {
            BranchHeadEntity branchHead = branchHeadRepository.findByRepositoryAndBranch(repo, branch)
                    .orElseThrow(() -> new IllegalArgumentException("Branch not found: " + branch));
            startCommitId = branchHead.getHeadCommitId();
            if (startCommitId == null) {
                return CommitHistoryResponseDto.builder()
                        .commits(Collections.emptyList())
                        .hasMore(false)
                        .build();
            }
        }

        int maxLimit = limit != null ? limit : 100;
        List<CommitSnapshotDto> commits = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String currentCommitId = startCommitId;

        while (currentCommitId != null && commits.size() < maxLimit) {
            if (visited.contains(currentCommitId)) {
                log.warn("Circular commit reference detected at: {}", currentCommitId);
                break;
            }
            visited.add(currentCommitId);

            Optional<CommitEntity> commitOpt = commitRepository.findByRepositoryAndCommitId(repo, currentCommitId);
            if (commitOpt.isEmpty()) {
                log.warn("Commit not found in history: {}", currentCommitId);
                break;
            }

            CommitEntity commit = commitOpt.get();
            commits.add(mapToDto(commit));

            currentCommitId = commit.getParentCommitId();
        }

        boolean hasMore = currentCommitId != null;

        return CommitHistoryResponseDto.builder()
                .commits(commits)
                .hasMore(hasMore)
                .build();
    }

    @Transactional(readOnly = true)
    public BatchObjectsResponseDto getBatchObjects(String repoKey, List<String> hashes) {
        RepositoryEntity repo = repositoryRepository.findByRepoKey(repoKey)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repoKey));

        Map<String, String> objects = new HashMap<>();

        for (String hash : hashes) {
            Optional<BlobEntity> blobOpt = blobRepository.findByRepositoryAndHash(repo, hash);
            if (blobOpt.isPresent()) {
                BlobEntity blob = blobOpt.get();
                try {
                    byte[] content = blobStorageService.loadContent(blob);
                    String base64Content = Base64.getEncoder().encodeToString(content);
                    objects.put(hash, base64Content);
                } catch (Exception e) {
                    log.error("Failed to load blob content for hash: {}", hash, e);
                }
            } else {
                log.warn("Blob not found: {}", hash);
            }
        }

        return BatchObjectsResponseDto.builder()
                .objects(objects)
                .build();
    }

    private CommitSnapshotDto mapToDto(CommitEntity entity) {
        Map<String, String> files;
        try {
            files = objectMapper.readValue(entity.getFilesJson(), new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.error("Failed to parse files JSON for commit: {}", entity.getCommitId(), e);
            files = Collections.emptyMap();
        }

        CommitSnapshotDto dto = new CommitSnapshotDto();
        dto.setId(entity.getCommitId());
        dto.setParent(entity.getParentCommitId());
        dto.setBranch(entity.getBranch());
        dto.setMessage(entity.getMessage());
        dto.setTimestamp(entity.getTimestamp());
        dto.setFiles(files);

        return dto;
    }
}
