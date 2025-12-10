package com.ismile.core.chronovcs.service.release;

import com.ismile.core.chronovcs.dto.release.*;
import com.ismile.core.chronovcs.entity.*;
import com.ismile.core.chronovcs.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReleaseService {

    private final ReleaseRepository releaseRepository;
    private final ReleaseTaskRepository releaseTaskRepository;
    private final RepositoryRepository repositoryRepository;
    private final RepositorySettingsRepository settingsRepository;
    private final UserRepository userRepository;

    /**
     * Create a new release
     */
    @Transactional
    public ReleaseResponse createRelease(String repoKey, CreateReleaseRequest request, String userEmail) {
        // Get repository
        RepositoryEntity repository = repositoryRepository.findByRepoKey(repoKey)
                .orElseThrow(() -> new RuntimeException("Repository not found: " + repoKey));

        // Get current user
        UserEntity user = userRepository.findByEmailAndActiveTrue(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Determine version
        String version = determineVersion(repository, request);

        // Validate version doesn't exist
        if (releaseRepository.findByRepositoryIdAndVersion(repository.getId(), version).isPresent()) {
            throw new RuntimeException("Release version already exists: " + version);
        }

        // Create release
        ReleaseEntity release = ReleaseEntity.builder()
                .repository(repository)
                .version(version)
                .versionType(request.getVersionType())
                .message(request.getMessage())
                .createdBy(user)
                .build();

        release = releaseRepository.save(release);

        // Create release tasks
        if (request.getJiraIssueKeys() != null && !request.getJiraIssueKeys().isEmpty()) {
            for (String issueKey : request.getJiraIssueKeys()) {
                // TODO: Fetch task details from task integration system
                // For now, use default MINOR version type
                String versionType = "MINOR";

                ReleaseTaskEntity task = ReleaseTaskEntity.builder()
                        .release(release)
                        .jiraIssueKey(issueKey)
                        .jiraIssueType("Story") // TODO: Fetch from task integration
                        .versionType(versionType)
                        .build();

                releaseTaskRepository.save(task);
            }
        }

        return toResponse(release);
    }

    /**
     * Recommend version based on JIRA tasks
     */
    public RecommendVersionResponse recommendVersion(String repoKey, List<String> jiraIssueKeys) {
        RepositoryEntity repository = repositoryRepository.findByRepoKey(repoKey)
                .orElseThrow(() -> new RuntimeException("Repository not found"));

        // Get current version
        String currentVersion = releaseRepository
                .findLatestByRepositoryId(repository.getId())
                .map(ReleaseEntity::getVersion)
                .orElse("0.0.0");

        // Analyze tasks (simplified - in real impl, fetch from JIRA)
        Map<String, Long> breakdown = new HashMap<>();
        breakdown.put("MAJOR", 0L);
        breakdown.put("MINOR", jiraIssueKeys != null ? (long) jiraIssueKeys.size() : 0L);
        breakdown.put("PATCH", 0L);

        // Determine version type
        String versionType;
        String reason;

        if (breakdown.get("MAJOR") > 0) {
            versionType = "MAJOR";
            reason = String.format("%d breaking change(s) detected", breakdown.get("MAJOR"));
        } else if (breakdown.get("MINOR") > 0) {
            versionType = "MINOR";
            reason = String.format("%d new feature(s) added", breakdown.get("MINOR"));
        } else {
            versionType = "PATCH";
            reason = String.format("%d bug fix(es)", breakdown.get("PATCH"));
        }

        // Calculate recommended version
        SemanticVersion current = SemanticVersion.parse(currentVersion);
        SemanticVersion recommended = current.increment(versionType);

        return RecommendVersionResponse.builder()
                .currentVersion(currentVersion)
                .recommendedVersion(recommended.toString())
                .versionType(versionType)
                .reason(reason)
                .breakdown(breakdown)
                .build();
    }

    /**
     * Get releases for repository
     */
    public List<ReleaseResponse> getReleases(String repoKey) {
        RepositoryEntity repository = repositoryRepository.findByRepoKey(repoKey)
                .orElseThrow(() -> new RuntimeException("Repository not found"));

        return releaseRepository.findByRepositoryIdOrderByCreatedAtDesc(repository.getId())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private String determineVersion(RepositoryEntity repository, CreateReleaseRequest request) {
        // If version is explicitly provided, use it
        if (request.getVersion() != null && !request.getVersion().trim().isEmpty()) {
            return request.getVersion().trim();
        }

        // Otherwise, auto-increment based on type
        String currentVersion = releaseRepository
                .findLatestByRepositoryId(repository.getId())
                .map(ReleaseEntity::getVersion)
                .orElse("0.0.0");

        SemanticVersion current = SemanticVersion.parse(currentVersion);

        if (request.getVersionType() != null) {
            return current.increment(request.getVersionType()).toString();
        }

        // Default to MINOR increment
        return current.incrementMinor().toString();
    }

    private ReleaseResponse toResponse(ReleaseEntity entity) {
        List<ReleaseTaskDto> tasks = releaseTaskRepository.findByReleaseId(entity.getId())
                .stream()
                .map(this::toTaskDto)
                .collect(Collectors.toList());

        return ReleaseResponse.builder()
                .id(entity.getId())
                .version(entity.getVersion())
                .versionType(entity.getVersionType())
                .message(entity.getMessage())
                .createdBy(entity.getCreatedBy() != null ? entity.getCreatedBy().getEmail() : null)
                .createdAt(entity.getCreatedAt())
                .tasks(tasks)
                .build();
    }

    private ReleaseTaskDto toTaskDto(ReleaseTaskEntity entity) {
        return ReleaseTaskDto.builder()
                .jiraIssueKey(entity.getJiraIssueKey())
                .jiraIssueType(entity.getJiraIssueType())
                .versionType(entity.getVersionType())
                .build();
    }
}
