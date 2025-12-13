package com.ismile.core.chronovcs.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption service for securing sensitive data
 *
 * Security features:
 * - AES-256-GCM authenticated encryption
 * - Random IV (Initialization Vector) for each encryption
 * - Authentication tag for integrity verification
 * - Base64 encoding for storage compatibility
 */
@Service
@Slf4j
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_IV_LENGTH = 12; // bytes (96 bits recommended for GCM)
    private static final int AES_KEY_SIZE = 256; // bits

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    /**
     * Initialize encryption service with master key from environment
     *
     * @param masterKeyBase64 Base64-encoded 256-bit AES key from environment variable
     */
    public EncryptionService(@Value("${chronovcs.security.master-key:}") String masterKeyBase64) {
        this.secureRandom = new SecureRandom();
        this.secretKey = initializeKey(masterKeyBase64);
    }

    /**
     * Initialize the encryption key from config or generate temporary
     */
    private SecretKey initializeKey(String masterKeyBase64) {
        if (masterKeyBase64 == null || masterKeyBase64.trim().isEmpty()) {
            log.warn("=".repeat(80));
            log.warn("WARNING: No master key configured! Using temporary generated key.");
            log.warn("THIS IS NOT SECURE FOR PRODUCTION!");
            log.warn("Set environment variable: CHRONOVCS_SECURITY_MASTER_KEY");
            log.warn("Generate a key with: generateMasterKey() method");
            log.warn("=".repeat(80));
            return generateTemporaryKey();
        }

        try {
            byte[] decodedKey = Base64.getDecoder().decode(masterKeyBase64);
            if (decodedKey.length != 32) { // 256 bits = 32 bytes
                throw new IllegalArgumentException("Master key must be 256 bits (32 bytes)");
            }
            log.info("Encryption service initialized with configured master key");
            return new SecretKeySpec(decodedKey, "AES");
        } catch (Exception e) {
            log.error("Failed to load master key, using temporary key", e);
            return generateTemporaryKey();
        }
    }

    /**
     * Generate a temporary key (for development/testing only)
     */
    private SecretKey generateTemporaryKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(AES_KEY_SIZE, secureRandom);
            return keyGen.generateKey();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate temporary encryption key", e);
        }
    }

    /**
     * Generate a new master key for production use
     *
     * @return Base64-encoded 256-bit AES key
     */
    public static String generateMasterKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256, new SecureRandom());
            SecretKey key = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate master key", e);
        }
    }

    /**
     * Encrypt plaintext using AES-256-GCM
     *
     * Format: [IV (12 bytes)][Ciphertext + Auth Tag]
     * Output: Base64-encoded combined data
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

            // Encode to Base64 for storage
            return Base64.getEncoder().encodeToString(byteBuffer.array());

        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Failed to encrypt data", e);
        }
    }

    /**
     * Decrypt ciphertext using AES-256-GCM
     *
     * @param encryptedData Base64-encoded encrypted data with IV
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
            throw new RuntimeException("Failed to decrypt data", e);
        }
    }

    /**
     * Check if data appears to be encrypted
     *
     * @param data Data to check
     * @return true if data looks like it's encrypted (Base64 with correct length)
     */
    public boolean isEncrypted(String data) {
        if (data == null || data.isEmpty()) {
            return false;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(data);
            // Encrypted data should have at least IV (12 bytes) + minimal ciphertext + tag (16 bytes)
            return decoded.length > (GCM_IV_LENGTH + 16);
        } catch (IllegalArgumentException e) {
            // Not valid Base64
            return false;
        }
    }
}
