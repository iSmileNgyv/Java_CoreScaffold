package com.ismile.core.auth.security;

import com.ismile.core.auth.exception.SecurityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * Password validation with security best practices
 * Mitigates CVE-2025-22228 (BCrypt DoS via long passwords)
 */
@RequiredArgsConstructor
@Slf4j
public class PasswordValidator {

    private final int minLength;
    private final int maxLength;

    // Password must contain: uppercase, lowercase, digit, special char
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]+$"
    );

    /**
     * Validate password meets security requirements
     * @param password Raw password to validate
     * @throws SecurityException if password is invalid
     */
    public void validate(String password) {
        if (password == null || password.isEmpty()) {
            throw new SecurityException("Password cannot be empty");
        }

        // Length validation (CVE-2025-22228 mitigation)
        if (password.length() < minLength) {
            throw new SecurityException(
                    String.format("Password must be at least %d characters", minLength)
            );
        }

        if (password.length() > maxLength) {
            log.warn("Password length exceeds maximum: {} > {}", password.length(), maxLength);
            throw new SecurityException(
                    String.format("Password must not exceed %d characters", maxLength)
            );
        }

        // Complexity validation
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new SecurityException(
                    "Password must contain uppercase, lowercase, digit and special character (@$!%*?&#)"
            );
        }

        // Check for common weak passwords
        if (isCommonPassword(password)) {
            throw new SecurityException("Password is too common, please choose a stronger one");
        }
    }

    /**
     * Check if password is in common weak passwords list
     */
    private boolean isCommonPassword(String password) {
        String[] commonPasswords = {
                "Password1!", "Welcome1!", "Admin123!", "User1234!",
                "Qwerty123!", "Abc12345!", "Pass1234!", "Test1234!"
        };

        for (String common : commonPasswords) {
            if (password.equalsIgnoreCase(common)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Truncate password if exceeds max length (fallback)
     * Use with caution - prefer rejection
     */
    public String truncate(String password) {
        if (password != null && password.length() > maxLength) {
            log.warn("Truncating password from {} to {} characters", password.length(), maxLength);
            return password.substring(0, maxLength);
        }
        return password;
    }
}