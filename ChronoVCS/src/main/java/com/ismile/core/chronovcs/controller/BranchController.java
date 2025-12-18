package com.ismile.core.chronovcs.controller;

import com.ismile.core.chronovcs.dto.branch.*;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import com.ismile.core.chronovcs.service.branch.BranchService;
import com.ismile.core.chronovcs.web.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/repositories/{repoKey}/branches")
@RequiredArgsConstructor
@Validated
public class BranchController {

    private final BranchService branchService;

    /**
     * List all branches in a repository.
     * GET /api/repositories/{repoKey}/branches
     */
    @GetMapping
    public ResponseEntity<BranchListResponse> listBranches(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey
    ) {
        BranchListResponse response = branchService.listBranches(user, repoKey);
        return ResponseEntity.ok(response);
    }

    /**
     * Get information about a specific branch.
     * GET /api/repositories/{repoKey}/branches/{branchName}
     */
    @GetMapping("/{branchName}")
    public ResponseEntity<BranchResponse> getBranch(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey,
            @PathVariable String branchName
    ) {
        BranchResponse response = branchService.getBranch(user, repoKey, branchName);
        return ResponseEntity.ok(response);
    }

    /**
     * Create a new branch.
     * POST /api/repositories/{repoKey}/branches
     */
    @PostMapping
    public ResponseEntity<BranchOperationResponse> createBranch(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey,
            @Valid @RequestBody CreateBranchRequest request
    ) {
        BranchOperationResponse response = branchService.createBranch(user, repoKey, request);
        return ResponseEntity.status(201).body(response);
    }

    /**
     * Delete a branch.
     * DELETE /api/repositories/{repoKey}/branches/{branchName}
     */
    @DeleteMapping("/{branchName}")
    public ResponseEntity<BranchOperationResponse> deleteBranch(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey,
            @PathVariable String branchName,
            @RequestParam(required = false, defaultValue = "false") boolean force
    ) {
        DeleteBranchRequest request = DeleteBranchRequest.builder()
                .branchName(branchName)
                .force(force)
                .build();

        BranchOperationResponse response = branchService.deleteBranch(user, repoKey, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Switch to a different branch.
     * POST /api/repositories/{repoKey}/branches/switch
     */
    @PostMapping("/switch")
    public ResponseEntity<BranchOperationResponse> switchBranch(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey,
            @Valid @RequestBody SwitchBranchRequest request
    ) {
        BranchOperationResponse response = branchService.switchBranch(user, repoKey, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Analyze a merge without performing it.
     * GET /api/repositories/{repoKey}/branches/merge/analyze
     */
    @GetMapping("/merge/analyze")
    public ResponseEntity<MergeAnalysisResponse> analyzeMerge(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey,
            @RequestParam String sourceBranch,
            @RequestParam(required = false) String targetBranch
    ) {
        MergeAnalysisResponse response = branchService.analyzeMerge(user, repoKey, sourceBranch, targetBranch);
        return ResponseEntity.ok(response);
    }

    /**
     * Merge one branch into another.
     * POST /api/repositories/{repoKey}/branches/merge
     */
    @PostMapping("/merge")
    public ResponseEntity<BranchOperationResponse> mergeBranch(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey,
            @Valid @RequestBody MergeBranchRequest request
    ) {
        BranchOperationResponse response = branchService.mergeBranch(user, repoKey, request);
        return ResponseEntity.ok(response);
    }
}
