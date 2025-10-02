package com.ismile.core.auth.security;

import com.ismile.core.auth.exception.SecurityException;
import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

/**
 * Input validation utility for user registration and login
 * Prevents XSS, SQL injection, and validates format
 */
@UtilityClass
public class InputValidator {

    // Username: 3-20 chars, alphanumeric + underscore
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,20}$");

    // Name/Surname: 2-50 chars, letters only
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-ZçÇğĞıİöÖşŞüÜ\\s]{2,50}$");

    // Azerbaijan phone number: +994XXXXXXXXX or 0XXXXXXXXX
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(\\+994|0)[0-9]{9}$");

    /**
     * Validate username
     */
    public static void validateUsername(String username) {
        if (username == null || username.isEmpty()) {
            throw new SecurityException("Username cannot be empty");
        }

        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new SecurityException(
                    "Username must be 3-20 characters and contain only letters, numbers, and underscores"
            );
        }
    }

    /**
     * Validate name or surname
     */
    public static void validateName(String name, String fieldName) {
        if (name == null || name.isEmpty()) {
            throw new SecurityException(fieldName + " cannot be empty");
        }

        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new SecurityException(
                    fieldName + " must be 2-50 characters and contain only letters"
            );
        }
    }

    /**
     * Validate phone number (Azerbaijan format)
     */
    public static void validatePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return; // Phone number is optional
        }

        if (!PHONE_PATTERN.matcher(phoneNumber).matches()) {
            throw new SecurityException(
                    "Invalid phone number format. Use: +994XXXXXXXXX or 0XXXXXXXXX"
            );
        }
    }

    /**
     * Sanitize input to prevent XSS/SQL injection
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }

        return input
                .trim()
                .replaceAll("<", "&lt;")
                .replaceAll(">", "&gt;")
                .replaceAll("\"", "&quot;")
                .replaceAll("'", "&#x27;")
                .replaceAll("/", "&#x2F;");
    }

    /**
     * Normalize phone number to standard format
     */
    public static String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return phoneNumber;
        }

        // Convert 0XXXXXXXXX to +994XXXXXXXXX
        if (phoneNumber.startsWith("0")) {
            return "+994" + phoneNumber.substring(1);
        }

        return phoneNumber;
    }
}