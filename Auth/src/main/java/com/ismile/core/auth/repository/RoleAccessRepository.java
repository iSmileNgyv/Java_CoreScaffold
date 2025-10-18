package com.ismile.core.auth.repository;

import com.ismile.core.auth.entity.RoleAccessEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleAccessRepository extends JpaRepository<RoleAccessEntity, Long> {
    /**
     * Checks if any of the given roles have active access to the specified operation.
     * This is the core query for our dynamic RBAC system.
     * @param roleCodes A list of role codes belonging to the user.
     * @param operationCode The full method name of the gRPC call (e.g., "auth.AuthService/SayHello").
     * @return True if access is granted, false otherwise.
     */
    boolean existsByRoleCodeInAndOperationCodeAndIsActiveTrue(List<String> roleCodes, String operationCode);
}
