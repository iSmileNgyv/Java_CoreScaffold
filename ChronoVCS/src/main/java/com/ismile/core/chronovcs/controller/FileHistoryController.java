package com.ismile.core.chronovcs.controller;

import com.ismile.core.chronovcs.dto.history.BlameResponse;
import com.ismile.core.chronovcs.dto.history.FileHistoryResponse;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import com.ismile.core.chronovcs.service.history.FileHistoryService;
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
public class FileHistoryController {

    private final FileHistoryService fileHistoryService;
    private final PermissionService permissionService;

    /**
     * Get file history for a specific file.
     * GET /api/repositories/{repoKey}/files/{filePath}/history
     *
     * Query parameters:
     * - branch: Branch name (default: repository default branch)
     * - limit: Maximum number of commits to return (default: 100)
     *
     * Examples:
     * - /api/repositories/my-repo/files/src/main.js/history
     * - /api/repositories/my-repo/files/src/main.js/history?branch=feature&limit=50
     */
    @GetMapping("/files/{filePath}/history")
    public ResponseEntity<FileHistoryResponse> getFileHistory(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey,
            @PathVariable String filePath,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) Integer limit
    ) {
        log.info("Getting file history for {} in repository {} (branch={})", filePath, repoKey, branch);

        // Check permissions
        permissionService.assertCanRead(user, repoKey);

        FileHistoryResponse response = fileHistoryService.getFileHistory(repoKey, filePath, branch, limit);
        return ResponseEntity.ok(response);
    }

    /**
     * Get blame/annotation for a file.
     * GET /api/repositories/{repoKey}/files/{filePath}/blame
     *
     * Query parameters:
     * - commit: Commit ID or branch name (default: repository default branch)
     *
     * Examples:
     * - /api/repositories/my-repo/files/src/main.js/blame
     * - /api/repositories/my-repo/files/src/main.js/blame?commit=abc123
     * - /api/repositories/my-repo/files/src/main.js/blame?commit=feature
     */
    @GetMapping("/files/{filePath}/blame")
    public ResponseEntity<BlameResponse> getFileBlame(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey,
            @PathVariable String filePath,
            @RequestParam(required = false) String commit
    ) {
        log.info("Getting blame for {} in repository {} (commit={})", filePath, repoKey, commit);

        // Check permissions
        permissionService.assertCanRead(user, repoKey);

        // Default to main branch if not specified
        String commitOrBranch = commit != null ? commit : "main";

        BlameResponse response = fileHistoryService.getBlame(repoKey, filePath, commitOrBranch);
        return ResponseEntity.ok(response);
    }

    /**
     * Alternative endpoint with wildcard path support.
     * GET /api/repositories/{repoKey}/blame?path=src/main.js&commit=main
     */
    @GetMapping("/blame")
    public ResponseEntity<BlameResponse> getBlameByQuery(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey,
            @RequestParam String path,
            @RequestParam(required = false, defaultValue = "main") String commit
    ) {
        log.info("Getting blame for {} in repository {} (commit={})", path, repoKey, commit);

        // Check permissions
        permissionService.assertCanRead(user, repoKey);

        BlameResponse response = fileHistoryService.getBlame(repoKey, path, commit);
        return ResponseEntity.ok(response);
    }

    /**
     * Alternative endpoint for file history with wildcard path support.
     * GET /api/repositories/{repoKey}/history?path=src/main.js&branch=main&limit=50
     */
    @GetMapping("/history")
    public ResponseEntity<FileHistoryResponse> getHistoryByQuery(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey,
            @RequestParam String path,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false, defaultValue = "100") Integer limit
    ) {
        log.info("Getting file history for {} in repository {} (branch={})", path, repoKey, branch);

        // Check permissions
        permissionService.assertCanRead(user, repoKey);

        FileHistoryResponse response = fileHistoryService.getFileHistory(repoKey, path, branch, limit);
        return ResponseEntity.ok(response);
    }
}
