package com.ismile.core.chronovcs.repository;

import com.ismile.core.chronovcs.entity.TaskIntegrationEntity;
import com.ismile.core.chronovcs.entity.TaskIntegrationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskIntegrationRepository extends JpaRepository<TaskIntegrationEntity, Long> {

    Optional<TaskIntegrationEntity> findByName(String name);

    List<TaskIntegrationEntity> findByEnabled(Boolean enabled);

    List<TaskIntegrationEntity> findByType(TaskIntegrationType type);

    @Query("SELECT t FROM TaskIntegrationEntity t WHERE t.enabled = true AND t.type = :type")
    Optional<TaskIntegrationEntity> findEnabledByType(TaskIntegrationType type);

    @Query("SELECT t FROM TaskIntegrationEntity t LEFT JOIN FETCH t.headers LEFT JOIN FETCH t.versionMappings WHERE t.id = :id")
    Optional<TaskIntegrationEntity> findByIdWithDetails(Long id);
}
