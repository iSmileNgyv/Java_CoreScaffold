package com.ismile.core.chronovcs.service.pullrequest;

import com.ismile.core.chronovcs.dto.branch.MergeAnalysisResponse;
import com.ismile.core.chronovcs.dto.branch.MergeBranchRequest;
import com.ismile.core.chronovcs.dto.pullrequest.CreatePullRequestRequest;
import com.ismile.core.chronovcs.dto.pullrequest.MergePullRequestRequest;
import com.ismile.core.chronovcs.dto.pullrequest.PullRequestListResponse;
import com.ismile.core.chronovcs.dto.pullrequest.PullRequestResponseDto;
import com.ismile.core.chronovcs.dto.pullrequest.UpdatePullRequestRequest;
import com.ismile.core.chronovcs.entity.BranchHeadEntity;
import com.ismile.core.chronovcs.entity.PullRequestEntity;
import com.ismile.core.chronovcs.entity.PullRequestStatus;
import com.ismile.core.chronovcs.entity.RepositoryEntity;
import com.ismile.core.chronovcs.entity.UserEntity;
import com.ismile.core.chronovcs.exception.BranchOperationException;
import com.ismile.core.chronovcs.exception.PullRequestNotFoundException;
import com.ismile.core.chronovcs.repository.BranchHeadRepository;
import com.ismile.core.chronovcs.repository.PullRequestRepository;
import com.ismile.core.chronovcs.repository.UserRepository;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import com.ismile.core.chronovcs.service.branch.BranchService;
import com.ismile.core.chronovcs.service.permission.PermissionService;
import com.ismile.core.chronovcs.service.repository.RepositoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PullRequestService {

    private final PullRequestRepository pullRequestRepository;
    private final RepositoryService repositoryService;
    private final BranchHeadRepository branchHeadRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final BranchService branchService;

    @Transactional
    public PullRequestResponseDto createPullRequest(AuthenticatedUser user,
                                                    String repoKey,
                                                    CreatePullRequestRequest request) {
        permissionService.assertCanRead(user, repoKey);
        RepositoryEntity repository = repositoryService.getByKeyOrThrow(repoKey);

        String sourceBranch = request.getSourceBranch();
        String targetBranch = request.getTargetBranch() != null
                ? request.getTargetBranch()
                : repository.getDefaultBranch();

        if (sourceBranch.equals(targetBranch)) {
            throw new IllegalArgumentException("Source and target branches must be different");
        }

        Optional<PullRequestEntity> existing = pullRequestRepository
                .findByRepositoryIdAndSourceBranchAndTargetBranchAndStatus(
                        repository.getId(), sourceBranch, targetBranch, PullRequestStatus.OPEN
                );
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Open pull request already exists for these branches");
        }

        BranchHeadEntity sourceHead = branchHeadRepository
                .findByRepositoryAndBranch(repository, sourceBranch)
                .orElseThrow(() -> new IllegalArgumentException("Source branch not found: " + sourceBranch));

        BranchHeadEntity targetHead = branchHeadRepository
                .findByRepositoryAndBranch(repository, targetBranch)
                .orElseThrow(() -> new IllegalArgumentException("Target branch not found: " + targetBranch));

        UserEntity creator = userRepository.findById(user.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + user.getUserId()));

        PullRequestEntity entity = PullRequestEntity.builder()
                .repository(repository)
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .status(PullRequestStatus.OPEN)
                .sourceBranch(sourceBranch)
                .targetBranch(targetBranch)
                .sourceHeadCommitId(sourceHead.getHeadCommitId())
                .targetHeadCommitId(targetHead.getHeadCommitId())
                .createdBy(creator)
                .build();

        PullRequestEntity saved = pullRequestRepository.save(entity);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PullRequestListResponse listPullRequests(AuthenticatedUser user,
                                                    String repoKey,
                                                    PullRequestStatus status) {
        permissionService.assertCanRead(user, repoKey);
        RepositoryEntity repository = repositoryService.getByKeyOrThrow(repoKey);

        List<PullRequestEntity> pullRequests = status == null
                ? pullRequestRepository.findByRepositoryIdOrderByCreatedAtDesc(repository.getId())
                : pullRequestRepository.findByRepositoryIdAndStatusOrderByCreatedAtDesc(repository.getId(), status);

        List<PullRequestResponseDto> responses = pullRequests.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PullRequestListResponse.builder()
                .pullRequests(responses)
                .totalCount(responses.size())
                .build();
    }

    @Transactional(readOnly = true)
    public PullRequestResponseDto getPullRequest(AuthenticatedUser user, String repoKey, Long prId) {
        permissionService.assertCanRead(user, repoKey);
        PullRequestEntity pullRequest = getPullRequestEntity(repoKey, prId);
        return toResponse(pullRequest);
    }

    @Transactional
    public PullRequestResponseDto updatePullRequest(AuthenticatedUser user,
                                                    String repoKey,
                                                    Long prId,
                                                    UpdatePullRequestRequest request) {
        permissionService.assertCanRead(user, repoKey);
        PullRequestEntity pullRequest = getPullRequestEntity(repoKey, prId);

        if (pullRequest.getStatus() != PullRequestStatus.OPEN) {
            throw new IllegalArgumentException("Only open pull requests can be updated");
        }

        if (request.getTitle() != null) {
            pullRequest.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            pullRequest.setDescription(request.getDescription());
        }
        if (request.getTargetBranch() != null) {
            String newTarget = request.getTargetBranch();
            if (newTarget.equals(pullRequest.getSourceBranch())) {
                throw new IllegalArgumentException("Source and target branches must be different");
            }
            RepositoryEntity repository = pullRequest.getRepository();
            BranchHeadEntity targetHead = branchHeadRepository
                    .findByRepositoryAndBranch(repository, newTarget)
                    .orElseThrow(() -> new IllegalArgumentException("Target branch not found: " + newTarget));
            pullRequest.setTargetBranch(newTarget);
            pullRequest.setTargetHeadCommitId(targetHead.getHeadCommitId());
        }

        PullRequestEntity saved = pullRequestRepository.save(pullRequest);
        return toResponse(saved);
    }

    @Transactional
    public PullRequestResponseDto closePullRequest(AuthenticatedUser user, String repoKey, Long prId) {
        permissionService.assertCanRead(user, repoKey);
        PullRequestEntity pullRequest = getPullRequestEntity(repoKey, prId);

        if (pullRequest.getStatus() == PullRequestStatus.MERGED) {
            throw new IllegalArgumentException("Merged pull requests cannot be closed");
        }

        if (pullRequest.getStatus() != PullRequestStatus.CLOSED) {
            pullRequest.setStatus(PullRequestStatus.CLOSED);
            pullRequest.setClosedAt(LocalDateTime.now());
        }

        PullRequestEntity saved = pullRequestRepository.save(pullRequest);
        return toResponse(saved);
    }

    @Transactional
    public PullRequestResponseDto reopenPullRequest(AuthenticatedUser user, String repoKey, Long prId) {
        permissionService.assertCanRead(user, repoKey);
        PullRequestEntity pullRequest = getPullRequestEntity(repoKey, prId);

        if (pullRequest.getStatus() != PullRequestStatus.CLOSED) {
            throw new IllegalArgumentException("Only closed pull requests can be reopened");
        }

        pullRequest.setStatus(PullRequestStatus.OPEN);
        pullRequest.setClosedAt(null);

        PullRequestEntity saved = pullRequestRepository.save(pullRequest);
        return toResponse(saved);
    }

    @Transactional
    public PullRequestResponseDto mergePullRequest(AuthenticatedUser user,
                                                   String repoKey,
                                                   Long prId,
                                                   MergePullRequestRequest request) {
        permissionService.assertCanMerge(user, repoKey);
        PullRequestEntity pullRequest = getPullRequestEntity(repoKey, prId);

        if (pullRequest.getStatus() != PullRequestStatus.OPEN) {
            throw new IllegalArgumentException("Only open pull requests can be merged");
        }

        MergeBranchRequest mergeRequest = MergeBranchRequest.builder()
                .sourceBranch(pullRequest.getSourceBranch())
                .targetBranch(pullRequest.getTargetBranch())
                .message(request != null ? request.getMessage() : null)
                .strategy(request != null && request.getStrategy() != null ? request.getStrategy() : "no-ff")
                .autoResolve(request != null && request.isAutoResolve())
                .build();

        try {
            var response = branchService.mergeBranch(user, repoKey, mergeRequest);
            String mergeCommitId = response.getBranch() != null ? response.getBranch().getHeadCommitId() : null;

            pullRequest.setStatus(PullRequestStatus.MERGED);
            pullRequest.setMergedAt(LocalDateTime.now());
            pullRequest.setMergeCommitId(mergeCommitId);

            UserEntity merger = userRepository.findById(user.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + user.getUserId()));
            pullRequest.setMergedBy(merger);

            // Update target head snapshot
            RepositoryEntity repository = pullRequest.getRepository();
            BranchHeadEntity targetHead = branchHeadRepository
                    .findByRepositoryAndBranch(repository, pullRequest.getTargetBranch())
                    .orElse(null);
            pullRequest.setTargetHeadCommitId(targetHead != null ? targetHead.getHeadCommitId() : null);

            PullRequestEntity saved = pullRequestRepository.save(pullRequest);
            return toResponse(saved);
        } catch (BranchOperationException ex) {
            throw new IllegalArgumentException(ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public MergeAnalysisResponse analyzePullRequest(AuthenticatedUser user, String repoKey, Long prId) {
        permissionService.assertCanRead(user, repoKey);
        PullRequestEntity pullRequest = getPullRequestEntity(repoKey, prId);
        return branchService.analyzeMerge(
                user,
                repoKey,
                pullRequest.getSourceBranch(),
                pullRequest.getTargetBranch()
        );
    }

    private PullRequestEntity getPullRequestEntity(String repoKey, Long prId) {
        RepositoryEntity repository = repositoryService.getByKeyOrThrow(repoKey);
        return pullRequestRepository.findByRepositoryIdAndId(repository.getId(), prId)
                .orElseThrow(() -> new PullRequestNotFoundException(prId));
    }

    private PullRequestResponseDto toResponse(PullRequestEntity entity) {
        return PullRequestResponseDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .status(entity.getStatus() != null ? entity.getStatus().name() : null)
                .sourceBranch(entity.getSourceBranch())
                .targetBranch(entity.getTargetBranch())
                .sourceHeadCommitId(entity.getSourceHeadCommitId())
                .targetHeadCommitId(entity.getTargetHeadCommitId())
                .mergeCommitId(entity.getMergeCommitId())
                .createdBy(entity.getCreatedBy() != null ? entity.getCreatedBy().getEmail() : null)
                .mergedBy(entity.getMergedBy() != null ? entity.getMergedBy().getEmail() : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .closedAt(entity.getClosedAt())
                .mergedAt(entity.getMergedAt())
                .build();
    }
}
