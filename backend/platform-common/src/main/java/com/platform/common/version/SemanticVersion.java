package com.platform.common.version;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a semantic version following the MAJOR.MINOR.PATCH format.
 * Provides parsing, validation, and comparison capabilities for version numbers.
 * 
 * <p>Valid version format: MAJOR.MINOR.PATCH where each component is a non-negative integer.
 * Examples: 1.0.0, 2.3.5, 10.20.30
 */
@Getter
@EqualsAndHashCode
public class SemanticVersion implements Comparable<SemanticVersion> {
    
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");
    
    private final int major;
    private final int minor;
    private final int patch;
    
    /**
     * Creates a new SemanticVersion with the specified components.
     *
     * @param major the major version number (must be non-negative)
     * @param minor the minor version number (must be non-negative)
     * @param patch the patch version number (must be non-negative)
     * @throws IllegalArgumentException if any component is negative
     */
    public SemanticVersion(int major, int minor, int patch) {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException(
                String.format("Version components must be non-negative: %d.%d.%d", major, minor, patch)
            );
        }
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }
    
    /**
     * Parses a version string in MAJOR.MINOR.PATCH format.
     *
     * @param versionString the version string to parse
     * @return a SemanticVersion instance
     * @throws IllegalArgumentException if the version string is invalid
     */
    public static SemanticVersion parse(String versionString) {
        if (versionString == null || versionString.trim().isEmpty()) {
            throw new IllegalArgumentException("Version string cannot be null or empty");
        }
        
        Matcher matcher = VERSION_PATTERN.matcher(versionString.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                String.format("Invalid version format: '%s'. Expected format: MAJOR.MINOR.PATCH", versionString)
            );
        }
        
        try {
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            int patch = Integer.parseInt(matcher.group(3));
            return new SemanticVersion(major, minor, patch);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                String.format("Version components must be valid integers: '%s'", versionString), e
            );
        }
    }
    
    /**
     * Validates whether a version string is in valid MAJOR.MINOR.PATCH format.
     *
     * @param versionString the version string to validate
     * @return true if the version string is valid, false otherwise
     */
    public static boolean isValid(String versionString) {
        if (versionString == null || versionString.trim().isEmpty()) {
            return false;
        }
        
        Matcher matcher = VERSION_PATTERN.matcher(versionString.trim());
        if (!matcher.matches()) {
            return false;
        }
        
        try {
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            int patch = Integer.parseInt(matcher.group(3));
            return major >= 0 && minor >= 0 && patch >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Compares this version with another version.
     * Comparison is done first by major, then minor, then patch.
     *
     * @param other the version to compare to
     * @return negative if this < other, zero if equal, positive if this > other
     */
    @Override
    public int compareTo(SemanticVersion other) {
        if (other == null) {
            throw new NullPointerException("Cannot compare to null version");
        }
        
        int majorCompare = Integer.compare(this.major, other.major);
        if (majorCompare != 0) {
            return majorCompare;
        }
        
        int minorCompare = Integer.compare(this.minor, other.minor);
        if (minorCompare != 0) {
            return minorCompare;
        }
        
        return Integer.compare(this.patch, other.patch);
    }
    
    /**
     * Checks if this version is greater than another version.
     *
     * @param other the version to compare to
     * @return true if this version is greater than the other
     */
    public boolean greaterThan(SemanticVersion other) {
        return this.compareTo(other) > 0;
    }
    
    /**
     * Checks if this version is less than another version.
     *
     * @param other the version to compare to
     * @return true if this version is less than the other
     */
    public boolean lessThan(SemanticVersion other) {
        return this.compareTo(other) < 0;
    }
    
    /**
     * Creates a new version with the major version incremented by 1.
     * Minor and patch versions are reset to 0.
     * 
     * <p>Example: 1.2.3 → 2.0.0
     *
     * @return a new SemanticVersion with incremented major version
     */
    public SemanticVersion incrementMajor() {
        return new SemanticVersion(this.major + 1, 0, 0);
    }
    
    /**
     * Creates a new version with the minor version incremented by 1.
     * Patch version is reset to 0, major version remains unchanged.
     * 
     * <p>Example: 1.2.3 → 1.3.0
     *
     * @return a new SemanticVersion with incremented minor version
     */
    public SemanticVersion incrementMinor() {
        return new SemanticVersion(this.major, this.minor + 1, 0);
    }
    
    /**
     * Creates a new version with the patch version incremented by 1.
     * Major and minor versions remain unchanged.
     * 
     * <p>Example: 1.2.3 → 1.2.4
     *
     * @return a new SemanticVersion with incremented patch version
     */
    public SemanticVersion incrementPatch() {
        return new SemanticVersion(this.major, this.minor, this.patch + 1);
    }
    
    /**
     * Returns the string representation of this version in MAJOR.MINOR.PATCH format.
     *
     * @return the version string
     */
    @Override
    public String toString() {
        return String.format("%d.%d.%d", major, minor, patch);
    }
}
