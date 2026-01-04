package com.ismile.core.chronovcs.controller;

import com.ismile.core.chronovcs.dto.diff.DiffResponse;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import com.ismile.core.chronovcs.service.diff.DiffService;
import com.ismile.core.chronovcs.service.permission.PermissionService;
import com.ismile.core.chronovcs.web.CurrentUser;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/repositories/{repoKey}")
@RequiredArgsConstructor
@Validated
@Slf4j
public class DiffController {

    private final DiffService diffService;
    private final PermissionService permissionService;

    /**
     * Compare two references (commits, branches, or tags).
     * GET /api/repositories/{repoKey}/compare/{base}...{head}
     *
     * Examples:
     * - /api/repositories/my-repo/compare/main...feature
     * - /api/repositories/my-repo/compare/abc123...def456
     * - /api/repositories/my-repo/compare/v1.0.0...v2.0.0
     */
    @GetMapping("/compare/{base}...{head}")
    public ResponseEntity<DiffResponse> compareReferences(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey,
            @PathVariable String base,
            @PathVariable String head,
            @RequestParam(required = false, defaultValue = "false") boolean patch,
            @RequestParam(required = false, defaultValue = "false") boolean ignoreWhitespace
    ) {
        log.info("Comparing {}...{} in repository {} (patch={}, ignoreWhitespace={})",
                base, head, repoKey, patch, ignoreWhitespace);

        // Check permissions
        permissionService.assertCanRead(user, repoKey);

        DiffResponse response = diffService.compare(repoKey, base, head, patch, ignoreWhitespace);
        return ResponseEntity.ok(response);
    }

    /**
     * Get diff for a specific commit (compared to its parent).
     * GET /api/repositories/{repoKey}/commits/{commitId}/diff
     */
    @GetMapping("/commits/{commitId}/diff")
    public ResponseEntity<DiffResponse> getCommitDiff(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey,
            @PathVariable String commitId,
            @RequestParam(required = false, defaultValue = "false") boolean patch,
            @RequestParam(required = false, defaultValue = "false") boolean ignoreWhitespace
    ) {
        log.info("Getting diff for commit {} in repository {} (patch={}, ignoreWhitespace={})",
                commitId, repoKey, patch, ignoreWhitespace);

        // Check permissions
        permissionService.assertCanRead(user, repoKey);

        DiffResponse response = diffService.getCommitDiff(repoKey, commitId, patch, ignoreWhitespace);
        return ResponseEntity.ok(response);
    }

    /**
     * Alternative endpoint for compare using query parameters.
     * GET /api/repositories/{repoKey}/compare?base=main&head=feature&patch=true
     */
    @GetMapping("/compare")
    public ResponseEntity<DiffResponse> compareWithParams(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey,
            @RequestParam String base,
            @RequestParam String head,
            @RequestParam(required = false, defaultValue = "false") boolean patch,
            @RequestParam(required = false, defaultValue = "false") boolean ignoreWhitespace
    ) {
        log.info("Comparing base={} head={} in repository {} (patch={}, ignoreWhitespace={})",
                base, head, repoKey, patch, ignoreWhitespace);

        // Check permissions
        permissionService.assertCanRead(user, repoKey);

        DiffResponse response = diffService.compare(repoKey, base, head, patch, ignoreWhitespace);
        return ResponseEntity.ok(response);
    }
}
