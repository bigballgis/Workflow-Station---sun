package com.developer.service;

import com.developer.dto.AiGeneratedData;
import com.developer.dto.AiValidationResult;
import com.developer.service.impl.AiValidationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AiValidationService 单元测试
 * 校验具体场景示例：BPMN XML 格式、枚举值边界条件、SVG 安全校验
 */
class AiValidationServiceTest {

    private AiValidationServiceImpl validationService;

    @BeforeEach
    void setUp() {
        validationService = new AiValidationServiceImpl();
    }

    // ==================== BPMN XML Validation ====================

    @Test
    void validate_validBpmnXml_shouldPass() {
        String validBpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             targetNamespace="http://example.com">
                  <process id="process1" isExecutable="true">
                    <startEvent id="start"/>
                  </process>
                </definitions>
                """;

        AiGeneratedData data = AiGeneratedData.builder()
                .processDefinition(Map.of("bpmnXml", validBpmn))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertTrue(result.isValid(), "Valid BPMN XML should pass validation");
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void validate_invalidBpmnXml_shouldFail() {
        String invalidXml = "<not-valid-xml><unclosed>";

        AiGeneratedData data = AiGeneratedData.builder()
                .processDefinition(Map.of("bpmnXml", invalidXml))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertFalse(result.isValid(), "Invalid XML should fail validation");
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> "BPMN_VALIDATION".equals(e.getErrorType())));
    }

    // ==================== Enum Value Validation ====================

    @Test
    void validate_validEnumValues_shouldPass() {
        AiGeneratedData data = AiGeneratedData.builder()
                .tableDefinitions(List.of(Map.of(
                        "tableName", "test_table",
                        "tableType", "MAIN",
                        "fieldDefinitions", List.of(Map.of(
                                "fieldName", "id",
                                "dataType", "BIGINT",
                                "isPrimaryKey", true
                        ))
                )))
                .formDefinitions(List.of(Map.of(
                        "formName", "test_form",
                        "formType", "MAIN"
                )))
                .actionDefinitions(List.of(Map.of(
                        "actionName", "test_action",
                        "actionType", "APPROVE"
                )))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertTrue(result.isValid(), "All valid enum values should pass");
    }

    @Test
    void validate_invalidTableType_shouldFail() {
        AiGeneratedData data = AiGeneratedData.builder()
                .tableDefinitions(List.of(Map.of(
                        "tableName", "test_table",
                        "tableType", "INVALID_TYPE",
                        "fieldDefinitions", List.of(Map.of(
                                "fieldName", "id",
                                "dataType", "BIGINT",
                                "isPrimaryKey", true
                        ))
                )))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertFalse(result.isValid(), "Invalid tableType should fail");
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> "INVALID_ENUM".equals(e.getErrorType())
                        && e.getFieldPath().contains("tableType")));
    }

    // ==================== SVG Validation ====================

    @Test
    void validate_svgWithScript_shouldFail() {
        String maliciousSvg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="48" height="48">
                  <script>alert('xss')</script>
                  <circle cx="24" cy="24" r="20"/>
                </svg>
                """;

        AiGeneratedData data = AiGeneratedData.builder()
                .icon(Map.of(
                        "name", "test-icon",
                        "category", "GENERAL",
                        "svgContent", maliciousSvg
                ))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertFalse(result.isValid(), "SVG with script tag should fail");
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> "SVG_VALIDATION".equals(e.getErrorType())
                        && e.getDescription().contains("script")));
    }

    @Test
    void validate_validSvg_shouldPass() {
        String cleanSvg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 48 48">
                  <circle cx="24" cy="24" r="20" fill="#4A90D9"/>
                </svg>
                """;

        AiGeneratedData data = AiGeneratedData.builder()
                .icon(Map.of(
                        "name", "clean-icon",
                        "category", "GENERAL",
                        "svgContent", cleanSvg
                ))
                .build();

        AiValidationResult result = validationService.validate(data);

        assertTrue(result.isValid(), "Clean SVG should pass validation");
    }
}
