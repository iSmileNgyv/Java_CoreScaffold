package com.ismile.core.chronovcscli.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CredentialsService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Path getConfigFilePath() {
        String home = System.getProperty("user.home");
        return Paths.get(home, ".vcs", "credentials.json");
    }

    private CredentialsStore loadStore() {
        try {
            Path path = getConfigFilePath();
            File file = path.toFile();

            if (!file.exists()) {
                return new CredentialsStore();
            }

            return objectMapper.readValue(file, CredentialsStore.class);
        } catch (Exception e) {
            log.warn("Failed to load credentials store: {}", e.getMessage());
            return new CredentialsStore();
        }
    }

    private void saveStore(CredentialsStore store) {
        try {
            Path path = getConfigFilePath();
            File file = path.toFile();

            File dir = file.getParentFile();
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("Could not create directory: " + dir);
            }

            objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(file, store);

            try {
                // Try to restrict permissions (Unix)
                Files.setPosixFilePermissions(path,
                        java.util.Set.of(
                                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
                        )
                );
            } catch (Exception ignored) {
                // On Windows or unsupported FS – ignore
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to save credentials store: " + e.getMessage(), e);
        }
    }

    public Optional<CredentialsEntry> findForServer(String baseUrl) {
        CredentialsStore store = loadStore();
        return store.getServers()
                .stream()
                .filter(c -> baseUrl.equalsIgnoreCase(c.getBaseUrl()))
                .findFirst();
    }

    public void saveOrUpdate(CredentialsEntry entry) {
        CredentialsStore store = loadStore();

        store.getServers().removeIf(c ->
                c.getBaseUrl().equalsIgnoreCase(entry.getBaseUrl())
        );
        store.getServers().add(entry);

        saveStore(store);
    }
}