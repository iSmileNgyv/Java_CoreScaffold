package com.ismile.core.chronovcs.controller;

import com.ismile.core.chronovcs.dto.branch.MergeAnalysisResponse;
import com.ismile.core.chronovcs.dto.pullrequest.CreatePullRequestRequest;
import com.ismile.core.chronovcs.dto.pullrequest.MergePullRequestRequest;
import com.ismile.core.chronovcs.dto.pullrequest.PullRequestListResponse;
import com.ismile.core.chronovcs.dto.pullrequest.PullRequestResponseDto;
import com.ismile.core.chronovcs.dto.pullrequest.UpdatePullRequestRequest;
import com.ismile.core.chronovcs.entity.PullRequestStatus;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import com.ismile.core.chronovcs.service.pullrequest.PullRequestService;
import com.ismile.core.chronovcs.web.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/repositories/{repoKey}/pull-requests")
@RequiredArgsConstructor
@Validated
public class PullRequestController {

    private final PullRequestService pullRequestService;

    @PostMapping
    public ResponseEntity<PullRequestResponseDto> createPullRequest(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey,
            @Valid @RequestBody CreatePullRequestRequest request
    ) {
        PullRequestResponseDto response = pullRequestService.createPullRequest(user, repoKey, request);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping
    public ResponseEntity<PullRequestListResponse> listPullRequests(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey,
            @RequestParam(required = false) String status
    ) {
        PullRequestStatus parsedStatus = null;
        if (status != null && !status.isBlank()) {
            parsedStatus = PullRequestStatus.valueOf(status.trim().toUpperCase());
        }
        PullRequestListResponse response = pullRequestService.listPullRequests(user, repoKey, parsedStatus);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{prId}")
    public ResponseEntity<PullRequestResponseDto> getPullRequest(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey,
            @PathVariable Long prId
    ) {
        PullRequestResponseDto response = pullRequestService.getPullRequest(user, repoKey, prId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{prId}")
    public ResponseEntity<PullRequestResponseDto> updatePullRequest(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey,
            @PathVariable Long prId,
            @Valid @RequestBody UpdatePullRequestRequest request
    ) {
        PullRequestResponseDto response = pullRequestService.updatePullRequest(user, repoKey, prId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{prId}/close")
    public ResponseEntity<PullRequestResponseDto> closePullRequest(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey,
            @PathVariable Long prId
    ) {
        PullRequestResponseDto response = pullRequestService.closePullRequest(user, repoKey, prId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{prId}/reopen")
    public ResponseEntity<PullRequestResponseDto> reopenPullRequest(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey,
            @PathVariable Long prId
    ) {
        PullRequestResponseDto response = pullRequestService.reopenPullRequest(user, repoKey, prId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{prId}/merge")
    public ResponseEntity<PullRequestResponseDto> mergePullRequest(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey,
            @PathVariable Long prId,
            @Valid @RequestBody(required = false) MergePullRequestRequest request
    ) {
        PullRequestResponseDto response = pullRequestService.mergePullRequest(user, repoKey, prId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{prId}/analysis")
    public ResponseEntity<MergeAnalysisResponse> analyzePullRequest(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey,
            @PathVariable Long prId
    ) {
        MergeAnalysisResponse response = pullRequestService.analyzePullRequest(user, repoKey, prId);
        return ResponseEntity.ok(response);
    }
}
