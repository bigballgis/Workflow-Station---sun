package com.developer.service;

import com.developer.dto.AiGeneratedData;
import com.developer.dto.AiValidationResult;
import com.developer.enums.*;
import com.developer.service.impl.AiValidationServiceImpl;
import net.jqwik.api.*;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Tag;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for AiValidationService enum value closedness validation.
 *
 * <p><b>Validates: Requirements 9.1</b></p>
 */
@Tag("Feature: ai-function-unit-generation, Property 4: 枚举值封闭性校验")
class AiEnumValidationProperties {

    private final AiValidationServiceImpl validationService = new AiValidationServiceImpl();

    // Collect all valid enum value strings for filtering
    private static final List<String> ALL_VALID_ENUMS;

    static {
        ALL_VALID_ENUMS = new java.util.ArrayList<>();
        Arrays.stream(TableType.values()).map(Enum::name).forEach(ALL_VALID_ENUMS::add);
        Arrays.stream(FormType.values()).map(Enum::name).forEach(ALL_VALID_ENUMS::add);
        Arrays.stream(DataType.values()).map(Enum::name).forEach(ALL_VALID_ENUMS::add);
        Arrays.stream(ActionType.values()).map(Enum::name).forEach(ALL_VALID_ENUMS::add);
        Arrays.stream(BindingType.values()).map(Enum::name).forEach(ALL_VALID_ENUMS::add);
        Arrays.stream(BindingMode.values()).map(Enum::name).forEach(ALL_VALID_ENUMS::add);
        Arrays.stream(IconCategory.values()).map(Enum::name).forEach(ALL_VALID_ENUMS::add);
    }

    /**
     * Property 4a: Valid enum values should produce no INVALID_ENUM errors.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void validEnumValuesShouldPass(
            @ForAll("validTableType") String tableType,
            @ForAll("validFormType") String formType,
            @ForAll("validDataType") String dataType,
            @ForAll("validActionType") String actionType,
            @ForAll("validBindingType") String bindingType,
            @ForAll("validBindingMode") String bindingMode,
            @ForAll("validIconCategory") String iconCategory) {

        AiGeneratedData data = AiGeneratedData.builder()
                .tableDefinitions(List.of(Map.of(
                        "tableName", "test_table",
                        "tableType", tableType,
                        "fieldDefinitions", List.of(Map.of(
                                "fieldName", "id",
                                "dataType", dataType,
                                "isPrimaryKey", true
                        ))
                )))
                .formDefinitions(List.of(Map.of(
                        "formName", "test_form",
                        "formType", formType,
                        "tableBindings", List.of(Map.of(
                                "tableName", "test_table",
                                "bindingType", bindingType,
                                "bindingMode", bindingMode
                        ))
                )))
                .actionDefinitions(List.of(Map.of(
                        "actionName", "test_action",
                        "actionType", actionType
                )))
                .icon(Map.of("category", iconCategory))
                .build();

        AiValidationResult result = validationService.validate(data);

        long enumErrors = result.getErrors().stream()
                .filter(e -> "INVALID_ENUM".equals(e.getErrorType()))
                .count();
        assertThat(enumErrors).isZero();
    }

    /**
     * Property 4b: Invalid enum values should produce INVALID_ENUM errors.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void invalidEnumValuesShouldError(
            @ForAll("invalidEnumString") String invalidValue) {

        // Test invalid tableType
        AiGeneratedData tableData = AiGeneratedData.builder()
                .tableDefinitions(List.of(Map.of(
                        "tableName", "t1",
                        "tableType", invalidValue,
                        "fieldDefinitions", List.of(Map.of(
                                "fieldName", "id",
                                "dataType", "INTEGER",
                                "isPrimaryKey", true
                        ))
                )))
                .build();

        AiValidationResult tableResult = validationService.validate(tableData);
        assertThat(tableResult.getErrors().stream()
                .anyMatch(e -> "INVALID_ENUM".equals(e.getErrorType())
                        && e.getFieldPath().contains("tableType")))
                .isTrue();

        // Test invalid formType
        AiGeneratedData formData = AiGeneratedData.builder()
                .formDefinitions(List.of(Map.of(
                        "formName", "f1",
                        "formType", invalidValue
                )))
                .build();

        AiValidationResult formResult = validationService.validate(formData);
        assertThat(formResult.getErrors().stream()
                .anyMatch(e -> "INVALID_ENUM".equals(e.getErrorType())
                        && e.getFieldPath().contains("formType")))
                .isTrue();

        // Test invalid actionType
        AiGeneratedData actionData = AiGeneratedData.builder()
                .actionDefinitions(List.of(Map.of(
                        "actionName", "a1",
                        "actionType", invalidValue
                )))
                .build();

        AiValidationResult actionResult = validationService.validate(actionData);
        assertThat(actionResult.getErrors().stream()
                .anyMatch(e -> "INVALID_ENUM".equals(e.getErrorType())
                        && e.getFieldPath().contains("actionType")))
                .isTrue();

        // Test invalid icon.category
        AiGeneratedData iconData = AiGeneratedData.builder()
                .icon(Map.of("category", invalidValue))
                .build();

        AiValidationResult iconResult = validationService.validate(iconData);
        assertThat(iconResult.getErrors().stream()
                .anyMatch(e -> "INVALID_ENUM".equals(e.getErrorType())
                        && e.getFieldPath().contains("icon.category")))
                .isTrue();
    }

    /**
     * Property 4c: Invalid bindingType/bindingMode should produce INVALID_ENUM errors.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void invalidBindingEnumsShouldError(
            @ForAll("invalidEnumString") String invalidValue) {

        AiGeneratedData data = AiGeneratedData.builder()
                .formDefinitions(List.of(Map.of(
                        "formName", "f1",
                        "formType", "MAIN",
                        "tableBindings", List.of(Map.of(
                                "tableName", "t1",
                                "bindingType", invalidValue,
                                "bindingMode", invalidValue
                        ))
                )))
                .build();

        AiValidationResult result = validationService.validate(data);
        assertThat(result.getErrors().stream()
                .anyMatch(e -> "INVALID_ENUM".equals(e.getErrorType())
                        && e.getFieldPath().contains("bindingType")))
                .isTrue();
        assertThat(result.getErrors().stream()
                .anyMatch(e -> "INVALID_ENUM".equals(e.getErrorType())
                        && e.getFieldPath().contains("bindingMode")))
                .isTrue();
    }

    /**
     * Property 4d: Invalid dataType should produce INVALID_ENUM error.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 100)
    void invalidDataTypeShouldError(
            @ForAll("invalidEnumString") String invalidValue) {

        AiGeneratedData data = AiGeneratedData.builder()
                .tableDefinitions(List.of(Map.of(
                        "tableName", "t1",
                        "tableType", "MAIN",
                        "fieldDefinitions", List.of(Map.of(
                                "fieldName", "col1",
                                "dataType", invalidValue,
                                "isPrimaryKey", true
                        ))
                )))
                .build();

        AiValidationResult result = validationService.validate(data);
        assertThat(result.getErrors().stream()
                .anyMatch(e -> "INVALID_ENUM".equals(e.getErrorType())
                        && e.getFieldPath().contains("dataType")))
                .isTrue();
    }

    // --- Providers ---

    @Provide
    Arbitrary<String> validTableType() {
        return Arbitraries.of(Arrays.stream(TableType.values()).map(Enum::name).collect(Collectors.toList()));
    }

    @Provide
    Arbitrary<String> validFormType() {
        return Arbitraries.of(Arrays.stream(FormType.values()).map(Enum::name).collect(Collectors.toList()));
    }

    @Provide
    Arbitrary<String> validDataType() {
        return Arbitraries.of(Arrays.stream(DataType.values()).map(Enum::name).collect(Collectors.toList()));
    }

    @Provide
    Arbitrary<String> validActionType() {
        return Arbitraries.of(Arrays.stream(ActionType.values()).map(Enum::name).collect(Collectors.toList()));
    }

    @Provide
    Arbitrary<String> validBindingType() {
        return Arbitraries.of(Arrays.stream(BindingType.values()).map(Enum::name).collect(Collectors.toList()));
    }

    @Provide
    Arbitrary<String> validBindingMode() {
        return Arbitraries.of(Arrays.stream(BindingMode.values()).map(Enum::name).collect(Collectors.toList()));
    }

    @Provide
    Arbitrary<String> validIconCategory() {
        return Arbitraries.of(Arrays.stream(IconCategory.values()).map(Enum::name).collect(Collectors.toList()));
    }

    @Provide
    Arbitrary<String> invalidEnumString() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                .filter(s -> !ALL_VALID_ENUMS.contains(s));
    }
}
