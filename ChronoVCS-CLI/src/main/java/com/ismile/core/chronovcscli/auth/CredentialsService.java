package com.ismile.core.chronovcscli.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcscli.security.CredentialsEncryption;
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
    private final CredentialsEncryption encryption = new CredentialsEncryption();

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

            CredentialsStore store = objectMapper.readValue(file, CredentialsStore.class);

            // Decrypt all tokens
            for (CredentialsEntry entry : store.getServers()) {
                if (entry.getToken() != null && !entry.getToken().isEmpty()) {
                    // Check if already encrypted (for backward compatibility)
                    if (encryption.isEncrypted(entry.getToken())) {
                        try {
                            String decrypted = encryption.decrypt(entry.getToken());
                            entry.setToken(decrypted);
                        } catch (Exception e) {
                            log.warn("Failed to decrypt token for {}: {}", entry.getBaseUrl(), e.getMessage());
                        }
                    }
                }
            }

            return store;
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

            // Create a copy of the store with encrypted tokens
            CredentialsStore encryptedStore = new CredentialsStore();
            for (CredentialsEntry entry : store.getServers()) {
                CredentialsEntry encryptedEntry = new CredentialsEntry();
                encryptedEntry.setBaseUrl(entry.getBaseUrl());
                encryptedEntry.setUserUid(entry.getUserUid());
                encryptedEntry.setEmail(entry.getEmail());

                // Encrypt token before saving
                if (entry.getToken() != null && !entry.getToken().isEmpty()) {
                    String encrypted = encryption.encrypt(entry.getToken());
                    encryptedEntry.setToken(encrypted);
                } else {
                    encryptedEntry.setToken(entry.getToken());
                }

                encryptedStore.getServers().add(encryptedEntry);
            }

            objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(file, encryptedStore);

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

            log.debug("Credentials saved with encryption");

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