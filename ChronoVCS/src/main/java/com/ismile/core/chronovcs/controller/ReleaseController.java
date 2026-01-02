package com.ismile.core.chronovcs.controller;

import com.ismile.core.chronovcs.dto.release.CreateReleaseRequest;
import com.ismile.core.chronovcs.dto.release.RecommendVersionResponse;
import com.ismile.core.chronovcs.dto.release.ReleaseResponse;
import com.ismile.core.chronovcs.service.release.ReleaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for Release management
 */
@RestController
@RequestMapping("/api/repositories/{repoKey}/releases")
@RequiredArgsConstructor
@Slf4j
public class ReleaseController {

    private final ReleaseService releaseService;

    /**
     * Get releases for repository
     * GET /api/repositories/{repoKey}/releases
     */
    @GetMapping
    public ResponseEntity<List<ReleaseResponse>> getReleases(@PathVariable String repoKey) {
        log.info("GET /api/repositories/{}/releases - Fetching releases", repoKey);
        List<ReleaseResponse> releases = releaseService.getReleases(repoKey);
        return ResponseEntity.ok(releases);
    }

    /**
     * Get the latest release for repository
     * GET /api/repositories/{repoKey}/releases/latest
     */
    @GetMapping("/latest")
    public ResponseEntity<ReleaseResponse> getLatestRelease(@PathVariable String repoKey) {
        log.info("GET /api/repositories/{}/releases/latest - Fetching latest release", repoKey);
        ReleaseResponse release = releaseService.getLatestRelease(repoKey);
        return ResponseEntity.ok(release);
    }

    /**
     * Get a specific release by version
     * GET /api/repositories/{repoKey}/releases/{version}
     */
    @GetMapping("/{version}")
    public ResponseEntity<ReleaseResponse> getRelease(
            @PathVariable String repoKey,
            @PathVariable String version
    ) {
        log.info("GET /api/repositories/{}/releases/{} - Fetching release", repoKey, version);
        ReleaseResponse release = releaseService.getRelease(repoKey, version);
        return ResponseEntity.ok(release);
    }

    /**
     * Recommend version based on JIRA tasks
     * GET /api/repositories/{repoKey}/releases/recommend?jiraIssues=PROJ-123,PROJ-456
     */
    @GetMapping("/recommend")
    public ResponseEntity<RecommendVersionResponse> recommendVersion(
            @PathVariable String repoKey,
            @RequestParam(required = false) List<String> jiraIssues
    ) {
        log.info("GET /api/repositories/{}/releases/recommend - Recommending version for {} tasks",
                repoKey, jiraIssues != null ? jiraIssues.size() : 0);

        RecommendVersionResponse recommendation = releaseService.recommendVersion(repoKey, jiraIssues);
        return ResponseEntity.ok(recommendation);
    }

    /**
     * Create a new release
     * POST /api/repositories/{repoKey}/releases
     */
    @PostMapping
    public ResponseEntity<ReleaseResponse> createRelease(
            @PathVariable String repoKey,
            @RequestBody CreateReleaseRequest request,
            Authentication authentication
    ) {
        log.info("POST /api/repositories/{}/releases - Creating release version: {}",
                repoKey, request.getVersion());

        String userEmail = authentication != null ? authentication.getName() : "system";
        ReleaseResponse release = releaseService.createRelease(repoKey, request, userEmail);

        return ResponseEntity.ok(release);
    }
}
