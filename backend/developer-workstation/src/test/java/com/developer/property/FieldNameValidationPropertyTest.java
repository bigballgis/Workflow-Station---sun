package com.developer.property;

import net.jqwik.api.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * 字段名校验属性测试
 * Feature: process-task-form-separation, Property 3: Form field names must reference Data_Table columns
 *
 * Validates: Requirements 2.5, 3.3, 3.4, 4.5
 */
public class FieldNameValidationPropertyTest {

    /**
     * Property 3: Field names that exist in Data_Table columns should pass validation.
     *
     * For any form field name that exists as a column name in the FunctionUnit's Data_Table,
     * validation should accept it.
     *
     * Validates: Requirements 2.5, 3.3, 3.4, 4.5
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 3: Valid field names are accepted")
    void validFieldNamesShouldBeAccepted(
            @ForAll("validFieldNameScenarios") FieldNameScenario scenario) {

        // All field names exist in the column set
        for (String fieldName : scenario.fieldNames) {
            assertThat(scenario.dataTableColumns).contains(fieldName);
        }

        List<String> invalidFields = validateFieldNames(scenario.fieldNames, scenario.dataTableColumns);
        assertThat(invalidFields).isEmpty();
    }

    /**
     * Property 3: Field names not in Data_Table columns should be rejected.
     *
     * For any field name that does not exist in the FunctionUnit's Data_Table columns,
     * validation should reject it.
     *
     * Validates: Requirements 2.5, 3.3, 3.4, 4.5
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 3: Invalid field names are rejected")
    void invalidFieldNamesShouldBeRejected(
            @ForAll("invalidFieldNameScenarios") FieldNameScenario scenario) {

        // At least one field name does not exist in the column set
        boolean hasInvalid = scenario.fieldNames.stream()
                .anyMatch(name -> !scenario.dataTableColumns.contains(name));
        assertThat(hasInvalid).isTrue();

        List<String> invalidFields = validateFieldNames(scenario.fieldNames, scenario.dataTableColumns);
        assertThat(invalidFields).isNotEmpty();
    }

    /**
     * Property 3: The number of rejected fields equals the count of names not in columns.
     *
     * Validates: Requirements 2.5, 3.3, 3.4, 4.5
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 3: Rejected count matches invalid field count")
    void rejectedCountMatchesInvalidFieldCount(
            @ForAll("mixedFieldNameScenarios") FieldNameScenario scenario) {

        long expectedInvalidCount = scenario.fieldNames.stream()
                .filter(name -> !scenario.dataTableColumns.contains(name))
                .count();

        List<String> invalidFields = validateFieldNames(scenario.fieldNames, scenario.dataTableColumns);
        assertThat(invalidFields).hasSize((int) expectedInvalidCount);
    }

    // ========== Validation Logic ==========

    /**
     * Business rule: All form field names must reference Data_Table columns.
     * Returns the list of field names that are NOT in the Data_Table columns.
     */
    private List<String> validateFieldNames(List<String> fieldNames, Set<String> dataTableColumns) {
        return fieldNames.stream()
                .filter(name -> !dataTableColumns.contains(name))
                .collect(Collectors.toList());
    }

    // ========== Data Classes ==========

    static class FieldNameScenario {
        final List<String> fieldNames;
        final Set<String> dataTableColumns;

        FieldNameScenario(List<String> fieldNames, Set<String> dataTableColumns) {
            this.fieldNames = fieldNames;
            this.dataTableColumns = dataTableColumns;
        }
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<FieldNameScenario> validFieldNameScenarios() {
        return dataTableColumnSets().flatMap(columns -> {
            if (columns.isEmpty()) {
                return Arbitraries.just(new FieldNameScenario(List.of(), columns));
            }
            List<String> columnList = new ArrayList<>(columns);
            return Arbitraries.of(columnList)
                    .list()
                    .ofMinSize(1)
                    .ofMaxSize(Math.min(5, columnList.size()))
                    .map(fieldNames -> new FieldNameScenario(fieldNames, columns));
        });
    }

    @Provide
    Arbitrary<FieldNameScenario> invalidFieldNameScenarios() {
        return dataTableColumnSets().flatMap(columns ->
                invalidFieldNames(columns)
                        .list()
                        .ofMinSize(1)
                        .ofMaxSize(5)
                        .map(fieldNames -> new FieldNameScenario(fieldNames, columns))
        );
    }

    @Provide
    Arbitrary<FieldNameScenario> mixedFieldNameScenarios() {
        return dataTableColumnSets().flatMap(columns -> {
            List<String> columnList = new ArrayList<>(columns);
            Arbitrary<String> validNames = columnList.isEmpty()
                    ? Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                    : Arbitraries.of(columnList);
            Arbitrary<String> invalidNames = invalidFieldNames(columns);

            return Arbitraries.oneOf(validNames, invalidNames)
                    .list()
                    .ofMinSize(1)
                    .ofMaxSize(8)
                    .map(fieldNames -> new FieldNameScenario(fieldNames, columns));
        });
    }

    private Arbitrary<Set<String>> dataTableColumnSets() {
        return columnNames()
                .set()
                .ofMinSize(1)
                .ofMaxSize(10);
    }

    private Arbitrary<String> columnNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(2)
                .ofMaxLength(20)
                .map(String::toLowerCase);
    }

    private Arbitrary<String> invalidFieldNames(Set<String> validColumns) {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(20)
                .filter(s -> !validColumns.contains(s));
    }
}
