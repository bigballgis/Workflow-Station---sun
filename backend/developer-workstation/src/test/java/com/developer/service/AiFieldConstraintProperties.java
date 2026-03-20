package com.developer.service;

import com.developer.dto.AiGeneratedData;
import com.developer.dto.AiValidationResult;
import com.developer.service.impl.AiValidationServiceImpl;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Tag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for AiValidationService field constraint validation.
 *
 * <p><b>Validates: Requirements 9.1</b></p>
 */
@Tag("Feature: ai-function-unit-generation, Property 6: 字段约束校验")
class AiFieldConstraintProperties {

    private final AiValidationServiceImpl validationService = new AiValidationServiceImpl();

    /**
     * Property 6a: DECIMAL without precision should produce FIELD_CONSTRAINT error.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void decimalWithoutPrecisionShouldError(
            @ForAll("fieldName") String fieldName) {

        Map<String, Object> field = new HashMap<>();
        field.put("fieldName", fieldName);
        field.put("dataType", "DECIMAL");
        field.put("scale", 2);
        field.put("isPrimaryKey", true);
        // precision is missing

        AiGeneratedData data = buildTableData(List.of(field));
        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "FIELD_CONSTRAINT".equals(e.getErrorType())
                        && e.getFieldPath().contains("precision")))
                .isTrue();
    }

    /**
     * Property 6b: DECIMAL without scale should produce FIELD_CONSTRAINT error.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void decimalWithoutScaleShouldError(
            @ForAll("fieldName") String fieldName) {

        Map<String, Object> field = new HashMap<>();
        field.put("fieldName", fieldName);
        field.put("dataType", "DECIMAL");
        field.put("precision", 10);
        field.put("isPrimaryKey", true);
        // scale is missing

        AiGeneratedData data = buildTableData(List.of(field));
        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "FIELD_CONSTRAINT".equals(e.getErrorType())
                        && e.getFieldPath().contains("scale")))
                .isTrue();
    }

    /**
     * Property 6c: DECIMAL with valid precision and scale should not produce FIELD_CONSTRAINT errors for those fields.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void decimalWithValidConstraintsShouldPass(
            @ForAll("fieldName") String fieldName,
            @ForAll @IntRange(min = 1, max = 38) int precision,
            @ForAll @IntRange(min = 1, max = 18) int scale) {

        Map<String, Object> field = new HashMap<>();
        field.put("fieldName", fieldName);
        field.put("dataType", "DECIMAL");
        field.put("precision", precision);
        field.put("scale", scale);
        field.put("isPrimaryKey", true);

        AiGeneratedData data = buildTableData(List.of(field));
        AiValidationResult result = validationService.validate(data);

        long constraintErrors = result.getErrors().stream()
                .filter(e -> "FIELD_CONSTRAINT".equals(e.getErrorType())
                        && (e.getFieldPath().contains("precision") || e.getFieldPath().contains("scale")))
                .count();
        assertThat(constraintErrors).isZero();
    }

    /**
     * Property 6d: VARCHAR without length should produce FIELD_CONSTRAINT error.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void varcharWithoutLengthShouldError(
            @ForAll("fieldName") String fieldName) {

        Map<String, Object> field = new HashMap<>();
        field.put("fieldName", fieldName);
        field.put("dataType", "VARCHAR");
        field.put("isPrimaryKey", true);
        // length is missing

        AiGeneratedData data = buildTableData(List.of(field));
        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "FIELD_CONSTRAINT".equals(e.getErrorType())
                        && e.getFieldPath().contains("length")))
                .isTrue();
    }

    /**
     * Property 6e: VARCHAR with valid length should not produce length FIELD_CONSTRAINT error.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void varcharWithValidLengthShouldPass(
            @ForAll("fieldName") String fieldName,
            @ForAll @IntRange(min = 1, max = 10000) int length) {

        Map<String, Object> field = new HashMap<>();
        field.put("fieldName", fieldName);
        field.put("dataType", "VARCHAR");
        field.put("length", length);
        field.put("isPrimaryKey", true);

        AiGeneratedData data = buildTableData(List.of(field));
        AiValidationResult result = validationService.validate(data);

        long lengthErrors = result.getErrors().stream()
                .filter(e -> "FIELD_CONSTRAINT".equals(e.getErrorType())
                        && e.getFieldPath().contains("length"))
                .count();
        assertThat(lengthErrors).isZero();
    }

    /**
     * Property 6f: Table without primary key should produce FIELD_CONSTRAINT error.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void tableWithoutPrimaryKeyShouldError(
            @ForAll("fieldName") String fieldName) {

        Map<String, Object> field = new HashMap<>();
        field.put("fieldName", fieldName);
        field.put("dataType", "INTEGER");
        field.put("isPrimaryKey", false);

        AiGeneratedData data = buildTableData(List.of(field));
        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "FIELD_CONSTRAINT".equals(e.getErrorType())
                        && e.getDescription().contains("主键")))
                .isTrue();
    }

    /**
     * Property 6g: Table with at least one primary key should not produce primary key error.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void tableWithPrimaryKeyShouldPass(
            @ForAll("fieldName") String fieldName) {

        Map<String, Object> pkField = new HashMap<>();
        pkField.put("fieldName", fieldName);
        pkField.put("dataType", "INTEGER");
        pkField.put("isPrimaryKey", true);

        AiGeneratedData data = buildTableData(List.of(pkField));
        AiValidationResult result = validationService.validate(data);

        long pkErrors = result.getErrors().stream()
                .filter(e -> "FIELD_CONSTRAINT".equals(e.getErrorType())
                        && e.getDescription().contains("主键"))
                .count();
        assertThat(pkErrors).isZero();
    }

    // --- Helpers ---

    private AiGeneratedData buildTableData(List<Map<String, Object>> fields) {
        return AiGeneratedData.builder()
                .tableDefinitions(List.of(Map.of(
                        "tableName", "test_table",
                        "tableType", "MAIN",
                        "fieldDefinitions", fields
                )))
                .build();
    }

    // --- Providers ---

    @Provide
    Arbitrary<String> fieldName() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30)
                .map(s -> "field_" + s);
    }
}
