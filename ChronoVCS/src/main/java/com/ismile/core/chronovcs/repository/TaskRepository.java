package com.ismile.core.chronovcs.repository;

import com.ismile.core.chronovcs.entity.TaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, Long> {

    Optional<TaskEntity> findByExternalIdAndTaskIntegrationId(String externalId, Long taskIntegrationId);

    List<TaskEntity> findByTaskIntegrationId(Long taskIntegrationId);

    List<TaskEntity> findByExternalIdIn(List<String> externalIds);
}
