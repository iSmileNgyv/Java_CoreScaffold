package com.ismile.argusomnicli.variable;

import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

/**
 * Built-in transformation functions.
 * Follows Single Responsibility - only provides transformation functions.
 */
@Component
public class BuiltInFunctions {

    /**
     * Execute a built-in function.
     *
     * @param functionName Function name
     * @param argument Function argument
     * @return Function result
     */
    public String execute(String functionName, String argument) {
        return switch (functionName.toLowerCase()) {
            case "file_hash" -> fileHash(argument);
            case "date" -> date(argument);
            case "base64" -> base64(argument);
            case "uuid" -> uuid();
            case "url_encode" -> urlEncode(argument);
            default -> throw new IllegalArgumentException("Unknown function: " + functionName);
        };
    }

    private String fileHash(String path) {
        try {
            File file = new File(path);
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(fileBytes);
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash file: " + path, e);
        }
    }

    private String date(String format) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
        return LocalDateTime.now().format(formatter);
    }

    private String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes());
    }

    private String uuid() {
        return UUID.randomUUID().toString();
    }

    private String urlEncode(String text) {
        return java.net.URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
