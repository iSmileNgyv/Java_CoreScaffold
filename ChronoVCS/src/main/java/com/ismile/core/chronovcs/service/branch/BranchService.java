package com.ismile.core.chronovcs.service.branch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcs.dto.branch.*;
import com.ismile.core.chronovcs.entity.BranchHeadEntity;
import com.ismile.core.chronovcs.entity.CommitEntity;
import com.ismile.core.chronovcs.entity.RepositoryEntity;
import com.ismile.core.chronovcs.exception.BranchAlreadyExistsException;
import com.ismile.core.chronovcs.exception.BranchNotFoundException;
import com.ismile.core.chronovcs.exception.BranchOperationException;
import com.ismile.core.chronovcs.exception.PermissionDeniedException;
import com.ismile.core.chronovcs.repository.BranchHeadRepository;
import com.ismile.core.chronovcs.repository.CommitRepository;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import com.ismile.core.chronovcs.service.graph.CommitGraphService;
import com.ismile.core.chronovcs.service.permission.PermissionService;
import com.ismile.core.chronovcs.service.repository.RepositoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BranchService {

    private final BranchHeadRepository branchHeadRepository;
    private final CommitRepository commitRepository;
    private final RepositoryService repositoryService;
    private final PermissionService permissionService;
    private final CommitGraphService commitGraphService;
    private final ObjectMapper objectMapper;

    /**
     * Create a new branch in the repository.
     */
    @Transactional
    public BranchOperationResponse createBranch(
            AuthenticatedUser user,
            String repoKey,
            CreateBranchRequest request) {

        log.info("Creating branch '{}' in repository '{}'", request.getBranchName(), repoKey);

        // Check permissions
        var permission = permissionService.resolvePermissionOrThrow(user, repoKey);
        if (!permission.isCanCreateBranch()) {
            throw new PermissionDeniedException("User does not have permission to create branches");
        }

        // Get repository
        RepositoryEntity repository = repositoryService.getByKeyOrThrow(repoKey);

        // Check if branch already exists
        if (branchHeadRepository.findByRepositoryAndBranch(repository, request.getBranchName()).isPresent()) {
            throw new BranchAlreadyExistsException(request.getBranchName());
        }

        // Determine starting commit
        String startCommitId = determineStartCommit(repository, request);

        // Create branch head entity
        BranchHeadEntity branchHead = BranchHeadEntity.builder()
                .repository(repository)
                .branch(request.getBranchName())
                .headCommitId(startCommitId)
                .updatedAt(LocalDateTime.now())
                .build();

        branchHead = branchHeadRepository.save(branchHead);

        log.info("Successfully created branch '{}' at commit '{}'", request.getBranchName(), startCommitId);

        return BranchOperationResponse.builder()
                .success(true)
                .message("Branch created successfully")
                .branch(mapToBranchResponse(branchHead, repository.getDefaultBranch(), null, repository))
                .details("Created from commit: " + (startCommitId != null ? startCommitId : "repository default"))
                .build();
    }

    /**
     * List all branches in a repository.
     */
    @Transactional(readOnly = true)
    public BranchListResponse listBranches(AuthenticatedUser user, String repoKey) {

        log.info("Listing branches for repository '{}'", repoKey);

        // Check permissions
        var permission = permissionService.resolvePermissionOrThrow(user, repoKey);
        if (!permission.isCanRead()) {
            throw new PermissionDeniedException("User does not have permission to read branches");
        }

        // Get repository
        RepositoryEntity repository = repositoryService.getByKeyOrThrow(repoKey);

        // Get all branches
        List<BranchHeadEntity> branches = branchHeadRepository.findAllByRepository(repository);

        // Map to response
        List<BranchResponse> branchResponses = branches.stream()
                .map(branch -> mapToBranchResponse(branch, repository.getDefaultBranch(), null, repository))
                .collect(Collectors.toList());

        log.info("Found {} branches in repository '{}'", branches.size(), repoKey);

        return BranchListResponse.builder()
                .branches(branchResponses)
                .defaultBranch(repository.getDefaultBranch())
                .currentBranch(null) // This would be client-specific
                .totalCount(branches.size())
                .build();
    }

    /**
     * Get information about a specific branch.
     */
    @Transactional(readOnly = true)
    public BranchResponse getBranch(AuthenticatedUser user, String repoKey, String branchName) {

        log.info("Getting branch '{}' from repository '{}'", branchName, repoKey);

        // Check permissions
        var permission = permissionService.resolvePermissionOrThrow(user, repoKey);
        if (!permission.isCanRead()) {
            throw new PermissionDeniedException("User does not have permission to read branches");
        }

        // Get repository
        RepositoryEntity repository = repositoryService.getByKeyOrThrow(repoKey);

        // Find branch
        BranchHeadEntity branchHead = branchHeadRepository
                .findByRepositoryAndBranch(repository, branchName)
                .orElseThrow(() -> new BranchNotFoundException(branchName));

        return mapToBranchResponse(branchHead, repository.getDefaultBranch(), null, repository);
    }

    /**
     * Delete a branch from the repository.
     */
    @Transactional
    public BranchOperationResponse deleteBranch(
            AuthenticatedUser user,
            String repoKey,
            DeleteBranchRequest request) {

        log.info("Deleting branch '{}' from repository '{}'", request.getBranchName(), repoKey);

        // Check permissions
        var permission = permissionService.resolvePermissionOrThrow(user, repoKey);
        if (!permission.isCanDeleteBranch()) {
            throw new PermissionDeniedException("User does not have permission to delete branches");
        }

        // Get repository
        RepositoryEntity repository = repositoryService.getByKeyOrThrow(repoKey);

        // Prevent deleting default branch
        if (request.getBranchName().equals(repository.getDefaultBranch())) {
            throw new BranchOperationException("Cannot delete the default branch: " + request.getBranchName());
        }

        // Find and delete branch
        BranchHeadEntity branchHead = branchHeadRepository
                .findByRepositoryAndBranch(repository, request.getBranchName())
                .orElseThrow(() -> new BranchNotFoundException(request.getBranchName()));

        // TODO: If not force, check for unmerged changes
        if (!request.isForce()) {
            // This would require commit graph analysis to check if branch is merged
            log.warn("Unmerged change detection not yet implemented");
        }

        branchHeadRepository.delete(branchHead);

        log.info("Successfully deleted branch '{}'", request.getBranchName());

        return BranchOperationResponse.builder()
                .success(true)
                .message("Branch deleted successfully")
                .branch(mapToBranchResponse(branchHead, repository.getDefaultBranch(), null, repository))
                .details("Deleted branch at commit: " + branchHead.getHeadCommitId())
                .build();
    }

    /**
     * Update the HEAD of a branch (used for commits/pushes).
     */
    @Transactional
    public void updateBranchHead(RepositoryEntity repository, String branchName, String newCommitId) {

        log.info("Updating branch '{}' HEAD to '{}'", branchName, newCommitId);

        BranchHeadEntity branchHead = branchHeadRepository
                .findByRepositoryAndBranch(repository, branchName)
                .orElseGet(() -> {
                    // Create branch if it doesn't exist
                    log.info("Branch '{}' does not exist, creating it", branchName);
                    return BranchHeadEntity.builder()
                            .repository(repository)
                            .branch(branchName)
                            .build();
                });

        branchHead.setHeadCommitId(newCommitId);
        branchHead.setUpdatedAt(LocalDateTime.now());

        branchHeadRepository.save(branchHead);

        log.info("Successfully updated branch '{}' HEAD", branchName);
    }

    /**
     * Switch to a different branch (conceptual - tracking current branch is client-side).
     * This method can be used to validate branch exists and optionally create it.
     */
    @Transactional
    public BranchOperationResponse switchBranch(
            AuthenticatedUser user,
            String repoKey,
            SwitchBranchRequest request) {

        log.info("Switching to branch '{}' in repository '{}'", request.getBranchName(), repoKey);

        // Check permissions
        var permission = permissionService.resolvePermissionOrThrow(user, repoKey);
        if (!permission.isCanRead()) {
            throw new PermissionDeniedException("User does not have permission to read branches");
        }

        // Get repository
        RepositoryEntity repository = repositoryService.getByKeyOrThrow(repoKey);

        // Find or create branch
        BranchHeadEntity branchHead = branchHeadRepository
                .findByRepositoryAndBranch(repository, request.getBranchName())
                .orElseGet(() -> {
                    if (request.isCreateIfNotExists()) {
                        if (!permission.isCanCreateBranch()) {
                            throw new PermissionDeniedException("User does not have permission to create branches");
                        }

                        log.info("Branch '{}' does not exist, creating it", request.getBranchName());

                        String startCommit = request.getFromCommitId();
                        if (startCommit == null) {
                            // Use default branch HEAD
                            BranchHeadEntity defaultBranch = branchHeadRepository
                                    .findByRepositoryAndBranch(repository, repository.getDefaultBranch())
                                    .orElse(null);
                            startCommit = defaultBranch != null ? defaultBranch.getHeadCommitId() : null;
                        }

                        BranchHeadEntity newBranch = BranchHeadEntity.builder()
                                .repository(repository)
                                .branch(request.getBranchName())
                                .headCommitId(startCommit)
                                .updatedAt(LocalDateTime.now())
                                .build();

                        return branchHeadRepository.save(newBranch);
                    } else {
                        throw new BranchNotFoundException(request.getBranchName());
                    }
                });

        log.info("Successfully switched to branch '{}'", request.getBranchName());

        return BranchOperationResponse.builder()
                .success(true)
                .message("Switched to branch successfully")
                .branch(mapToBranchResponse(branchHead, repository.getDefaultBranch(), request.getBranchName(), repository))
                .details("Current HEAD: " + branchHead.getHeadCommitId())
                .build();
    }

    /**
     * Analyze a merge without performing it.
     */
    @Transactional(readOnly = true)
    public MergeAnalysisResponse analyzeMerge(
            AuthenticatedUser user,
            String repoKey,
            String sourceBranch,
            String targetBranch) {

        log.info("Analyzing merge: {} -> {} in repository '{}'", sourceBranch, targetBranch, repoKey);

        // Check permissions
        var permission = permissionService.resolvePermissionOrThrow(user, repoKey);
        if (!permission.isCanRead()) {
            throw new PermissionDeniedException("User does not have permission to read branches");
        }

        // Get repository
        RepositoryEntity repository = repositoryService.getByKeyOrThrow(repoKey);

        // Get branches
        BranchHeadEntity sourceHead = branchHeadRepository
                .findByRepositoryAndBranch(repository, sourceBranch)
                .orElseThrow(() -> new BranchNotFoundException(sourceBranch));

        String targetBranchName = targetBranch != null ? targetBranch : repository.getDefaultBranch();
        BranchHeadEntity targetHead = branchHeadRepository
                .findByRepositoryAndBranch(repository, targetBranchName)
                .orElseThrow(() -> new BranchNotFoundException(targetBranchName));

        // Perform analysis
        return commitGraphService.analyzeMerge(repository, targetHead.getHeadCommitId(), sourceHead.getHeadCommitId());
    }

    /**
     * Merge one branch into another with full conflict detection.
     */
    @Transactional
    public BranchOperationResponse mergeBranch(
            AuthenticatedUser user,
            String repoKey,
            MergeBranchRequest request) {

        log.info("Merging branch '{}' into '{}' in repository '{}'",
                request.getSourceBranch(), request.getTargetBranch(), repoKey);

        // Check permissions
        var permission = permissionService.resolvePermissionOrThrow(user, repoKey);
        if (!permission.isCanMerge()) {
            throw new PermissionDeniedException("User does not have permission to merge branches");
        }

        // Get repository
        RepositoryEntity repository = repositoryService.getByKeyOrThrow(repoKey);

        // Get source branch
        BranchHeadEntity sourceBranch = branchHeadRepository
                .findByRepositoryAndBranch(repository, request.getSourceBranch())
                .orElseThrow(() -> new BranchNotFoundException(request.getSourceBranch()));

        // Determine target branch
        String targetBranchName = request.getTargetBranch() != null
                ? request.getTargetBranch()
                : repository.getDefaultBranch();

        BranchHeadEntity targetBranch = branchHeadRepository
                .findByRepositoryAndBranch(repository, targetBranchName)
                .orElseThrow(() -> new BranchNotFoundException(targetBranchName));

        String sourceCommitId = sourceBranch.getHeadCommitId();
        String targetCommitId = targetBranch.getHeadCommitId();

        if (sourceCommitId == null || targetCommitId == null) {
            throw new BranchOperationException("Cannot merge: one or both branches have no commits");
        }

        // Analyze merge
        MergeAnalysisResponse analysis = commitGraphService.analyzeMerge(repository, targetCommitId, sourceCommitId);

        // Check if already up to date
        if (sourceCommitId.equals(targetCommitId)) {
            return BranchOperationResponse.builder()
                    .success(true)
                    .message("Already up-to-date")
                    .branch(mapToBranchResponse(targetBranch, repository.getDefaultBranch(), null, repository))
                    .details("Source and target are at the same commit")
                    .build();
        }

        // Handle fast-forward merge
        if (analysis.isCanFastForward() && "fast-forward".equals(request.getStrategy())) {
            log.info("Performing fast-forward merge");
            targetBranch.setHeadCommitId(sourceCommitId);
            targetBranch.setUpdatedAt(LocalDateTime.now());
            branchHeadRepository.save(targetBranch);

            return BranchOperationResponse.builder()
                    .success(true)
                    .message("Fast-forward merge completed successfully")
                    .branch(mapToBranchResponse(targetBranch, repository.getDefaultBranch(), null, repository))
                    .details(String.format("Fast-forwarded from %s to %s", targetCommitId, sourceCommitId))
                    .build();
        }

        // Check for conflicts
        if (!analysis.isCanAutoMerge() && !request.isAutoResolve()) {
            throw new BranchOperationException(
                    String.format("Merge has %d conflict(s). Please resolve conflicts manually or use autoResolve=true",
                            analysis.getConflicts().size())
            );
        }

        // Get commits for merge
        CommitEntity sourceCommit = commitRepository.findByRepositoryAndCommitId(repository, sourceCommitId)
                .orElseThrow(() -> new BranchOperationException("Source commit not found"));
        CommitEntity targetCommit = commitRepository.findByRepositoryAndCommitId(repository, targetCommitId)
                .orElseThrow(() -> new BranchOperationException("Target commit not found"));
        CommitEntity baseCommit = commitRepository.findByRepositoryAndCommitId(repository, analysis.getMergeBase())
                .orElse(null);

        // Create merged snapshot
        Map<String, String> mergedFiles;
        try {
            Map<String, String> baseFiles = baseCommit != null
                    ? objectMapper.readValue(baseCommit.getFilesJson(), Map.class)
                    : Map.of();
            Map<String, String> targetFiles = objectMapper.readValue(targetCommit.getFilesJson(), Map.class);
            Map<String, String> sourceFiles = objectMapper.readValue(sourceCommit.getFilesJson(), Map.class);

            mergedFiles = commitGraphService.createMergedSnapshot(baseFiles, targetFiles, sourceFiles);
        } catch (Exception e) {
            throw new BranchOperationException("Failed to create merged snapshot: " + e.getMessage());
        }

        // Create merge commit
        String mergeCommitId = UUID.randomUUID().toString().replace("-", "");
        String mergeMessage = request.getMessage() != null
                ? request.getMessage()
                : String.format("Merge branch '%s' into '%s'", request.getSourceBranch(), targetBranchName);

        try {
            CommitEntity mergeCommit = CommitEntity.builder()
                    .repository(repository)
                    .commitId(mergeCommitId)
                    .parentCommitId(targetCommitId) // Primary parent is target
                    .branch(targetBranchName)
                    .message(mergeMessage)
                    .timestamp(LocalDateTime.now().toString())
                    .filesJson(objectMapper.writeValueAsString(mergedFiles))
                    .build();

            commitRepository.save(mergeCommit);

            // Update target branch HEAD
            targetBranch.setHeadCommitId(mergeCommitId);
            targetBranch.setUpdatedAt(LocalDateTime.now());
            branchHeadRepository.save(targetBranch);

            log.info("Merge completed successfully. Created merge commit: {}", mergeCommitId);

            return BranchOperationResponse.builder()
                    .success(true)
                    .message("Merge completed successfully")
                    .branch(mapToBranchResponse(targetBranch, repository.getDefaultBranch(), null, repository))
                    .details(String.format("Merged %d file(s). Merge commit: %s",
                            mergedFiles.size(), mergeCommitId))
                    .build();

        } catch (Exception e) {
            throw new BranchOperationException("Failed to create merge commit: " + e.getMessage());
        }
    }

    /**
     * Determine the starting commit for a new branch.
     */
    private String determineStartCommit(RepositoryEntity repository, CreateBranchRequest request) {
        // If fromBranch is specified, use its HEAD
        if (request.getFromBranch() != null) {
            BranchHeadEntity sourceBranch = branchHeadRepository
                    .findByRepositoryAndBranch(repository, request.getFromBranch())
                    .orElseThrow(() -> new BranchNotFoundException(request.getFromBranch()));
            return sourceBranch.getHeadCommitId();
        }

        // If fromCommitId is specified, use it
        if (request.getFromCommitId() != null) {
            return request.getFromCommitId();
        }

        // Otherwise, use default branch HEAD
        BranchHeadEntity defaultBranch = branchHeadRepository
                .findByRepositoryAndBranch(repository, repository.getDefaultBranch())
                .orElse(null);

        return defaultBranch != null ? defaultBranch.getHeadCommitId() : null;
    }

    /**
     * Map BranchHeadEntity to BranchResponse DTO.
     */
    private BranchResponse mapToBranchResponse(
            BranchHeadEntity branchHead,
            String defaultBranch,
            String currentBranch,
            RepositoryEntity repository) {

        Integer ahead = null;
        Integer behind = null;

        // Calculate commits ahead/behind relative to default branch
        if (!branchHead.getBranch().equals(defaultBranch) && branchHead.getHeadCommitId() != null) {
            try {
                BranchHeadEntity defaultBranchHead = branchHeadRepository
                        .findByRepositoryAndBranch(repository, defaultBranch)
                        .orElse(null);

                if (defaultBranchHead != null && defaultBranchHead.getHeadCommitId() != null) {
                    CommitDistance distance = commitGraphService.calculateDistance(
                            repository,
                            defaultBranchHead.getHeadCommitId(),
                            branchHead.getHeadCommitId()
                    );
                    ahead = distance.getAhead();
                    behind = distance.getBehind();
                }
            } catch (Exception e) {
                log.warn("Failed to calculate commit distance for branch {}: {}", branchHead.getBranch(), e.getMessage());
            }
        }

        return BranchResponse.builder()
                .branchName(branchHead.getBranch())
                .headCommitId(branchHead.getHeadCommitId())
                .isDefault(branchHead.getBranch().equals(defaultBranch))
                .isCurrent(branchHead.getBranch().equals(currentBranch))
                .updatedAt(branchHead.getUpdatedAt())
                .commitsAhead(ahead)
                .commitsBehind(behind)
                .build();
    }
}
