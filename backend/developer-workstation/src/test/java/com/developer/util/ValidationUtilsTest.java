package com.developer.util;

import com.platform.common.exception.VersionValidationError;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit tests for ValidationUtils.
 * Tests input validation for function unit versioning operations.
 */
class ValidationUtilsTest {
    
    @Test
    void validateFunctionUnitName_validName_shouldNotThrow() {
        assertDoesNotThrow(() -> ValidationUtils.validateFunctionUnitName("my-function-unit"));
        assertDoesNotThrow(() -> ValidationUtils.validateFunctionUnitName("function_unit_123"));
        assertDoesNotThrow(() -> ValidationUtils.validateFunctionUnitName("FunctionUnit"));
    }
    
    @Test
    void validateFunctionUnitName_nullName_shouldThrowException() {
        assertThatThrownBy(() -> ValidationUtils.validateFunctionUnitName(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
    }
    
    @Test
    void validateFunctionUnitName_emptyName_shouldThrowException() {
        assertThatThrownBy(() -> ValidationUtils.validateFunctionUnitName(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
        
        assertThatThrownBy(() -> ValidationUtils.validateFunctionUnitName("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
    }
    
    @Test
    void validateFunctionUnitName_tooLong_shouldThrowException() {
        String longName = "a".repeat(101);
        assertThatThrownBy(() -> ValidationUtils.validateFunctionUnitName(longName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too long");
    }
    
    @Test
    void validateFunctionUnitName_invalidCharacters_shouldThrowException() {
        assertThatThrownBy(() -> ValidationUtils.validateFunctionUnitName("function unit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
        
        assertThatThrownBy(() -> ValidationUtils.validateFunctionUnitName("function@unit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }
    
    @Test
    void validateChangeType_validType_shouldNotThrow() {
        assertDoesNotThrow(() -> ValidationUtils.validateChangeType("major"));
        assertDoesNotThrow(() -> ValidationUtils.validateChangeType("minor"));
        assertDoesNotThrow(() -> ValidationUtils.validateChangeType("patch"));
    }
    
    @Test
    void validateChangeType_nullType_shouldThrowException() {
        assertThatThrownBy(() -> ValidationUtils.validateChangeType(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
    }
    
    @Test
    void validateChangeType_invalidType_shouldThrowException() {
        assertThatThrownBy(() -> ValidationUtils.validateChangeType("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid change type");
        
        assertThatThrownBy(() -> ValidationUtils.validateChangeType("MAJOR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid change type");
    }
    
    @Test
    void validateVersionFormat_validVersion_shouldNotThrow() {
        assertDoesNotThrow(() -> ValidationUtils.validateVersionFormat("1.0.0"));
        assertDoesNotThrow(() -> ValidationUtils.validateVersionFormat("10.20.30"));
        assertDoesNotThrow(() -> ValidationUtils.validateVersionFormat("0.0.1"));
    }
    
    @Test
    void validateVersionFormat_nullVersion_shouldThrowException() {
        assertThatThrownBy(() -> ValidationUtils.validateVersionFormat(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
    }
    
    @Test
    void validateVersionFormat_invalidFormat_shouldThrowException() {
        assertThatThrownBy(() -> ValidationUtils.validateVersionFormat("1.0"))
                .isInstanceOf(VersionValidationError.class);
        
        assertThatThrownBy(() -> ValidationUtils.validateVersionFormat("1.0.0.0"))
                .isInstanceOf(VersionValidationError.class);
        
        assertThatThrownBy(() -> ValidationUtils.validateVersionFormat("v1.0.0"))
                .isInstanceOf(VersionValidationError.class);
    }
    
    @Test
    void validateVersionId_validId_shouldNotThrow() {
        assertDoesNotThrow(() -> ValidationUtils.validateVersionId(1L));
        assertDoesNotThrow(() -> ValidationUtils.validateVersionId(100L));
    }
    
    @Test
    void validateVersionId_nullId_shouldThrowException() {
        assertThatThrownBy(() -> ValidationUtils.validateVersionId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }
    
    @Test
    void validateVersionId_negativeId_shouldThrowException() {
        assertThatThrownBy(() -> ValidationUtils.validateVersionId(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive");
        
        assertThatThrownBy(() -> ValidationUtils.validateVersionId(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive");
    }
    
    @Test
    void validateBpmnXml_validXml_shouldNotThrow() {
        assertDoesNotThrow(() -> ValidationUtils.validateBpmnXml("<bpmn>content</bpmn>"));
        assertDoesNotThrow(() -> ValidationUtils.validateBpmnXml("<?xml version=\"1.0\"?><bpmn/>"));
    }
    
    @Test
    void validateBpmnXml_nullXml_shouldThrowException() {
        assertThatThrownBy(() -> ValidationUtils.validateBpmnXml(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
    }
    
    @Test
    void validateBpmnXml_emptyXml_shouldThrowException() {
        assertThatThrownBy(() -> ValidationUtils.validateBpmnXml(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
    }
    
    @Test
    void validateBpmnXml_invalidXml_shouldThrowException() {
        assertThatThrownBy(() -> ValidationUtils.validateBpmnXml("not xml content"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be valid XML");
    }
}
