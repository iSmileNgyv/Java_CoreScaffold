package com.ismile.core.auth.service;

import com.ismile.core.auth.repository.RoleAccessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service to handle authorization logic.
 * It checks if a user with a given set of roles has permission to access an operation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorizationCheckService {

    private final RoleAccessRepository roleAccessRepository;

    /**
     * Checks permission based on user roles and the requested operation.
     *
     * @param userRoles     The list of roles assigned to the authenticated user.
     * @param operationCode The unique identifier for the operation (the gRPC full method name).
     * @return true if the user is authorized, false otherwise.
     */
    @Transactional(readOnly = true)
    public boolean hasPermission(List<String> userRoles, String operationCode) {
        if (userRoles == null || userRoles.isEmpty()) {
            log.warn("Authorization check failed: User has no roles. Operation: {}", operationCode);
            return false;
        }

        boolean hasAccess = roleAccessRepository.existsByRoleCodeInAndOperationCodeAndIsActiveTrue(userRoles, operationCode);

        if (!hasAccess) {
            log.warn("ACCESS DENIED for roles {} on operation {}", userRoles, operationCode);
        } else {
            log.debug("Access granted for roles {} on operation {}", userRoles, operationCode);
        }

        return hasAccess;
    }
}
