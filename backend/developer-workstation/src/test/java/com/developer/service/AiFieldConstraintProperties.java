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
@Tag("Feature: ai-function-unit-generation, Property 6: field constraint validation")
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
                        && e.getDescription().contains("primary key")))
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
                        && e.getDescription().contains("primary key"))
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

    /**
     * Property 5a: Formula with empty targetField should produce FIELD_CONSTRAINT error.
     *
     * <p><b>Validates: Requirements 7.2</b></p>
     */
    @Property(tries = 100)
    void formulaWithEmptyTargetFieldShouldError(
            @ForAll("fieldName") String expression) {

        Map<String, Object> formula = new HashMap<>();
        formula.put("targetField", "");
        formula.put("expression", expression);
        formula.put("dependsOn", List.of("field1"));

        Map<String, Object> configJson = new HashMap<>();
        configJson.put("formulas", List.of(formula));

        AiGeneratedData data = AiGeneratedData.builder()
                .formDefinitions(List.of(Map.of(
                        "formName", "test_form",
                        "formType", "PROCESS",
                        "configJson", configJson
                )))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "FIELD_CONSTRAINT".equals(e.getErrorType())
                        && e.getFieldPath().contains("formulas")
                        && e.getFieldPath().contains("targetField")))
                .isTrue();
    }

    /**
     * Property 5b: Formula with empty dependsOn should produce FIELD_CONSTRAINT error.
     *
     * <p><b>Validates: Requirements 7.2</b></p>
     */
    @Property(tries = 100)
    void formulaWithEmptyDependsOnShouldError(
            @ForAll("fieldName") String targetField) {

        Map<String, Object> formula = new HashMap<>();
        formula.put("targetField", targetField);
        formula.put("expression", "a + b");
        formula.put("dependsOn", List.of());

        Map<String, Object> configJson = new HashMap<>();
        configJson.put("formulas", List.of(formula));

        AiGeneratedData data = AiGeneratedData.builder()
                .formDefinitions(List.of(Map.of(
                        "formName", "test_form",
                        "formType", "PROCESS",
                        "configJson", configJson
                )))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "FIELD_CONSTRAINT".equals(e.getErrorType())
                        && e.getFieldPath().contains("dependsOn")))
                .isTrue();
    }

    /**
     * Property 5c: Linkage with invalid linkageType should produce INVALID_ENUM error.
     *
     * <p><b>Validates: Requirements 7.3</b></p>
     */
    @Property(tries = 100)
    void linkageWithInvalidTypeShouldError(
            @ForAll("invalidLinkageType") String invalidType) {

        Map<String, Object> linkage = new HashMap<>();
        linkage.put("sourceField", "field_a");
        linkage.put("targetField", "field_b");
        linkage.put("linkageType", invalidType);

        Map<String, Object> configJson = new HashMap<>();
        configJson.put("linkages", List.of(linkage));

        AiGeneratedData data = AiGeneratedData.builder()
                .formDefinitions(List.of(Map.of(
                        "formName", "test_form",
                        "formType", "PROCESS",
                        "configJson", configJson
                )))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "INVALID_ENUM".equals(e.getErrorType())
                        && e.getFieldPath().contains("linkageType")))
                .isTrue();
    }

    /**
     * Property 5d: SummaryRule with invalid aggregation should produce INVALID_ENUM error.
     *
     * <p><b>Validates: Requirements 7.5</b></p>
     */
    @Property(tries = 100)
    void summaryRuleWithInvalidAggregationShouldError(
            @ForAll("invalidAggregation") String invalidAgg) {

        Map<String, Object> rule = new HashMap<>();
        rule.put("sourceColumn", "amount");
        rule.put("targetField", "total");
        rule.put("aggregation", invalidAgg);

        Map<String, Object> configJson = new HashMap<>();
        configJson.put("summaryRules", List.of(rule));

        AiGeneratedData data = AiGeneratedData.builder()
                .formDefinitions(List.of(Map.of(
                        "formName", "test_form",
                        "formType", "PROCESS",
                        "configJson", configJson
                )))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "INVALID_ENUM".equals(e.getErrorType())
                        && e.getFieldPath().contains("aggregation")))
                .isTrue();
    }

    /**
     * Property 5e: CrossFieldRule with empty fields should produce FIELD_CONSTRAINT error.
     *
     * <p><b>Validates: Requirements 7.4</b></p>
     */
    @Property(tries = 100)
    void crossFieldRuleWithEmptyFieldsShouldError(
            @ForAll("fieldName") String targetField) {

        Map<String, Object> rule = new HashMap<>();
        rule.put("fields", List.of());
        rule.put("message", "validation error");
        rule.put("targetField", targetField);

        Map<String, Object> configJson = new HashMap<>();
        configJson.put("crossFieldRules", List.of(rule));

        AiGeneratedData data = AiGeneratedData.builder()
                .formDefinitions(List.of(Map.of(
                        "formName", "test_form",
                        "formType", "PROCESS",
                        "configJson", configJson
                )))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "FIELD_CONSTRAINT".equals(e.getErrorType())
                        && e.getFieldPath().contains("crossFieldRules")
                        && e.getFieldPath().contains("fields")))
                .isTrue();
    }

    // --- Providers ---

    @Provide
    Arbitrary<String> invalidLinkageType() {
        java.util.Set<String> valid = java.util.Set.of("option-filtering", "value-auto-fill", "field-state-change");
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20)
                .filter(s -> !valid.contains(s));
    }

    @Provide
    Arbitrary<String> invalidAggregation() {
        java.util.Set<String> valid = java.util.Set.of("SUM", "AVG", "COUNT", "MIN", "MAX");
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10)
                .filter(s -> !valid.contains(s));
    }

    // --- Providers ---

    @Provide
    Arbitrary<String> fieldName() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30)
                .map(s -> "field_" + s);
    }
}
