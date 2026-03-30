package com.developer.service;

import com.developer.dto.AiGeneratedData;
import com.developer.dto.AiValidationResult;
import com.developer.service.impl.AiValidationServiceImpl;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for AiValidationService uniqueness validation.
 *
 * <p><b>Validates: Requirements 9.1</b></p>
 */
@Tag("Feature: ai-function-unit-generation, Property 8: 唯一性校验")
class AiUniquenessValidationProperties {

    private final AiValidationServiceImpl validationService = new AiValidationServiceImpl();

    /**
     * Property 8a: Duplicate tableNames should produce UNIQUENESS error.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void duplicateTableNamesShouldError(
            @ForAll("name") String tableName) {

        Map<String, Object> pkField = Map.of(
                "fieldName", "id",
                "dataType", "INTEGER",
                "isPrimaryKey", true
        );

        AiGeneratedData data = AiGeneratedData.builder()
                .tableDefinitions(List.of(
                        Map.of("tableName", tableName, "tableType", "MAIN",
                                "fieldDefinitions", List.of(pkField)),
                        Map.of("tableName", tableName, "tableType", "SUB",
                                "fieldDefinitions", List.of(pkField))
                ))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "UNIQUENESS".equals(e.getErrorType())
                        && e.getFieldPath().contains("tableName")))
                .isTrue();
    }

    /**
     * Property 8b: Unique tableNames should not produce UNIQUENESS error for tableName.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void uniqueTableNamesShouldPass(
            @ForAll("name") String name1,
            @ForAll("name") String name2) {

        Assume.that(!name1.equals(name2));

        Map<String, Object> pkField = Map.of(
                "fieldName", "id",
                "dataType", "INTEGER",
                "isPrimaryKey", true
        );

        AiGeneratedData data = AiGeneratedData.builder()
                .tableDefinitions(List.of(
                        Map.of("tableName", name1, "tableType", "MAIN",
                                "fieldDefinitions", List.of(pkField)),
                        Map.of("tableName", name2, "tableType", "SUB",
                                "fieldDefinitions", List.of(pkField))
                ))
                .build();

        AiValidationResult result = validationService.validate(data);

        long uniquenessErrors = result.getErrors().stream()
                .filter(e -> "UNIQUENESS".equals(e.getErrorType())
                        && e.getFieldPath().contains("tableName"))
                .count();
        assertThat(uniquenessErrors).isZero();
    }

    /**
     * Property 8c: Duplicate formNames should produce UNIQUENESS error.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void duplicateFormNamesShouldError(
            @ForAll("name") String formName) {

        AiGeneratedData data = AiGeneratedData.builder()
                .formDefinitions(List.of(
                        Map.of("formName", formName, "formType", "PROCESS"),
                        Map.of("formName", formName, "formType", "TASK")
                ))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "UNIQUENESS".equals(e.getErrorType())
                        && e.getFieldPath().contains("formName")))
                .isTrue();
    }

    /**
     * Property 8d: Unique formNames should not produce UNIQUENESS error for formName.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void uniqueFormNamesShouldPass(
            @ForAll("name") String name1,
            @ForAll("name") String name2) {

        Assume.that(!name1.equals(name2));

        AiGeneratedData data = AiGeneratedData.builder()
                .formDefinitions(List.of(
                        Map.of("formName", name1, "formType", "PROCESS"),
                        Map.of("formName", name2, "formType", "TASK")
                ))
                .build();

        AiValidationResult result = validationService.validate(data);

        long uniquenessErrors = result.getErrors().stream()
                .filter(e -> "UNIQUENESS".equals(e.getErrorType())
                        && e.getFieldPath().contains("formName"))
                .count();
        assertThat(uniquenessErrors).isZero();
    }

    /**
     * Property 8e: Duplicate actionNames should produce UNIQUENESS error.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void duplicateActionNamesShouldError(
            @ForAll("name") String actionName) {

        AiGeneratedData data = AiGeneratedData.builder()
                .actionDefinitions(List.of(
                        Map.of("actionName", actionName, "actionType", "APPROVE"),
                        Map.of("actionName", actionName, "actionType", "REJECT")
                ))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "UNIQUENESS".equals(e.getErrorType())
                        && e.getFieldPath().contains("actionName")))
                .isTrue();
    }

    /**
     * Property 8f: Unique actionNames should not produce UNIQUENESS error for actionName.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void uniqueActionNamesShouldPass(
            @ForAll("name") String name1,
            @ForAll("name") String name2) {

        Assume.that(!name1.equals(name2));

        AiGeneratedData data = AiGeneratedData.builder()
                .actionDefinitions(List.of(
                        Map.of("actionName", name1, "actionType", "APPROVE"),
                        Map.of("actionName", name2, "actionType", "REJECT")
                ))
                .build();

        AiValidationResult result = validationService.validate(data);

        long uniquenessErrors = result.getErrors().stream()
                .filter(e -> "UNIQUENESS".equals(e.getErrorType())
                        && e.getFieldPath().contains("actionName"))
                .count();
        assertThat(uniquenessErrors).isZero();
    }

    /**
     * Property 8g: Duplicate fieldNames within the same table should produce UNIQUENESS error.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void duplicateFieldNamesInSameTableShouldError(
            @ForAll("name") String fieldName) {

        AiGeneratedData data = AiGeneratedData.builder()
                .tableDefinitions(List.of(Map.of(
                        "tableName", "test_table",
                        "tableType", "MAIN",
                        "fieldDefinitions", List.of(
                                Map.of("fieldName", fieldName, "dataType", "INTEGER", "isPrimaryKey", true),
                                Map.of("fieldName", fieldName, "dataType", "VARCHAR", "length", 100, "isPrimaryKey", false)
                        )
                )))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "UNIQUENESS".equals(e.getErrorType())
                        && e.getFieldPath().contains("fieldName")))
                .isTrue();
    }

    /**
     * Property 8h: Unique fieldNames within the same table should not produce UNIQUENESS error for fieldName.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void uniqueFieldNamesInSameTableShouldPass(
            @ForAll("name") String name1,
            @ForAll("name") String name2) {

        Assume.that(!name1.equals(name2));

        AiGeneratedData data = AiGeneratedData.builder()
                .tableDefinitions(List.of(Map.of(
                        "tableName", "test_table",
                        "tableType", "MAIN",
                        "fieldDefinitions", List.of(
                                Map.of("fieldName", name1, "dataType", "INTEGER", "isPrimaryKey", true),
                                Map.of("fieldName", name2, "dataType", "VARCHAR", "length", 100, "isPrimaryKey", false)
                        )
                )))
                .build();

        AiValidationResult result = validationService.validate(data);

        long uniquenessErrors = result.getErrors().stream()
                .filter(e -> "UNIQUENESS".equals(e.getErrorType())
                        && e.getFieldPath().contains("fieldName"))
                .count();
        assertThat(uniquenessErrors).isZero();
    }

    // --- Providers ---

    @Provide
    Arbitrary<String> name() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20)
                .map(s -> "n_" + s);
    }
}
