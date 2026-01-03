package com.ismile.core.chronovcs.service.repository;

import com.ismile.core.chronovcs.entity.RepositoryEntity;
import com.ismile.core.chronovcs.repository.BlobRepository;
import com.ismile.core.chronovcs.repository.BranchHeadRepository;
import com.ismile.core.chronovcs.repository.CommitRepository;
import com.ismile.core.chronovcs.repository.RepoPermissionRepository;
import com.ismile.core.chronovcs.repository.RepositoryRepository;
import com.ismile.core.chronovcs.repository.RepositorySettingsRepository;
import com.ismile.core.chronovcs.repository.TokenPermissionRepository;
import com.ismile.core.chronovcs.repository.ReleaseRepository;
import com.ismile.core.chronovcs.repository.ReleaseTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RepositoryDeleteService {

    private final RepositoryRepository repositoryRepository;
    private final RepositorySettingsRepository settingsRepository;
    private final BranchHeadRepository branchHeadRepository;
    private final CommitRepository commitRepository;
    private final BlobRepository blobRepository;
    private final RepoPermissionRepository repoPermissionRepository;
    private final TokenPermissionRepository tokenPermissionRepository;
    private final ReleaseRepository releaseRepository;
    private final ReleaseTaskRepository releaseTaskRepository;

    @Transactional
    public void deleteRepository(String repoKey) {
        RepositoryEntity repository = repositoryRepository.findByRepoKey(repoKey)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repoKey));

        releaseRepository.findByRepositoryIdOrderByCreatedAtDesc(repository.getId())
                .forEach(release -> releaseTaskRepository.deleteByReleaseId(release.getId()));
        releaseRepository.deleteAll(releaseRepository.findByRepositoryIdOrderByCreatedAtDesc(repository.getId()));

        tokenPermissionRepository.deleteAllByRepositoryId(repository.getId());
        repoPermissionRepository.deleteAllByRepository(repository);
        settingsRepository.deleteByRepositoryId(repository.getId());
        branchHeadRepository.deleteAllByRepository(repository);
        commitRepository.deleteAllByRepository(repository);
        blobRepository.deleteAllByRepository(repository);

        repositoryRepository.delete(repository);
    }
}
