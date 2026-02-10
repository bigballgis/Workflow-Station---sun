package com.platform.common.version;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for SemanticVersion class.
 * Tests parsing, validation, comparison, and edge cases.
 */
@DisplayName("SemanticVersion")
class SemanticVersionTest {
    
    @Test
    @DisplayName("should create version with valid components")
    void shouldCreateVersionWithValidComponents() {
        SemanticVersion version = new SemanticVersion(1, 2, 3);
        
        assertThat(version.getMajor()).isEqualTo(1);
        assertThat(version.getMinor()).isEqualTo(2);
        assertThat(version.getPatch()).isEqualTo(3);
        assertThat(version.toString()).isEqualTo("1.2.3");
    }
    
    @Test
    @DisplayName("should create version 0.0.0")
    void shouldCreateVersionZero() {
        SemanticVersion version = new SemanticVersion(0, 0, 0);
        
        assertThat(version.getMajor()).isEqualTo(0);
        assertThat(version.getMinor()).isEqualTo(0);
        assertThat(version.getPatch()).isEqualTo(0);
        assertThat(version.toString()).isEqualTo("0.0.0");
    }
    
    @Test
    @DisplayName("should reject negative major version")
    void shouldRejectNegativeMajor() {
        assertThatThrownBy(() -> new SemanticVersion(-1, 0, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Version components must be non-negative");
    }
    
    @Test
    @DisplayName("should reject negative minor version")
    void shouldRejectNegativeMinor() {
        assertThatThrownBy(() -> new SemanticVersion(1, -1, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Version components must be non-negative");
    }
    
    @Test
    @DisplayName("should reject negative patch version")
    void shouldRejectNegativePatch() {
        assertThatThrownBy(() -> new SemanticVersion(1, 0, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Version components must be non-negative");
    }
    
    @ParameterizedTest
    @DisplayName("should parse valid version strings")
    @CsvSource({
        "1.0.0, 1, 0, 0",
        "2.3.5, 2, 3, 5",
        "10.20.30, 10, 20, 30",
        "0.0.0, 0, 0, 0",
        "999.999.999, 999, 999, 999"
    })
    void shouldParseValidVersionStrings(String versionString, int major, int minor, int patch) {
        SemanticVersion version = SemanticVersion.parse(versionString);
        
        assertThat(version.getMajor()).isEqualTo(major);
        assertThat(version.getMinor()).isEqualTo(minor);
        assertThat(version.getPatch()).isEqualTo(patch);
    }
    
    @Test
    @DisplayName("should parse version string with whitespace")
    void shouldParseVersionWithWhitespace() {
        SemanticVersion version = SemanticVersion.parse("  1.2.3  ");
        
        assertThat(version.getMajor()).isEqualTo(1);
        assertThat(version.getMinor()).isEqualTo(2);
        assertThat(version.getPatch()).isEqualTo(3);
    }
    
    @ParameterizedTest
    @DisplayName("should reject invalid version strings")
    @ValueSource(strings = {
        "",
        "   ",
        "1",
        "1.0",
        "1.0.0.0",
        "a.b.c",
        "1.a.0",
        "1.0.a",
        "-1.0.0",
        "1.-1.0",
        "1.0.-1",
        "1.0.0-alpha",
        "v1.0.0",
        "1.0.0-SNAPSHOT",
        "1..0",
        ".1.0.0",
        "1.0.0.",
        "1,0,0"
    })
    void shouldRejectInvalidVersionStrings(String invalidVersion) {
        assertThatThrownBy(() -> SemanticVersion.parse(invalidVersion))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageMatching("(Invalid version format|Version string cannot be null or empty).*");
    }
    
    @Test
    @DisplayName("should reject null version string")
    void shouldRejectNullVersionString() {
        assertThatThrownBy(() -> SemanticVersion.parse(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Version string cannot be null or empty");
    }
    
    @ParameterizedTest
    @DisplayName("should validate correct version strings")
    @ValueSource(strings = {
        "1.0.0",
        "2.3.5",
        "10.20.30",
        "0.0.0",
        "999.999.999"
    })
    void shouldValidateCorrectVersionStrings(String versionString) {
        assertThat(SemanticVersion.isValid(versionString)).isTrue();
    }
    
    @ParameterizedTest
    @DisplayName("should invalidate incorrect version strings")
    @ValueSource(strings = {
        "",
        "   ",
        "1",
        "1.0",
        "1.0.0.0",
        "a.b.c",
        "1.a.0",
        "-1.0.0",
        "1.0.0-alpha",
        "v1.0.0"
    })
    void shouldInvalidateIncorrectVersionStrings(String versionString) {
        assertThat(SemanticVersion.isValid(versionString)).isFalse();
    }
    
    @Test
    @DisplayName("should invalidate null version string")
    void shouldInvalidateNullVersionString() {
        assertThat(SemanticVersion.isValid(null)).isFalse();
    }
    
    @Test
    @DisplayName("should compare equal versions")
    void shouldCompareEqualVersions() {
        SemanticVersion v1 = new SemanticVersion(1, 2, 3);
        SemanticVersion v2 = new SemanticVersion(1, 2, 3);
        
        assertThat(v1.compareTo(v2)).isEqualTo(0);
        assertThat(v1).isEqualTo(v2);
        assertThat(v1.greaterThan(v2)).isFalse();
        assertThat(v1.lessThan(v2)).isFalse();
    }
    
    @Test
    @DisplayName("should compare versions by major number")
    void shouldCompareVersionsByMajor() {
        SemanticVersion v1 = new SemanticVersion(1, 0, 0);
        SemanticVersion v2 = new SemanticVersion(2, 0, 0);
        
        assertThat(v1.compareTo(v2)).isNegative();
        assertThat(v2.compareTo(v1)).isPositive();
        assertThat(v1.lessThan(v2)).isTrue();
        assertThat(v2.greaterThan(v1)).isTrue();
    }
    
    @Test
    @DisplayName("should compare versions by minor number when major is equal")
    void shouldCompareVersionsByMinor() {
        SemanticVersion v1 = new SemanticVersion(1, 2, 0);
        SemanticVersion v2 = new SemanticVersion(1, 3, 0);
        
        assertThat(v1.compareTo(v2)).isNegative();
        assertThat(v2.compareTo(v1)).isPositive();
        assertThat(v1.lessThan(v2)).isTrue();
        assertThat(v2.greaterThan(v1)).isTrue();
    }
    
    @Test
    @DisplayName("should compare versions by patch number when major and minor are equal")
    void shouldCompareVersionsByPatch() {
        SemanticVersion v1 = new SemanticVersion(1, 2, 3);
        SemanticVersion v2 = new SemanticVersion(1, 2, 4);
        
        assertThat(v1.compareTo(v2)).isNegative();
        assertThat(v2.compareTo(v1)).isPositive();
        assertThat(v1.lessThan(v2)).isTrue();
        assertThat(v2.greaterThan(v1)).isTrue();
    }
    
    @Test
    @DisplayName("should handle comparison with large version numbers")
    void shouldCompareWithLargeNumbers() {
        SemanticVersion v1 = new SemanticVersion(999, 999, 998);
        SemanticVersion v2 = new SemanticVersion(999, 999, 999);
        
        assertThat(v1.lessThan(v2)).isTrue();
        assertThat(v2.greaterThan(v1)).isTrue();
    }
    
    @Test
    @DisplayName("should throw exception when comparing to null")
    void shouldThrowExceptionWhenComparingToNull() {
        SemanticVersion version = new SemanticVersion(1, 0, 0);
        
        assertThatThrownBy(() -> version.compareTo(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Cannot compare to null version");
    }
    
    @Test
    @DisplayName("should have consistent equals and hashCode")
    void shouldHaveConsistentEqualsAndHashCode() {
        SemanticVersion v1 = new SemanticVersion(1, 2, 3);
        SemanticVersion v2 = new SemanticVersion(1, 2, 3);
        SemanticVersion v3 = new SemanticVersion(1, 2, 4);
        
        // Reflexive
        assertThat(v1).isEqualTo(v1);
        
        // Symmetric
        assertThat(v1).isEqualTo(v2);
        assertThat(v2).isEqualTo(v1);
        
        // Transitive (v1 == v2 and v2 == v1, so v1 == v1)
        assertThat(v1).isEqualTo(v2);
        assertThat(v2).isEqualTo(v1);
        assertThat(v1).isEqualTo(v1);
        
        // Consistent hashCode
        assertThat(v1.hashCode()).isEqualTo(v2.hashCode());
        
        // Not equal
        assertThat(v1).isNotEqualTo(v3);
        assertThat(v1).isNotEqualTo(null);
        assertThat(v1).isNotEqualTo("1.2.3");
    }
    
    @Test
    @DisplayName("should format version as string correctly")
    void shouldFormatVersionAsString() {
        assertThat(new SemanticVersion(1, 2, 3).toString()).isEqualTo("1.2.3");
        assertThat(new SemanticVersion(0, 0, 0).toString()).isEqualTo("0.0.0");
        assertThat(new SemanticVersion(10, 20, 30).toString()).isEqualTo("10.20.30");
    }
    
    @Test
    @DisplayName("should parse and format version consistently")
    void shouldParseAndFormatConsistently() {
        String original = "1.2.3";
        SemanticVersion version = SemanticVersion.parse(original);
        String formatted = version.toString();
        
        assertThat(formatted).isEqualTo(original);
    }
    
    // ========== Version Increment Tests ==========
    
    @Test
    @DisplayName("should increment major version and reset minor and patch to 0")
    void shouldIncrementMajorVersion() {
        SemanticVersion version = new SemanticVersion(1, 2, 3);
        SemanticVersion incremented = version.incrementMajor();
        
        assertThat(incremented.getMajor()).isEqualTo(2);
        assertThat(incremented.getMinor()).isEqualTo(0);
        assertThat(incremented.getPatch()).isEqualTo(0);
        assertThat(incremented.toString()).isEqualTo("2.0.0");
    }
    
    @Test
    @DisplayName("should increment major version from 0.0.0")
    void shouldIncrementMajorFromZero() {
        SemanticVersion version = new SemanticVersion(0, 0, 0);
        SemanticVersion incremented = version.incrementMajor();
        
        assertThat(incremented.getMajor()).isEqualTo(1);
        assertThat(incremented.getMinor()).isEqualTo(0);
        assertThat(incremented.getPatch()).isEqualTo(0);
        assertThat(incremented.toString()).isEqualTo("1.0.0");
    }
    
    @Test
    @DisplayName("should increment minor version and reset patch to 0")
    void shouldIncrementMinorVersion() {
        SemanticVersion version = new SemanticVersion(1, 2, 3);
        SemanticVersion incremented = version.incrementMinor();
        
        assertThat(incremented.getMajor()).isEqualTo(1);
        assertThat(incremented.getMinor()).isEqualTo(3);
        assertThat(incremented.getPatch()).isEqualTo(0);
        assertThat(incremented.toString()).isEqualTo("1.3.0");
    }
    
    @Test
    @DisplayName("should increment minor version from 0.0.0")
    void shouldIncrementMinorFromZero() {
        SemanticVersion version = new SemanticVersion(0, 0, 0);
        SemanticVersion incremented = version.incrementMinor();
        
        assertThat(incremented.getMajor()).isEqualTo(0);
        assertThat(incremented.getMinor()).isEqualTo(1);
        assertThat(incremented.getPatch()).isEqualTo(0);
        assertThat(incremented.toString()).isEqualTo("0.1.0");
    }
    
    @Test
    @DisplayName("should increment patch version only")
    void shouldIncrementPatchVersion() {
        SemanticVersion version = new SemanticVersion(1, 2, 3);
        SemanticVersion incremented = version.incrementPatch();
        
        assertThat(incremented.getMajor()).isEqualTo(1);
        assertThat(incremented.getMinor()).isEqualTo(2);
        assertThat(incremented.getPatch()).isEqualTo(4);
        assertThat(incremented.toString()).isEqualTo("1.2.4");
    }
    
    @Test
    @DisplayName("should increment patch version from 0.0.0")
    void shouldIncrementPatchFromZero() {
        SemanticVersion version = new SemanticVersion(0, 0, 0);
        SemanticVersion incremented = version.incrementPatch();
        
        assertThat(incremented.getMajor()).isEqualTo(0);
        assertThat(incremented.getMinor()).isEqualTo(0);
        assertThat(incremented.getPatch()).isEqualTo(1);
        assertThat(incremented.toString()).isEqualTo("0.0.1");
    }
    
    @Test
    @DisplayName("should not modify original version when incrementing")
    void shouldNotModifyOriginalVersionWhenIncrementing() {
        SemanticVersion original = new SemanticVersion(1, 2, 3);
        
        SemanticVersion major = original.incrementMajor();
        SemanticVersion minor = original.incrementMinor();
        SemanticVersion patch = original.incrementPatch();
        
        // Original should remain unchanged
        assertThat(original.getMajor()).isEqualTo(1);
        assertThat(original.getMinor()).isEqualTo(2);
        assertThat(original.getPatch()).isEqualTo(3);
        
        // Each increment should be different
        assertThat(major.toString()).isEqualTo("2.0.0");
        assertThat(minor.toString()).isEqualTo("1.3.0");
        assertThat(patch.toString()).isEqualTo("1.2.4");
    }
    
    @Test
    @DisplayName("should chain increments correctly")
    void shouldChainIncrementsCorrectly() {
        SemanticVersion version = new SemanticVersion(1, 0, 0);
        
        // Chain multiple increments
        SemanticVersion v1 = version.incrementPatch(); // 1.0.1
        SemanticVersion v2 = v1.incrementPatch();      // 1.0.2
        SemanticVersion v3 = v2.incrementMinor();      // 1.1.0
        SemanticVersion v4 = v3.incrementPatch();      // 1.1.1
        SemanticVersion v5 = v4.incrementMajor();      // 2.0.0
        
        assertThat(v1.toString()).isEqualTo("1.0.1");
        assertThat(v2.toString()).isEqualTo("1.0.2");
        assertThat(v3.toString()).isEqualTo("1.1.0");
        assertThat(v4.toString()).isEqualTo("1.1.1");
        assertThat(v5.toString()).isEqualTo("2.0.0");
    }
    
    // ========== Property-Based Tests ==========
    
    /**
     * Property 29: Semantic Version Format Validation
     * 
     * **Validates: Requirements 10.1, 10.7**
     * 
     * For any version number created in the system, it should match the format 
     * MAJOR.MINOR.PATCH where each component is a non-negative integer.
     * 
     * This property ensures that:
     * 1. All version numbers can be parsed successfully
     * 2. All version numbers follow the semantic versioning format
     * 3. All components are non-negative integers
     * 4. The string representation matches the expected format
     */
    @Property(tries = 100)
    @Label("Property 29: Any valid semantic version should match MAJOR.MINOR.PATCH format with non-negative integers")
    void anyValidSemanticVersionShouldMatchFormat(
        @ForAll @IntRange(min = 0, max = 999) int major,
        @ForAll @IntRange(min = 0, max = 999) int minor,
        @ForAll @IntRange(min = 0, max = 999) int patch
    ) {
        // Create a version with the generated components
        SemanticVersion version = new SemanticVersion(major, minor, patch);
        
        // Get the string representation
        String versionString = version.toString();
        
        // Property 1: The version string should be valid according to isValid()
        assertThat(SemanticVersion.isValid(versionString))
            .as("Version string '%s' should be valid", versionString)
            .isTrue();
        
        // Property 2: The version string should match the expected format
        String expectedFormat = String.format("%d.%d.%d", major, minor, patch);
        assertThat(versionString)
            .as("Version string should match MAJOR.MINOR.PATCH format")
            .isEqualTo(expectedFormat);
        
        // Property 3: The version string should be parseable back to the same version
        SemanticVersion parsedVersion = SemanticVersion.parse(versionString);
        assertThat(parsedVersion)
            .as("Parsed version should equal original version")
            .isEqualTo(version);
        
        // Property 4: All components should be non-negative
        assertThat(version.getMajor())
            .as("Major version should be non-negative")
            .isGreaterThanOrEqualTo(0);
        assertThat(version.getMinor())
            .as("Minor version should be non-negative")
            .isGreaterThanOrEqualTo(0);
        assertThat(version.getPatch())
            .as("Patch version should be non-negative")
            .isGreaterThanOrEqualTo(0);
        
        // Property 5: The version string should match the regex pattern
        assertThat(versionString)
            .as("Version string should match regex pattern")
            .matches("^\\d+\\.\\d+\\.\\d+$");
    }
    
    /**
     * Property test for parsing: Any string that matches MAJOR.MINOR.PATCH format
     * with non-negative integers should be parseable.
     */
    @Property(tries = 100)
    @Label("Property 29 (parsing): Any string in MAJOR.MINOR.PATCH format should be parseable")
    void anyValidFormatStringShouldBeParseable(
        @ForAll @IntRange(min = 0, max = 999) int major,
        @ForAll @IntRange(min = 0, max = 999) int minor,
        @ForAll @IntRange(min = 0, max = 999) int patch
    ) {
        // Create a version string in the correct format
        String versionString = String.format("%d.%d.%d", major, minor, patch);
        
        // Property: The string should be parseable without throwing an exception
        assertThatCode(() -> SemanticVersion.parse(versionString))
            .as("Valid format string '%s' should be parseable", versionString)
            .doesNotThrowAnyException();
        
        // Property: The parsed version should have the correct components
        SemanticVersion version = SemanticVersion.parse(versionString);
        assertThat(version.getMajor()).isEqualTo(major);
        assertThat(version.getMinor()).isEqualTo(minor);
        assertThat(version.getPatch()).isEqualTo(patch);
    }
    
    /**
     * Property test for validation: Any string that doesn't match the format
     * should be rejected by isValid().
     */
    @Property(tries = 100)
    @Label("Property 29 (validation): Invalid format strings should be rejected")
    void invalidFormatStringsShouldBeRejected(
        @ForAll("invalidVersionStrings") String invalidVersion
    ) {
        // Property: Invalid version strings should return false from isValid()
        assertThat(SemanticVersion.isValid(invalidVersion))
            .as("Invalid version string '%s' should be rejected", invalidVersion)
            .isFalse();
        
        // Property: Invalid version strings should throw exception when parsed
        assertThatThrownBy(() -> SemanticVersion.parse(invalidVersion))
            .as("Parsing invalid version string '%s' should throw exception", invalidVersion)
            .isInstanceOf(IllegalArgumentException.class);
    }
    
    /**
     * Arbitrary provider for invalid version strings.
     * Generates various invalid formats to test rejection.
     */
    @Provide
    Arbitrary<String> invalidVersionStrings() {
        return Arbitraries.oneOf(
            // Missing components
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
            Arbitraries.integers().map(String::valueOf),
            Arbitraries.integers().map(i -> i + "." + i),
            
            // Too many components
            Arbitraries.integers().map(i -> String.format("%d.%d.%d.%d", i, i, i, i)),
            
            // Non-numeric components
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(5)
                .map(s -> s + ".0.0"),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(5)
                .map(s -> "0." + s + ".0"),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(5)
                .map(s -> "0.0." + s),
            
            // Negative numbers
            Arbitraries.integers().lessOrEqual(-1)
                .map(i -> String.format("%d.0.0", i)),
            Arbitraries.integers().lessOrEqual(-1)
                .map(i -> String.format("0.%d.0", i)),
            Arbitraries.integers().lessOrEqual(-1)
                .map(i -> String.format("0.0.%d", i)),
            
            // Special characters and prefixes
            Arbitraries.integers().greaterOrEqual(0)
                .map(i -> String.format("v%d.%d.%d", i, i, i)),
            Arbitraries.integers().greaterOrEqual(0)
                .map(i -> String.format("%d.%d.%d-SNAPSHOT", i, i, i)),
            Arbitraries.integers().greaterOrEqual(0)
                .map(i -> String.format("%d.%d.%d-alpha", i, i, i)),
            
            // Empty and whitespace
            Arbitraries.just(""),
            Arbitraries.just("   "),
            
            // Malformed separators
            Arbitraries.integers().greaterOrEqual(0)
                .map(i -> String.format("%d,%d,%d", i, i, i)),
            Arbitraries.integers().greaterOrEqual(0)
                .map(i -> String.format("%d-%d-%d", i, i, i)),
            Arbitraries.integers().greaterOrEqual(0)
                .map(i -> String.format("%d..%d.%d", i, i, i))
        );
    }
    
    // ========== Property-Based Tests for Version Increment Logic ==========
    
    /**
     * Property 30: Major Version Increment
     * 
     * **Validates: Requirements 10.2, 10.5**
     * 
     * For any deployment with change type 'major', the new version should have 
     * MAJOR incremented by 1, and MINOR and PATCH reset to 0.
     * 
     * This property ensures that:
     * 1. Major version is incremented by exactly 1
     * 2. Minor version is reset to 0
     * 3. Patch version is reset to 0
     * 4. The original version remains unchanged (immutability)
     * 5. The result is always a valid semantic version
     */
    @Property(tries = 100)
    @Label("Property 30: Major version increment should increment MAJOR by 1 and reset MINOR and PATCH to 0")
    void majorVersionIncrementShouldResetMinorAndPatch(
        @ForAll @IntRange(min = 0, max = 999) int major,
        @ForAll @IntRange(min = 0, max = 999) int minor,
        @ForAll @IntRange(min = 0, max = 999) int patch
    ) {
        // Given: A semantic version with arbitrary components
        SemanticVersion original = new SemanticVersion(major, minor, patch);
        
        // When: We increment the major version
        SemanticVersion incremented = original.incrementMajor();
        
        // Then: Property 1 - Major version should be incremented by exactly 1
        assertThat(incremented.getMajor())
            .as("Major version should be incremented by 1")
            .isEqualTo(major + 1);
        
        // Then: Property 2 - Minor version should be reset to 0
        assertThat(incremented.getMinor())
            .as("Minor version should be reset to 0 when major is incremented")
            .isEqualTo(0);
        
        // Then: Property 3 - Patch version should be reset to 0
        assertThat(incremented.getPatch())
            .as("Patch version should be reset to 0 when major is incremented")
            .isEqualTo(0);
        
        // Then: Property 4 - Original version should remain unchanged (immutability)
        assertThat(original.getMajor())
            .as("Original major version should remain unchanged")
            .isEqualTo(major);
        assertThat(original.getMinor())
            .as("Original minor version should remain unchanged")
            .isEqualTo(minor);
        assertThat(original.getPatch())
            .as("Original patch version should remain unchanged")
            .isEqualTo(patch);
        
        // Then: Property 5 - Result should be a valid semantic version
        String incrementedString = incremented.toString();
        assertThat(SemanticVersion.isValid(incrementedString))
            .as("Incremented version should be valid")
            .isTrue();
        
        // Then: Property 6 - Result should be greater than original (unless overflow)
        if (major < Integer.MAX_VALUE) {
            assertThat(incremented.greaterThan(original))
                .as("Incremented major version should be greater than original")
                .isTrue();
        }
        
        // Then: Property 7 - String representation should match expected format
        String expectedFormat = String.format("%d.0.0", major + 1);
        assertThat(incrementedString)
            .as("Incremented version string should match expected format")
            .isEqualTo(expectedFormat);
    }
    
    /**
     * Property 31: Minor Version Increment
     * 
     * **Validates: Requirements 10.3, 10.6**
     * 
     * For any deployment with change type 'minor', the new version should have 
     * MINOR incremented by 1, and PATCH reset to 0, with MAJOR unchanged.
     * 
     * This property ensures that:
     * 1. Minor version is incremented by exactly 1
     * 2. Patch version is reset to 0
     * 3. Major version remains unchanged
     * 4. The original version remains unchanged (immutability)
     * 5. The result is always a valid semantic version
     */
    @Property(tries = 100)
    @Label("Property 31: Minor version increment should increment MINOR by 1, reset PATCH to 0, and keep MAJOR unchanged")
    void minorVersionIncrementShouldResetPatchAndKeepMajor(
        @ForAll @IntRange(min = 0, max = 999) int major,
        @ForAll @IntRange(min = 0, max = 999) int minor,
        @ForAll @IntRange(min = 0, max = 999) int patch
    ) {
        // Given: A semantic version with arbitrary components
        SemanticVersion original = new SemanticVersion(major, minor, patch);
        
        // When: We increment the minor version
        SemanticVersion incremented = original.incrementMinor();
        
        // Then: Property 1 - Major version should remain unchanged
        assertThat(incremented.getMajor())
            .as("Major version should remain unchanged when minor is incremented")
            .isEqualTo(major);
        
        // Then: Property 2 - Minor version should be incremented by exactly 1
        assertThat(incremented.getMinor())
            .as("Minor version should be incremented by 1")
            .isEqualTo(minor + 1);
        
        // Then: Property 3 - Patch version should be reset to 0
        assertThat(incremented.getPatch())
            .as("Patch version should be reset to 0 when minor is incremented")
            .isEqualTo(0);
        
        // Then: Property 4 - Original version should remain unchanged (immutability)
        assertThat(original.getMajor())
            .as("Original major version should remain unchanged")
            .isEqualTo(major);
        assertThat(original.getMinor())
            .as("Original minor version should remain unchanged")
            .isEqualTo(minor);
        assertThat(original.getPatch())
            .as("Original patch version should remain unchanged")
            .isEqualTo(patch);
        
        // Then: Property 5 - Result should be a valid semantic version
        String incrementedString = incremented.toString();
        assertThat(SemanticVersion.isValid(incrementedString))
            .as("Incremented version should be valid")
            .isTrue();
        
        // Then: Property 6 - Result should be greater than original (unless overflow)
        if (minor < Integer.MAX_VALUE) {
            assertThat(incremented.greaterThan(original))
                .as("Incremented minor version should be greater than original")
                .isTrue();
        }
        
        // Then: Property 7 - String representation should match expected format
        String expectedFormat = String.format("%d.%d.0", major, minor + 1);
        assertThat(incrementedString)
            .as("Incremented version string should match expected format")
            .isEqualTo(expectedFormat);
    }
    
    /**
     * Property 32: Patch Version Increment
     * 
     * **Validates: Requirements 10.4**
     * 
     * For any deployment with change type 'patch', the new version should have 
     * PATCH incremented by 1, with MAJOR and MINOR unchanged.
     * 
     * This property ensures that:
     * 1. Patch version is incremented by exactly 1
     * 2. Major version remains unchanged
     * 3. Minor version remains unchanged
     * 4. The original version remains unchanged (immutability)
     * 5. The result is always a valid semantic version
     */
    @Property(tries = 100)
    @Label("Property 32: Patch version increment should increment PATCH by 1 and keep MAJOR and MINOR unchanged")
    void patchVersionIncrementShouldKeepMajorAndMinor(
        @ForAll @IntRange(min = 0, max = 999) int major,
        @ForAll @IntRange(min = 0, max = 999) int minor,
        @ForAll @IntRange(min = 0, max = 999) int patch
    ) {
        // Given: A semantic version with arbitrary components
        SemanticVersion original = new SemanticVersion(major, minor, patch);
        
        // When: We increment the patch version
        SemanticVersion incremented = original.incrementPatch();
        
        // Then: Property 1 - Major version should remain unchanged
        assertThat(incremented.getMajor())
            .as("Major version should remain unchanged when patch is incremented")
            .isEqualTo(major);
        
        // Then: Property 2 - Minor version should remain unchanged
        assertThat(incremented.getMinor())
            .as("Minor version should remain unchanged when patch is incremented")
            .isEqualTo(minor);
        
        // Then: Property 3 - Patch version should be incremented by exactly 1
        assertThat(incremented.getPatch())
            .as("Patch version should be incremented by 1")
            .isEqualTo(patch + 1);
        
        // Then: Property 4 - Original version should remain unchanged (immutability)
        assertThat(original.getMajor())
            .as("Original major version should remain unchanged")
            .isEqualTo(major);
        assertThat(original.getMinor())
            .as("Original minor version should remain unchanged")
            .isEqualTo(minor);
        assertThat(original.getPatch())
            .as("Original patch version should remain unchanged")
            .isEqualTo(patch);
        
        // Then: Property 5 - Result should be a valid semantic version
        String incrementedString = incremented.toString();
        assertThat(SemanticVersion.isValid(incrementedString))
            .as("Incremented version should be valid")
            .isTrue();
        
        // Then: Property 6 - Result should be greater than original (unless overflow)
        if (patch < Integer.MAX_VALUE) {
            assertThat(incremented.greaterThan(original))
                .as("Incremented patch version should be greater than original")
                .isTrue();
        }
        
        // Then: Property 7 - String representation should match expected format
        String expectedFormat = String.format("%d.%d.%d", major, minor, patch + 1);
        assertThat(incrementedString)
            .as("Incremented version string should match expected format")
            .isEqualTo(expectedFormat);
    }
}
