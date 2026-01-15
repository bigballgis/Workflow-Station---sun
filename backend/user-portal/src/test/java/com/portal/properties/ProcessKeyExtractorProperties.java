package com.portal.properties;

import com.portal.util.ProcessKeyExtractor;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for ProcessKeyExtractor utility class.
 * 
 * Feature: process-key-function-unit-mapping
 * Property 1: Process Key Extraction Correctness
 * 
 * Tests that process keys are correctly extracted from Flowable process definition IDs.
 * Flowable format: {processKey}:{version}:{uuid}
 * 
 * **Validates: Requirements 1.1, 1.2, 3.5**
 */
class ProcessKeyExtractorProperties {

    /**
     * Property 1: For any valid process definition ID in format {processKey}:{version}:{uuid},
     * extracting the process key SHALL return exactly the substring before the first colon.
     * 
     * **Feature: process-key-function-unit-mapping, Property 1: Process Key Extraction Correctness**
     * **Validates: Requirements 1.1**
     */
    @Property(tries = 100)
    void extractProcessKeyReturnsSubstringBeforeFirstColon(
            @ForAll("validProcessKeys") String processKey,
            @ForAll @IntRange(min = 1, max = 999) int version,
            @ForAll("uuids") String uuid) {
        
        // Given: A full process definition ID in Flowable format
        String fullId = processKey + ":" + version + ":" + uuid;
        
        // When: Extracting the process key
        String extracted = ProcessKeyExtractor.extractProcessKey(fullId);
        
        // Then: Should return exactly the process key portion
        assertThat(extracted).isEqualTo(processKey);
    }

    /**
     * Property: For any string without colons, the entire string SHALL be returned unchanged.
     * 
     * **Feature: process-key-function-unit-mapping, Property 1: Process Key Extraction Correctness**
     * **Validates: Requirements 1.2**
     */
    @Property(tries = 100)
    void extractProcessKeyReturnsOriginalWhenNoColon(
            @ForAll("stringsWithoutColons") String input) {
        
        // When: Extracting from a string without colons
        String extracted = ProcessKeyExtractor.extractProcessKey(input);
        
        // Then: Should return the original string
        assertThat(extracted).isEqualTo(input);
    }

    /**
     * Property: Process keys with special characters (underscores, hyphens) are handled correctly.
     * 
     * **Feature: process-key-function-unit-mapping, Property 1: Process Key Extraction Correctness**
     * **Validates: Requirements 1.4**
     */
    @Property(tries = 100)
    void extractProcessKeyHandlesSpecialCharacters(
            @ForAll("processKeysWithSpecialChars") String processKey,
            @ForAll @IntRange(min = 1, max = 999) int version,
            @ForAll("uuids") String uuid) {
        
        // Given: A process key with special characters
        String fullId = processKey + ":" + version + ":" + uuid;
        
        // When: Extracting the process key
        String extracted = ProcessKeyExtractor.extractProcessKey(fullId);
        
        // Then: Should correctly extract the key with special characters
        assertThat(extracted).isEqualTo(processKey);
    }

    /**
     * Property: Null input returns null.
     * 
     * **Validates: Requirements 1.3**
     */
    @Example
    void extractProcessKeyReturnsNullForNullInput() {
        assertThat(ProcessKeyExtractor.extractProcessKey(null)).isNull();
    }

    /**
     * Property: Empty string returns null.
     * 
     * **Validates: Requirements 1.3**
     */
    @Example
    void extractProcessKeyReturnsNullForEmptyInput() {
        assertThat(ProcessKeyExtractor.extractProcessKey("")).isNull();
    }

    /**
     * Property: isFullProcessDefinitionId correctly identifies full format.
     */
    @Property(tries = 100)
    void isFullProcessDefinitionIdReturnsTrueForValidFormat(
            @ForAll("validProcessKeys") String processKey,
            @ForAll @IntRange(min = 1, max = 999) int version,
            @ForAll("uuids") String uuid) {
        
        String fullId = processKey + ":" + version + ":" + uuid;
        
        assertThat(ProcessKeyExtractor.isFullProcessDefinitionId(fullId)).isTrue();
    }

    /**
     * Property: isFullProcessDefinitionId returns false for strings without two colons.
     */
    @Property(tries = 100)
    void isFullProcessDefinitionIdReturnsFalseForInvalidFormat(
            @ForAll("stringsWithoutColons") String input) {
        
        assertThat(ProcessKeyExtractor.isFullProcessDefinitionId(input)).isFalse();
    }

    /**
     * Property: extractVersion correctly extracts version number.
     */
    @Property(tries = 100)
    void extractVersionReturnsCorrectVersion(
            @ForAll("validProcessKeys") String processKey,
            @ForAll @IntRange(min = 1, max = 999) int version,
            @ForAll("uuids") String uuid) {
        
        String fullId = processKey + ":" + version + ":" + uuid;
        
        String extractedVersion = ProcessKeyExtractor.extractVersion(fullId);
        
        assertThat(extractedVersion).isEqualTo(String.valueOf(version));
    }

    /**
     * Property: Extraction is idempotent - extracting from an already extracted key returns the same value.
     */
    @Property(tries = 100)
    void extractionIsIdempotent(
            @ForAll("validProcessKeys") String processKey,
            @ForAll @IntRange(min = 1, max = 999) int version,
            @ForAll("uuids") String uuid) {
        
        String fullId = processKey + ":" + version + ":" + uuid;
        
        String firstExtraction = ProcessKeyExtractor.extractProcessKey(fullId);
        String secondExtraction = ProcessKeyExtractor.extractProcessKey(firstExtraction);
        
        // Extracting from an already extracted key should return the same value
        assertThat(secondExtraction).isEqualTo(firstExtraction);
    }

    // ==================== Arbitrary Providers ====================

    @Provide
    Arbitrary<String> validProcessKeys() {
        // Generate process keys like: Process_PurchaseRequest, LeaveRequest, etc.
        return Arbitraries.of(
                "Process_PurchaseRequest",
                "Process_LeaveRequest",
                "Process_ExpenseReport",
                "SimpleProcess",
                "MyWorkflow",
                "Test_Process_123"
        );
    }

    @Provide
    Arbitrary<String> processKeysWithSpecialChars() {
        // Generate process keys with underscores, hyphens, and numbers
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_', '-')
                .ofMinLength(3)
                .ofMaxLength(30)
                .filter(s -> !s.contains(":") && Character.isLetter(s.charAt(0)));
    }

    @Provide
    Arbitrary<String> stringsWithoutColons() {
        // Generate non-empty strings without colons
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_', '-')
                .ofMinLength(1)
                .ofMaxLength(50)
                .filter(s -> !s.contains(":"));
    }

    @Provide
    Arbitrary<String> uuids() {
        // Generate UUID-like strings
        return Arbitraries.strings()
                .withCharRange('a', 'f')
                .withCharRange('0', '9')
                .ofLength(32)
                .map(s -> s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + 
                         s.substring(12, 16) + "-" + s.substring(16, 20) + "-" + 
                         s.substring(20, 32));
    }
}
