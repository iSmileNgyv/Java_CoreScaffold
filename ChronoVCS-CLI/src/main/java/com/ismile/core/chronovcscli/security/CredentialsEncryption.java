package com.ismile.core.chronovcscli.security;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for CLI credentials
 *
 * Security features:
 * - AES-256-GCM authenticated encryption
 * - Per-user key stored in ~/.vcs/master.key
 * - Automatic key generation on first use
 * - Platform-specific file permissions
 */
@Slf4j
public class CredentialsEncryption {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_IV_LENGTH = 12; // bytes
    private static final int AES_KEY_SIZE = 256; // bits

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    public CredentialsEncryption() {
        this.secureRandom = new SecureRandom();
        this.secretKey = loadOrCreateMasterKey();
    }

    /**
     * Get path to master key file
     */
    private Path getMasterKeyPath() {
        String home = System.getProperty("user.home");
        return Paths.get(home, ".vcs", "master.key");
    }

    /**
     * Load existing master key or create new one
     */
    private SecretKey loadOrCreateMasterKey() {
        Path keyPath = getMasterKeyPath();
        File keyFile = keyPath.toFile();

        try {
            if (keyFile.exists()) {
                // Load existing key
                byte[] keyBytes = Files.readAllBytes(keyPath);
                byte[] decodedKey = Base64.getDecoder().decode(keyBytes);
                log.debug("Loaded existing master key from {}", keyPath);
                return new SecretKeySpec(decodedKey, "AES");
            } else {
                // Generate new key
                log.info("Generating new master key for credentials encryption");
                SecretKey newKey = generateKey();
                saveMasterKey(newKey, keyPath);
                return newKey;
            }
        } catch (Exception e) {
            log.error("Failed to load/create master key", e);
            throw new RuntimeException("Failed to initialize encryption", e);
        }
    }

    /**
     * Generate a new AES-256 key
     */
    private SecretKey generateKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(AES_KEY_SIZE, secureRandom);
            return keyGen.generateKey();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate encryption key", e);
        }
    }

    /**
     * Save master key to file with restricted permissions
     */
    private void saveMasterKey(SecretKey key, Path keyPath) throws IOException {
        File keyFile = keyPath.toFile();
        File dir = keyFile.getParentFile();

        // Create directory if needed
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create directory: " + dir);
        }

        // Encode and save key
        byte[] encoded = key.getEncoded();
        String base64Key = Base64.getEncoder().encodeToString(encoded);
        Files.write(keyPath, base64Key.getBytes(StandardCharsets.UTF_8));

        // Restrict permissions (Unix/Linux/macOS only)
        try {
            Files.setPosixFilePermissions(keyPath,
                    java.util.Set.of(
                            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
                    )
            );
            log.debug("Set restrictive permissions on master key file");
        } catch (Exception e) {
            // Windows or unsupported FS - ignore
            log.warn("Could not set POSIX permissions (Windows?): {}", e.getMessage());
        }

        log.info("Saved master key to {}", keyPath);
    }

    /**
     * Encrypt plaintext using AES-256-GCM
     *
     * @param plaintext Text to encrypt
     * @return Base64-encoded encrypted data with IV
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }

        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // Encrypt
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Combine IV + ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            // Encode to Base64
            return Base64.getEncoder().encodeToString(byteBuffer.array());

        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Failed to encrypt credentials", e);
        }
    }

    /**
     * Decrypt ciphertext using AES-256-GCM
     *
     * @param encryptedData Base64-encoded encrypted data
     * @return Decrypted plaintext
     */
    public String decrypt(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) {
            return encryptedData;
        }

        try {
            // Decode from Base64
            byte[] decoded = Base64.getDecoder().decode(encryptedData);

            // Extract IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // Decrypt
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new RuntimeException("Failed to decrypt credentials", e);
        }
    }

    /**
     * Check if data appears to be encrypted
     */
    public boolean isEncrypted(String data) {
        if (data == null || data.isEmpty()) {
            return false;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(data);
            return decoded.length > (GCM_IV_LENGTH + 16);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
