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
                        "formType", "PROCESS",
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

    /**
     * Property 6a: Invalid fieldPermissions value should produce INVALID_ENUM error.
     *
     * <p><b>Validates: Requirements 8.1</b></p>
     */
    @Property(tries = 100)
    void invalidFieldPermissionValueShouldError(
            @ForAll("invalidEnumString") String invalidValue) {

        AiGeneratedData data = AiGeneratedData.builder()
                .formDefinitions(List.of(Map.of(
                        "formName", "test_form",
                        "formType", "PROCESS",
                        "fieldPermissions", Map.of("field1", invalidValue)
                )))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "INVALID_ENUM".equals(e.getErrorType())
                        && e.getFieldPath().contains("fieldPermissions")))
                .isTrue();
    }

    /**
     * Property 6b: Valid fieldPermissions values should not produce errors.
     *
     * <p><b>Validates: Requirements 8.1</b></p>
     */
    @Property(tries = 100)
    void validFieldPermissionValuesShouldPass(
            @ForAll("validPermission") String permission) {

        AiGeneratedData data = AiGeneratedData.builder()
                .formDefinitions(List.of(Map.of(
                        "formName", "test_form",
                        "formType", "PROCESS",
                        "fieldPermissions", Map.of("field1", permission)
                )))
                .build();

        AiValidationResult result = validationService.validate(data);

        long permErrors = result.getErrors().stream()
                .filter(e -> "INVALID_ENUM".equals(e.getErrorType())
                        && e.getFieldPath().contains("fieldPermissions"))
                .count();
        assertThat(permErrors).isZero();
    }

    /**
     * Property 6c: visibilityCondition as String should produce FORMAT_MISMATCH error.
     *
     * <p><b>Validates: Requirements 9.1, 9.3</b></p>
     */
    @Property(tries = 100)
    void visibilityConditionAsStringShouldError(
            @ForAll("invalidEnumString") String stringCondition) {

        java.util.Map<String, Object> configJson = new java.util.HashMap<>();
        configJson.put("visibilityCondition", stringCondition);

        AiGeneratedData data = AiGeneratedData.builder()
                .actionDefinitions(List.of(Map.of(
                        "actionName", "test_action",
                        "actionType", "APPROVE",
                        "configJson", configJson
                )))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "FORMAT_MISMATCH".equals(e.getErrorType())
                        && e.getFieldPath().contains("visibilityCondition")))
                .isTrue();
    }

    /**
     * Property 6d: visibilityCondition with invalid operator should produce INVALID_ENUM error.
     *
     * <p><b>Validates: Requirements 9.2</b></p>
     */
    @Property(tries = 100)
    void visibilityConditionInvalidOperatorShouldError(
            @ForAll("invalidEnumString") String invalidOperator) {

        java.util.Map<String, Object> condition = new java.util.HashMap<>();
        condition.put("field", "status");
        condition.put("operator", invalidOperator);
        condition.put("value", "active");

        java.util.Map<String, Object> configJson = new java.util.HashMap<>();
        configJson.put("visibilityCondition", condition);

        AiGeneratedData data = AiGeneratedData.builder()
                .actionDefinitions(List.of(Map.of(
                        "actionName", "test_action",
                        "actionType", "APPROVE",
                        "configJson", configJson
                )))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertThat(result.getErrors().stream()
                .anyMatch(e -> "INVALID_ENUM".equals(e.getErrorType())
                        && e.getFieldPath().contains("visibilityCondition.operator")))
                .isTrue();
    }

    /**
     * Property 15a: Deprecated FormType MAIN should produce warning, not error, and isValid should be true.
     *
     * <p><b>Validates: Requirements 19.1, 19.2, 19.3</b></p>
     */
    @Property(tries = 100)
    void deprecatedFormTypeMainShouldWarnNotError(
            @ForAll("fieldName") String formName) {

        AiGeneratedData data = AiGeneratedData.builder()
                .formDefinitions(List.of(Map.of(
                        "formName", formName,
                        "formType", "MAIN"
                )))
                .build();

        AiValidationResult result = validationService.validate(data);

        // Should produce DEPRECATED_ENUM warning
        assertThat(result.getWarnings().stream()
                .anyMatch(w -> "DEPRECATED_ENUM".equals(w.getErrorType())
                        && w.getFieldPath().contains("formType")
                        && w.getDescription().contains("PROCESS")))
                .isTrue();

        // Should NOT produce INVALID_ENUM error for formType
        assertThat(result.getErrors().stream()
                .noneMatch(e -> "INVALID_ENUM".equals(e.getErrorType())
                        && e.getFieldPath().contains("formType")))
                .isTrue();

        // isValid should be true (warnings don't affect validity)
        assertThat(result.isValid()).isTrue();
    }

    /**
     * Property 15b: Deprecated FormType SUB should produce warning, not error, and isValid should be true.
     *
     * <p><b>Validates: Requirements 19.1, 19.2, 19.3</b></p>
     */
    @Property(tries = 100)
    void deprecatedFormTypeSubShouldWarnNotError(
            @ForAll("fieldName") String formName) {

        AiGeneratedData data = AiGeneratedData.builder()
                .formDefinitions(List.of(Map.of(
                        "formName", formName,
                        "formType", "SUB"
                )))
                .build();

        AiValidationResult result = validationService.validate(data);

        // Should produce DEPRECATED_ENUM warning
        assertThat(result.getWarnings().stream()
                .anyMatch(w -> "DEPRECATED_ENUM".equals(w.getErrorType())
                        && w.getFieldPath().contains("formType")
                        && w.getDescription().contains("TASK")))
                .isTrue();

        // Should NOT produce INVALID_ENUM error for formType
        assertThat(result.getErrors().stream()
                .noneMatch(e -> "INVALID_ENUM".equals(e.getErrorType())
                        && e.getFieldPath().contains("formType")))
                .isTrue();

        // isValid should be true
        assertThat(result.isValid()).isTrue();
    }

    // --- Providers ---

    @Provide
    Arbitrary<String> validPermission() {
        return Arbitraries.of("READONLY", "EDITABLE");
    }

    @Provide
    Arbitrary<String> fieldName() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20)
                .map(s -> "f_" + s);
    }

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
                .filter(s -> !ALL_VALID_ENUMS.contains(s)
                        && !"MAIN".equals(s) && !"SUB".equals(s));
    }
}
