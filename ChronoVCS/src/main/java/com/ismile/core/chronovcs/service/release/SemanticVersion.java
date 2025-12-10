package com.ismile.core.chronovcs.service.release;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SemanticVersion {
    private int major;
    private int minor;
    private int patch;

    public static SemanticVersion parse(String version) {
        if (version == null || version.trim().isEmpty()) {
            return new SemanticVersion(0, 0, 0);
        }

        String[] parts = version.trim().split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid semantic version format: " + version);
        }

        try {
            return new SemanticVersion(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
            );
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid semantic version format: " + version, e);
        }
    }

    public SemanticVersion incrementMajor() {
        return new SemanticVersion(major + 1, 0, 0);
    }

    public SemanticVersion incrementMinor() {
        return new SemanticVersion(major, minor + 1, 0);
    }

    public SemanticVersion incrementPatch() {
        return new SemanticVersion(major, minor, patch + 1);
    }

    public SemanticVersion increment(String type) {
        return switch (type.toUpperCase()) {
            case "MAJOR" -> incrementMajor();
            case "MINOR" -> incrementMinor();
            case "PATCH" -> incrementPatch();
            default -> throw new IllegalArgumentException("Invalid version type: " + type);
        };
    }

    @Override
    public String toString() {
        return String.format("%d.%d.%d", major, minor, patch);
    }
}
