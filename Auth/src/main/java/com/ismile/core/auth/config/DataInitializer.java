package com.ismile.core.auth.config;

import com.ismile.core.auth.entity.RoleEntity;
import com.ismile.core.auth.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Initialize default data on application startup
 * Creates default roles if they don't exist
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;

    @Override
    public void run(String... args) {
        createDefaultRoles();
    }

    /**
     * Create default roles
     */
    private void createDefaultRoles() {
        createRoleIfNotExists("USER", "User", true);
        createRoleIfNotExists("ADMIN", "Administrator", false);
        createRoleIfNotExists("SUPER_ADMIN", "Super Administrator", false);

        log.info("Default roles initialized");
    }

    /**
     * Create role if it doesn't exist
     */
    private void createRoleIfNotExists(String code, String name, boolean isDefault) {
        if (!roleRepository.existsById(code)) {
            RoleEntity role = new RoleEntity();
            role.setCode(code);
            role.setName(name);
            role.setDefaultRole(isDefault);
            roleRepository.save(role);

            log.info("Created role: {} ({})", name, code);
        }
    }
}