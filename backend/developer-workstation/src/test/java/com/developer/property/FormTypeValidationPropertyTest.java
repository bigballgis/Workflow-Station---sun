package com.developer.property;

import com.developer.enums.FormType;
import net.jqwik.api.*;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * FormType 验证属性测试
 * Feature: process-task-form-separation, Property 1: Invalid FormType values are rejected
 *
 * Validates: Requirements 1.7
 */
public class FormTypeValidationPropertyTest {

    private static final Set<String> VALID_FORM_TYPES = Set.of("PROCESS", "TASK", "ACTION");

    /**
     * Property 1: Invalid FormType values are rejected
     *
     * For any string that is not one of PROCESS, TASK, or ACTION,
     * FormType.valueOf() should throw IllegalArgumentException.
     *
     * Validates: Requirements 1.7
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 1: Invalid FormType values are rejected")
    void invalidFormTypeValuesAreRejected(@ForAll("invalidFormTypeStrings") String invalidValue) {
        assertThatThrownBy(() -> FormType.valueOf(invalidValue))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Complementary test: valid FormType values PROCESS, TASK, ACTION are accepted.
     *
     * Validates: Requirements 1.7
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 1: Valid FormType values are accepted")
    void validFormTypeValuesAreAccepted(@ForAll("validFormTypeStrings") String validValue) {
        FormType result = FormType.valueOf(validValue);
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo(validValue);
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> invalidFormTypeStrings() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(20)
                .filter(s -> !VALID_FORM_TYPES.contains(s));
    }

    @Provide
    Arbitrary<String> validFormTypeStrings() {
        return Arbitraries.of("PROCESS", "TASK", "ACTION");
    }
}
