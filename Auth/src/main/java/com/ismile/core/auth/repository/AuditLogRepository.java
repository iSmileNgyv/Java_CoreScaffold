package com.ismile.core.auth.repository;

import com.ismile.core.auth.entity.AuditLogEntity;
import com.ismile.core.auth.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    List<AuditLogEntity> findByUserOrderByCreatedAtDesc(UserEntity user);

    List<AuditLogEntity> findByEventTypeOrderByCreatedAtDesc(AuditLogEntity.AuditEventType eventType);

    List<AuditLogEntity> findByIpAddressOrderByCreatedAtDesc(String ipAddress);

    List<AuditLogEntity> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);
}