package com.ismile.core.chronovcs.repository;

import com.ismile.core.chronovcs.entity.VersionMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VersionMappingRepository extends JpaRepository<VersionMappingEntity, Long> {

    List<VersionMappingEntity> findByTaskIntegrationId(Long taskIntegrationId);

    Optional<VersionMappingEntity> findByTaskIntegrationIdAndFieldNameAndFieldValue(
        Long taskIntegrationId,
        String fieldName,
        String fieldValue
    );

    void deleteByTaskIntegrationId(Long taskIntegrationId);
}
