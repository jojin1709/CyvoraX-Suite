package com.venomproxy.update;

import java.util.Objects;

public final class SemanticVersion implements Comparable<SemanticVersion> {
    private final int major;
    private final int minor;
    private final int patch;
    private final String preRelease;
    private final String original;

    private SemanticVersion(int major, int minor, int patch, String preRelease, String original) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preRelease = preRelease == null ? "" : preRelease;
        this.original = original;
    }

    public static SemanticVersion parse(String value) {
        String source = value == null || value.isBlank() ? "0.0.0" : value.trim();
        String normalized = source.startsWith("v") || source.startsWith("V") ? source.substring(1) : source;
        String[] releaseAndPre = normalized.split("-", 2);
        String[] parts = releaseAndPre[0].split("\\.");
        int major = numberAt(parts, 0);
        int minor = numberAt(parts, 1);
        int patch = numberAt(parts, 2);
        String preRelease = releaseAndPre.length > 1 ? releaseAndPre[1] : "";
        return new SemanticVersion(major, minor, patch, preRelease, source);
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int majorCompare = Integer.compare(major, other.major);
        if (majorCompare != 0) {
            return majorCompare;
        }
        int minorCompare = Integer.compare(minor, other.minor);
        if (minorCompare != 0) {
            return minorCompare;
        }
        int patchCompare = Integer.compare(patch, other.patch);
        if (patchCompare != 0) {
            return patchCompare;
        }
        if (preRelease.isBlank() && !other.preRelease.isBlank()) {
            return 1;
        }
        if (!preRelease.isBlank() && other.preRelease.isBlank()) {
            return -1;
        }
        return preRelease.compareToIgnoreCase(other.preRelease);
    }

    public boolean isNewerThan(SemanticVersion current) {
        return compareTo(current) > 0;
    }

    @Override
    public String toString() {
        return original;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SemanticVersion version)) {
            return false;
        }
        return major == version.major && minor == version.minor && patch == version.patch
                && preRelease.equals(version.preRelease);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, preRelease);
    }

    private static int numberAt(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        String digits = parts[index].replaceAll("[^0-9].*$", "");
        if (digits.isBlank()) {
            return 0;
        }
        return Integer.parseInt(digits);
    }
}
