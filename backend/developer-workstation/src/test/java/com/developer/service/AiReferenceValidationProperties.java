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
 * Property-based tests for AiValidationService reference integrity validation.
 *
 * <p><b>Validates: Requirements 9.1</b></p>
 */
@Tag("Feature: ai-function-unit-generation, Property 7: 引用完整性校验")
class AiReferenceValidationProperties {

    private final AiValidationServiceImpl validationService = new AiValidationServiceImpl();

    /**
     * Property 7a: Foreign key referencing a non-existent table should produce REFERENCE_INTEGRITY error.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void fkReferencingNonExistentTableShouldError(
            @ForAll("tableName") String existingTable,
            @ForAll("tableName") String nonExistentTable) {

        Assume.that(!existingTable.equals(nonExistentTable));

        Map<String, Object> fk = new HashMap<>();
        fk.put("refTableName", nonExistentTable);
        fk.put("refFieldName", "id");

        Map<String, Object> table = new HashMap<>();
        table.put("tableName", existingTable);
        table.put("tableType", "MAIN");
        table.put("fieldDefinitions", List.of(Map.of(
                "fieldName", "id",
                "dataType", "INTEGER",
                "isPrimaryKey", true
        )));
        table.put("foreignKeys", List.of(fk));

        AiGeneratedData data = AiGeneratedData.builder()
                .tableDefinitions(List.of(table))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "REFERENCE_INTEGRITY".equals(e.getErrorType())
                        && e.getFieldPath().contains("refTableName")))
                .isTrue();
    }

    /**
     * Property 7b: Foreign key referencing a non-existent field in an existing table should produce REFERENCE_INTEGRITY error.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void fkReferencingNonExistentFieldShouldError(
            @ForAll("tableName") String sourceTable,
            @ForAll("tableName") String refTable,
            @ForAll("fieldName") String existingField,
            @ForAll("fieldName") String nonExistentField) {

        Assume.that(!sourceTable.equals(refTable));
        Assume.that(!existingField.equals(nonExistentField));

        Map<String, Object> fk = new HashMap<>();
        fk.put("refTableName", refTable);
        fk.put("refFieldName", nonExistentField);

        Map<String, Object> table1 = new HashMap<>();
        table1.put("tableName", sourceTable);
        table1.put("tableType", "MAIN");
        table1.put("fieldDefinitions", List.of(Map.of(
                "fieldName", "id",
                "dataType", "INTEGER",
                "isPrimaryKey", true
        )));
        table1.put("foreignKeys", List.of(fk));

        Map<String, Object> table2 = new HashMap<>();
        table2.put("tableName", refTable);
        table2.put("tableType", "SUB");
        table2.put("fieldDefinitions", List.of(Map.of(
                "fieldName", existingField,
                "dataType", "INTEGER",
                "isPrimaryKey", true
        )));

        AiGeneratedData data = AiGeneratedData.builder()
                .tableDefinitions(List.of(table1, table2))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "REFERENCE_INTEGRITY".equals(e.getErrorType())
                        && e.getFieldPath().contains("refFieldName")))
                .isTrue();
    }

    /**
     * Property 7c: Valid foreign key references should not produce REFERENCE_INTEGRITY errors.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void validFkReferencesShouldPass(
            @ForAll("tableName") String sourceTable,
            @ForAll("tableName") String refTable,
            @ForAll("fieldName") String refField) {

        Assume.that(!sourceTable.equals(refTable));

        Map<String, Object> fk = new HashMap<>();
        fk.put("refTableName", refTable);
        fk.put("refFieldName", refField);

        Map<String, Object> table1 = new HashMap<>();
        table1.put("tableName", sourceTable);
        table1.put("tableType", "MAIN");
        table1.put("fieldDefinitions", List.of(Map.of(
                "fieldName", "id",
                "dataType", "INTEGER",
                "isPrimaryKey", true
        )));
        table1.put("foreignKeys", List.of(fk));

        Map<String, Object> table2 = new HashMap<>();
        table2.put("tableName", refTable);
        table2.put("tableType", "SUB");
        table2.put("fieldDefinitions", List.of(Map.of(
                "fieldName", refField,
                "dataType", "INTEGER",
                "isPrimaryKey", true
        )));

        AiGeneratedData data = AiGeneratedData.builder()
                .tableDefinitions(List.of(table1, table2))
                .build();

        AiValidationResult result = validationService.validate(data);

        long refErrors = result.getErrors().stream()
                .filter(e -> "REFERENCE_INTEGRITY".equals(e.getErrorType()))
                .count();
        assertThat(refErrors).isZero();
    }

    /**
     * Property 7d: Form table binding referencing a non-existent table should produce REFERENCE_INTEGRITY error.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void formBindingReferencingNonExistentTableShouldError(
            @ForAll("tableName") String existingTable,
            @ForAll("tableName") String nonExistentTable) {

        Assume.that(!existingTable.equals(nonExistentTable));

        AiGeneratedData data = AiGeneratedData.builder()
                .tableDefinitions(List.of(Map.of(
                        "tableName", existingTable,
                        "tableType", "MAIN",
                        "fieldDefinitions", List.of(Map.of(
                                "fieldName", "id",
                                "dataType", "INTEGER",
                                "isPrimaryKey", true
                        ))
                )))
                .formDefinitions(List.of(Map.of(
                        "formName", "test_form",
                        "formType", "MAIN",
                        "tableBindings", List.of(Map.of(
                                "tableName", nonExistentTable,
                                "bindingType", "PRIMARY",
                                "bindingMode", "EDITABLE"
                        ))
                )))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "REFERENCE_INTEGRITY".equals(e.getErrorType())
                        && e.getFieldPath().contains("tableBindings")))
                .isTrue();
    }

    /**
     * Property 7e: Form table binding referencing an existing table should not produce REFERENCE_INTEGRITY error.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void formBindingReferencingExistingTableShouldPass(
            @ForAll("tableName") String tableName) {

        AiGeneratedData data = AiGeneratedData.builder()
                .tableDefinitions(List.of(Map.of(
                        "tableName", tableName,
                        "tableType", "MAIN",
                        "fieldDefinitions", List.of(Map.of(
                                "fieldName", "id",
                                "dataType", "INTEGER",
                                "isPrimaryKey", true
                        ))
                )))
                .formDefinitions(List.of(Map.of(
                        "formName", "test_form",
                        "formType", "MAIN",
                        "tableBindings", List.of(Map.of(
                                "tableName", tableName,
                                "bindingType", "PRIMARY",
                                "bindingMode", "EDITABLE"
                        ))
                )))
                .build();

        AiValidationResult result = validationService.validate(data);

        long refErrors = result.getErrors().stream()
                .filter(e -> "REFERENCE_INTEGRITY".equals(e.getErrorType()))
                .count();
        assertThat(refErrors).isZero();
    }

    // --- Providers ---

    @Provide
    Arbitrary<String> tableName() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20)
                .map(s -> "tbl_" + s);
    }

    @Provide
    Arbitrary<String> fieldName() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20)
                .map(s -> "fld_" + s);
    }
}
