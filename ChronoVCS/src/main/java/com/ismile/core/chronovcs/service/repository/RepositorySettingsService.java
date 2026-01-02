package com.ismile.core.chronovcs.service.repository;

import com.ismile.core.chronovcs.dto.repository.RepositorySettingsResponseDto;
import com.ismile.core.chronovcs.dto.repository.UpdateRepositorySettingsRequestDto;
import com.ismile.core.chronovcs.entity.ReleaseEntity;
import com.ismile.core.chronovcs.entity.RepositoryEntity;
import com.ismile.core.chronovcs.entity.RepositorySettingsEntity;
import com.ismile.core.chronovcs.repository.ReleaseRepository;
import com.ismile.core.chronovcs.repository.RepositorySettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RepositorySettingsService {

    private final RepositoryService repositoryService;
    private final RepositorySettingsRepository settingsRepository;
    private final ReleaseRepository releaseRepository;

    @Transactional
    public RepositorySettingsResponseDto getSettings(String repoKey) {
        RepositoryEntity repository = repositoryService.getByKeyOrThrow(repoKey);
        RepositorySettingsEntity settings = getOrCreateSettings(repository);
        return mapToResponse(settings);
    }

    @Transactional
    public RepositorySettingsResponseDto updateSettings(String repoKey, UpdateRepositorySettingsRequestDto request) {
        RepositoryEntity repository = repositoryService.getByKeyOrThrow(repoKey);
        RepositorySettingsEntity settings = getOrCreateSettings(repository);

        boolean releaseEnabledChanged = false;

        if (request.getReleaseEnabled() != null
                && !request.getReleaseEnabled().equals(settings.getReleaseEnabled())) {
            settings.setReleaseEnabled(request.getReleaseEnabled());
            releaseEnabledChanged = true;
        }
        if (request.getTaskRequired() != null) {
            settings.setTaskRequired(request.getTaskRequired());
        }
        if (request.getAutoIncrement() != null) {
            settings.setAutoIncrement(request.getAutoIncrement());
        }
        if (request.getEnforceSemanticVersioning() != null) {
            settings.setEnforceSemanticVersioning(request.getEnforceSemanticVersioning());
        }

        RepositorySettingsEntity saved = settingsRepository.save(settings);

        if (releaseEnabledChanged) {
            List<ReleaseEntity> releases =
                    releaseRepository.findByRepositoryIdOrderByCreatedAtDesc(repository.getId());
            if (!releases.isEmpty()) {
                releaseRepository.deleteAll(releases);
            }
        }

        return mapToResponse(saved);
    }

    @Transactional
    public RepositorySettingsEntity getOrCreateSettings(RepositoryEntity repository) {
        return settingsRepository.findByRepositoryId(repository.getId())
                .orElseGet(() -> settingsRepository.save(
                        RepositorySettingsEntity.builder()
                                .repository(repository)
                                .build()
                ));
    }

    private RepositorySettingsResponseDto mapToResponse(RepositorySettingsEntity settings) {
        return RepositorySettingsResponseDto.builder()
                .releaseEnabled(Boolean.TRUE.equals(settings.getReleaseEnabled()))
                .taskRequired(Boolean.TRUE.equals(settings.getTaskRequired()))
                .autoIncrement(Boolean.TRUE.equals(settings.getAutoIncrement()))
                .enforceSemanticVersioning(Boolean.TRUE.equals(settings.getEnforceSemanticVersioning()))
                .build();
    }
}
