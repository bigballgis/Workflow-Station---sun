package com.developer.service;

import com.developer.dto.AiGeneratedData;
import com.developer.dto.AiValidationResult;
import com.developer.service.impl.AiValidationServiceImpl;
import net.jqwik.api.*;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Tag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for AiValidationService decision definition validation.
 *
 * <p><b>Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6</b></p>
 */
@Tag("Feature: ai-function-unit-generation-refactor, Property 4: decision definition validation completeness")
class AiDecisionValidationProperties {

    private final AiValidationServiceImpl validationService = new AiValidationServiceImpl();

    private static final Set<String> VALID_HIT_POLICIES = Set.of(
            "FIRST", "UNIQUE", "PRIORITY", "ANY", "COLLECT", "RULE_ORDER", "OUTPUT_ORDER");

    /**
     * Property 4a: Empty decisionKey should produce FIELD_CONSTRAINT error.
     *
     * <p><b>Validates: Requirements 6.2</b></p>
     */
    @Property(tries = 100)
    void emptyDecisionKeyShouldError(
            @ForAll("validHitPolicy") String hitPolicy) {

        Map<String, Object> decision = new HashMap<>();
        decision.put("decisionKey", "");
        decision.put("hitPolicy", hitPolicy);

        AiGeneratedData data = AiGeneratedData.builder()
                .decisionDefinitions(List.of(decision))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "FIELD_CONSTRAINT".equals(e.getErrorType())
                        && e.getFieldPath().contains("decisionKey")))
                .isTrue();
    }

    /**
     * Property 4b: decisionKey exceeding 100 characters should produce FIELD_CONSTRAINT error.
     *
     * <p><b>Validates: Requirements 6.2</b></p>
     */
    @Property(tries = 100)
    void longDecisionKeyShouldError(
            @ForAll("longKey") String longKey) {

        Map<String, Object> decision = new HashMap<>();
        decision.put("decisionKey", longKey);
        decision.put("hitPolicy", "FIRST");

        AiGeneratedData data = AiGeneratedData.builder()
                .decisionDefinitions(List.of(decision))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "FIELD_CONSTRAINT".equals(e.getErrorType())
                        && e.getFieldPath().contains("decisionKey")
                        && e.getDescription().contains("100")))
                .isTrue();
    }

    /**
     * Property 4c: Valid hitPolicy should not produce INVALID_ENUM error.
     *
     * <p><b>Validates: Requirements 6.3</b></p>
     */
    @Property(tries = 100)
    void validHitPolicyShouldPass(
            @ForAll("validHitPolicy") String hitPolicy,
            @ForAll("validDecisionKey") String decisionKey) {

        Map<String, Object> decision = new HashMap<>();
        decision.put("decisionKey", decisionKey);
        decision.put("hitPolicy", hitPolicy);

        AiGeneratedData data = AiGeneratedData.builder()
                .decisionDefinitions(List.of(decision))
                .build();

        AiValidationResult result = validationService.validate(data);

        long hitPolicyErrors = result.getErrors().stream()
                .filter(e -> "INVALID_ENUM".equals(e.getErrorType())
                        && e.getFieldPath().contains("hitPolicy"))
                .count();
        assertThat(hitPolicyErrors).isZero();
    }

    /**
     * Property 4d: Invalid hitPolicy should produce INVALID_ENUM error.
     *
     * <p><b>Validates: Requirements 6.3</b></p>
     */
    @Property(tries = 100)
    void invalidHitPolicyShouldError(
            @ForAll("invalidHitPolicy") String invalidPolicy) {

        Map<String, Object> decision = new HashMap<>();
        decision.put("decisionKey", "test_decision");
        decision.put("hitPolicy", invalidPolicy);

        AiGeneratedData data = AiGeneratedData.builder()
                .decisionDefinitions(List.of(decision))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "INVALID_ENUM".equals(e.getErrorType())
                        && e.getFieldPath().contains("hitPolicy")))
                .isTrue();
    }

    /**
     * Property 4e: dmnXml with script tag should produce DMN_VALIDATION error.
     *
     * <p><b>Validates: Requirements 6.5</b></p>
     */
    @Property(tries = 100)
    void dmnXmlWithScriptTagShouldError(
            @ForAll("validDecisionKey") String decisionKey) {

        Map<String, Object> decision = new HashMap<>();
        decision.put("decisionKey", decisionKey);
        decision.put("hitPolicy", "FIRST");
        decision.put("dmnXml", "<definitions><script>alert('xss')</script></definitions>");

        AiGeneratedData data = AiGeneratedData.builder()
                .decisionDefinitions(List.of(decision))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "DMN_VALIDATION".equals(e.getErrorType())
                        && e.getFieldPath().contains("dmnXml")))
                .isTrue();
    }

    /**
     * Property 4f: Invalid dmnXml should produce DMN_VALIDATION error.
     *
     * <p><b>Validates: Requirements 6.5</b></p>
     */
    @Property(tries = 100)
    void invalidDmnXmlShouldError(
            @ForAll("validDecisionKey") String decisionKey) {

        Map<String, Object> decision = new HashMap<>();
        decision.put("decisionKey", decisionKey);
        decision.put("hitPolicy", "FIRST");
        decision.put("dmnXml", "not-valid-xml<<<>>>");

        AiGeneratedData data = AiGeneratedData.builder()
                .decisionDefinitions(List.of(decision))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "DMN_VALIDATION".equals(e.getErrorType())
                        && e.getFieldPath().contains("dmnXml")))
                .isTrue();
    }

    /**
     * Property 4g: Duplicate decisionKeys should produce UNIQUENESS error.
     *
     * <p><b>Validates: Requirements 6.6</b></p>
     */
    @Property(tries = 100)
    void duplicateDecisionKeysShouldError(
            @ForAll("validDecisionKey") String decisionKey) {

        Map<String, Object> d1 = new HashMap<>();
        d1.put("decisionKey", decisionKey);
        d1.put("hitPolicy", "FIRST");

        Map<String, Object> d2 = new HashMap<>();
        d2.put("decisionKey", decisionKey);
        d2.put("hitPolicy", "UNIQUE");

        AiGeneratedData data = AiGeneratedData.builder()
                .decisionDefinitions(List.of(d1, d2))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "UNIQUENESS".equals(e.getErrorType())
                        && e.getFieldPath().contains("decisionKey")))
                .isTrue();
    }

    // --- Providers ---

    @Provide
    Arbitrary<String> validHitPolicy() {
        return Arbitraries.of(VALID_HIT_POLICIES.stream().toList());
    }

    @Provide
    Arbitrary<String> invalidHitPolicy() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                .filter(s -> !VALID_HIT_POLICIES.contains(s));
    }

    @Provide
    Arbitrary<String> validDecisionKey() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(30)
                .map(s -> "dec_" + s);
    }

    @Provide
    Arbitrary<String> longKey() {
        return Arbitraries.strings().alpha().ofMinLength(101).ofMaxLength(150);
    }
}
