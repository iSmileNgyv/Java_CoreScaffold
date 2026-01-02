package com.ismile.core.chronovcs.service.clone;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcs.dto.clone.BatchObjectsResponseDto;
import com.ismile.core.chronovcs.dto.clone.CommitHistoryResponseDto;
import com.ismile.core.chronovcs.dto.clone.RefsResponseDto;
import com.ismile.core.chronovcs.dto.push.CommitFileEntryDto;
import com.ismile.core.chronovcs.dto.push.CommitSnapshotDto;
import com.ismile.core.chronovcs.dto.tree.TreeEntryDto;
import com.ismile.core.chronovcs.dto.tree.TreeResponseDto;
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

        return mapToDto(commit, repo.getRepoKey(), true);
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
            commits.add(mapToDto(commit, repo.getRepoKey(), false));

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

    @Transactional(readOnly = true)
    public TreeResponseDto getTree(String repoKey, String ref, String path) {
        RepositoryEntity repo = repositoryRepository.findByRepoKey(repoKey)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repoKey));

        String resolvedRef = (ref == null || ref.isBlank()) ? repo.getDefaultBranch() : ref;
        String commitId = resolveCommitId(repo, resolvedRef);

        if (commitId == null || commitId.isBlank()) {
            return new TreeResponseDto(repoKey, resolvedRef, null, normalizePath(path), Collections.emptyList());
        }

        CommitEntity commit = commitRepository.findByRepositoryAndCommitId(repo, commitId)
                .orElseThrow(() -> new IllegalArgumentException("Commit not found: " + commitId));

        Map<String, String> files = parseFilesJson(commit);
        List<TreeEntryDto> entries = buildTreeEntries(repoKey, files, normalizePath(path));

        return new TreeResponseDto(repoKey, resolvedRef, commitId, normalizePath(path), entries);
    }

    private String resolveCommitId(RepositoryEntity repo, String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }

        Optional<BranchHeadEntity> branchOpt = branchHeadRepository.findByRepositoryAndBranch(repo, ref);
        if (branchOpt.isPresent()) {
            return branchOpt.get().getHeadCommitId();
        }

        Optional<CommitEntity> commitOpt = commitRepository.findByRepositoryAndCommitId(repo, ref);
        if (commitOpt.isPresent()) {
            return ref;
        }

        throw new IllegalArgumentException("Ref not found: " + ref);
    }

    private List<TreeEntryDto> buildTreeEntries(String repoKey,
                                                 Map<String, String> files,
                                                 String normalizedPath) {
        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }

        String prefix = normalizedPath.isEmpty() ? "" : normalizedPath + "/";
        Map<String, TreeEntryDto> entries = new HashMap<>();
        TreeEntryDto exactFile = null;

        for (Map.Entry<String, String> entry : files.entrySet()) {
            String fullPath = entry.getKey();
            String hash = entry.getValue();

            if (!normalizedPath.isEmpty() && fullPath.equals(normalizedPath)) {
                exactFile = new TreeEntryDto(
                        normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1),
                        normalizedPath,
                        "FILE",
                        hash,
                        "/api/repositories/" + repoKey + "/blobs/" + hash
                );
                continue;
            }

            if (!prefix.isEmpty() && !fullPath.startsWith(prefix)) {
                continue;
            }

            String rest = prefix.isEmpty() ? fullPath : fullPath.substring(prefix.length());
            if (rest.isEmpty()) {
                continue;
            }

            int slashIndex = rest.indexOf('/');
            if (slashIndex >= 0) {
                String dirName = rest.substring(0, slashIndex);
                String dirPath = normalizedPath.isEmpty() ? dirName : normalizedPath + "/" + dirName;
                entries.putIfAbsent(dirPath, new TreeEntryDto(dirName, dirPath, "DIR", null, null));
            } else {
                String fileName = rest;
                String filePath = normalizedPath.isEmpty() ? fileName : normalizedPath + "/" + fileName;
                String url = "/api/repositories/" + repoKey + "/blobs/" + hash;
                entries.putIfAbsent(filePath, new TreeEntryDto(fileName, filePath, "FILE", hash, url));
            }
        }

        if (exactFile != null && entries.isEmpty()) {
            return List.of(exactFile);
        }

        List<TreeEntryDto> result = new ArrayList<>(entries.values());
        result.sort((a, b) -> {
            if (!a.getType().equals(b.getType())) {
                return a.getType().equals("DIR") ? -1 : 1;
            }
            return a.getName().compareToIgnoreCase(b.getName());
        });
        return result;
    }

    private String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.trim();
        if (normalized.equals("/")) {
            return "";
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private Map<String, String> parseFilesJson(CommitEntity entity) {
        Map<String, String> files;
        try {
            files = objectMapper.readValue(entity.getFilesJson(), new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.error("Failed to parse files JSON for commit: {}", entity.getCommitId(), e);
            files = Collections.emptyMap();
        }
        return files;
    }

    private CommitSnapshotDto mapToDto(CommitEntity entity, String repoKey, boolean includeFileEntries) {
        Map<String, String> files = parseFilesJson(entity);

        CommitSnapshotDto dto = new CommitSnapshotDto();
        dto.setId(entity.getCommitId());
        dto.setParent(entity.getParentCommitId());
        dto.setBranch(entity.getBranch());
        dto.setMessage(entity.getMessage());
        dto.setTimestamp(entity.getTimestamp());
        dto.setFiles(files);
        if (includeFileEntries && !files.isEmpty()) {
            List<CommitFileEntryDto> entries = new ArrayList<>();
            List<String> paths = new ArrayList<>(files.keySet());
            Collections.sort(paths);
            for (String path : paths) {
                String hash = files.get(path);
                String url = "/api/repositories/" + repoKey + "/blobs/" + hash;
                entries.add(new CommitFileEntryDto(path, hash, url));
            }
            dto.setFileEntries(entries);
        }

        return dto;
    }
}
