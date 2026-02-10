package com.platform.common.exception;

import com.platform.common.enums.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for VersionValidationError exception class.
 */
@DisplayName("VersionValidationError")
class VersionValidationErrorTest {
    
    @Test
    @DisplayName("should create validation error with field, value, and constraint")
    void shouldCreateWithFieldValueAndConstraint() {
        VersionValidationError error = new VersionValidationError(
            "version",
            "1.0",
            "Must match format MAJOR.MINOR.PATCH"
        );
        
        assertThat(error.getField()).isEqualTo("version");
        assertThat(error.getValue()).isEqualTo("1.0");
        assertThat(error.getConstraint()).isEqualTo("Must match format MAJOR.MINOR.PATCH");
        assertThat(error.getMessage()).contains("Validation failed for version");
        assertThat(error.getMessage()).contains("Must match format MAJOR.MINOR.PATCH");
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(error.getDetails()).containsEntry("field", "version");
        assertThat(error.getDetails()).containsEntry("value", "1.0");
        assertThat(error.getDetails()).containsEntry("constraint", "Must match format MAJOR.MINOR.PATCH");
    }
    
    @Test
    @DisplayName("should create validation error with custom message")
    void shouldCreateWithCustomMessage() {
        VersionValidationError error = new VersionValidationError(
            "changeType",
            "invalid",
            "Must be major, minor, or patch",
            "Custom error message"
        );
        
        assertThat(error.getField()).isEqualTo("changeType");
        assertThat(error.getValue()).isEqualTo("invalid");
        assertThat(error.getConstraint()).isEqualTo("Must be major, minor, or patch");
        assertThat(error.getMessage()).isEqualTo("Custom error message");
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }
    
    @Test
    @DisplayName("should create invalid version format error")
    void shouldCreateInvalidVersionFormat() {
        VersionValidationError error = VersionValidationError.invalidVersionFormat("1.0");
        
        assertThat(error.getField()).isEqualTo("version");
        assertThat(error.getValue()).isEqualTo("1.0");
        assertThat(error.getConstraint()).isEqualTo("Must match format MAJOR.MINOR.PATCH (e.g., 1.0.0)");
        assertThat(error.getMessage()).contains("Invalid semantic version format");
        assertThat(error.getMessage()).contains("1.0");
        assertThat(error.getMessage()).contains("Expected format: MAJOR.MINOR.PATCH");
    }
    
    @Test
    @DisplayName("should create duplicate version error")
    void shouldCreateDuplicateVersion() {
        VersionValidationError error = VersionValidationError.duplicateVersion("digital-lending", "1.2.3");
        
        assertThat(error.getField()).isEqualTo("version");
        assertThat(error.getValue()).isEqualTo("1.2.3");
        assertThat(error.getConstraint()).isEqualTo("Version must be unique for function unit");
        assertThat(error.getMessage()).contains("Version '1.2.3' already exists");
        assertThat(error.getMessage()).contains("digital-lending");
    }
    
    @Test
    @DisplayName("should create invalid change type error")
    void shouldCreateInvalidChangeType() {
        VersionValidationError error = VersionValidationError.invalidChangeType("invalid");
        
        assertThat(error.getField()).isEqualTo("changeType");
        assertThat(error.getValue()).isEqualTo("invalid");
        assertThat(error.getConstraint()).isEqualTo("Must be one of: major, minor, patch");
        assertThat(error.getMessage()).contains("Invalid change type: 'invalid'");
        assertThat(error.getMessage()).contains("Must be 'major', 'minor', or 'patch'");
    }
    
    @Test
    @DisplayName("should create rollback version not found error")
    void shouldCreateRollbackVersionNotFound() {
        VersionValidationError error = VersionValidationError.rollbackVersionNotFound(123L);
        
        assertThat(error.getField()).isEqualTo("versionId");
        assertThat(error.getValue()).isEqualTo(123L);
        assertThat(error.getConstraint()).isEqualTo("Target version must exist");
        assertThat(error.getMessage()).contains("Cannot rollback to version ID 123");
        assertThat(error.getMessage()).contains("version not found");
    }
    
    @Test
    @DisplayName("should create rollback to active version error")
    void shouldCreateRollbackToActiveVersion() {
        VersionValidationError error = VersionValidationError.rollbackToActiveVersion("1.0.0");
        
        assertThat(error.getField()).isEqualTo("version");
        assertThat(error.getValue()).isEqualTo("1.0.0");
        assertThat(error.getConstraint()).isEqualTo("Cannot rollback to already-active version");
        assertThat(error.getMessage()).contains("Version '1.0.0' is already active");
        assertThat(error.getMessage()).contains("cannot rollback to it");
    }
    
    @Test
    @DisplayName("should create empty function unit name error")
    void shouldCreateEmptyFunctionUnitName() {
        VersionValidationError error = VersionValidationError.emptyFunctionUnitName();
        
        assertThat(error.getField()).isEqualTo("functionUnitName");
        assertThat(error.getValue()).isNull();
        assertThat(error.getConstraint()).isEqualTo("Function unit name is required");
        assertThat(error.getMessage()).contains("Function unit name cannot be null or empty");
    }
    
    @Test
    @DisplayName("should be instance of PlatformException")
    void shouldBeInstanceOfPlatformException() {
        VersionValidationError error = new VersionValidationError("field", "value", "constraint");
        
        assertThat(error).isInstanceOf(PlatformException.class);
        assertThat(error).isInstanceOf(RuntimeException.class);
    }
    
    @Test
    @DisplayName("should include details in exception")
    void shouldIncludeDetailsInException() {
        VersionValidationError error = new VersionValidationError("testField", "testValue", "testConstraint");
        
        assertThat(error.getDetails()).isNotNull();
        assertThat(error.getDetails()).containsKey("field");
        assertThat(error.getDetails()).containsKey("value");
        assertThat(error.getDetails()).containsKey("constraint");
        assertThat(error.getDetails().get("field")).isEqualTo("testField");
        assertThat(error.getDetails().get("value")).isEqualTo("testValue");
        assertThat(error.getDetails().get("constraint")).isEqualTo("testConstraint");
    }
    
    @Test
    @DisplayName("should handle different value types")
    void shouldHandleDifferentValueTypes() {
        // Test with String
        VersionValidationError error1 = new VersionValidationError("field1", "string", "constraint");
        assertThat(error1.getValue()).isInstanceOf(String.class);
        
        // Test with Long
        VersionValidationError error2 = new VersionValidationError("field2", 123L, "constraint");
        assertThat(error2.getValue()).isInstanceOf(Long.class);
        
        // Test with null
        VersionValidationError error3 = new VersionValidationError("field3", null, "constraint");
        assertThat(error3.getValue()).isNull();
    }
    
    @Test
    @DisplayName("should format message consistently for factory methods")
    void shouldFormatMessageConsistentlyForFactoryMethods() {
        VersionValidationError error1 = VersionValidationError.invalidVersionFormat("1.0");
        VersionValidationError error2 = VersionValidationError.duplicateVersion("unit", "1.0.0");
        VersionValidationError error3 = VersionValidationError.invalidChangeType("bad");
        VersionValidationError error4 = VersionValidationError.rollbackVersionNotFound(1L);
        VersionValidationError error5 = VersionValidationError.rollbackToActiveVersion("1.0.0");
        VersionValidationError error6 = VersionValidationError.emptyFunctionUnitName();
        
        // All should have ErrorCode.VALIDATION_FAILED
        assertThat(error1.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(error2.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(error3.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(error4.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(error5.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(error6.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        
        // All should have non-empty messages
        assertThat(error1.getMessage()).isNotEmpty();
        assertThat(error2.getMessage()).isNotEmpty();
        assertThat(error3.getMessage()).isNotEmpty();
        assertThat(error4.getMessage()).isNotEmpty();
        assertThat(error5.getMessage()).isNotEmpty();
        assertThat(error6.getMessage()).isNotEmpty();
    }
    
    @Test
    @DisplayName("should provide helpful error messages for common validation scenarios")
    void shouldProvideHelpfulErrorMessages() {
        // Invalid format
        VersionValidationError formatError = VersionValidationError.invalidVersionFormat("v1.0.0");
        assertThat(formatError.getMessage()).contains("Invalid semantic version format");
        assertThat(formatError.getMessage()).contains("Expected format: MAJOR.MINOR.PATCH");
        
        // Duplicate version
        VersionValidationError duplicateError = VersionValidationError.duplicateVersion("test-unit", "2.0.0");
        assertThat(duplicateError.getMessage()).contains("already exists");
        assertThat(duplicateError.getMessage()).contains("test-unit");
        assertThat(duplicateError.getMessage()).contains("2.0.0");
        
        // Invalid change type
        VersionValidationError changeTypeError = VersionValidationError.invalidChangeType("huge");
        assertThat(changeTypeError.getMessage()).contains("Invalid change type");
        assertThat(changeTypeError.getMessage()).contains("major");
        assertThat(changeTypeError.getMessage()).contains("minor");
        assertThat(changeTypeError.getMessage()).contains("patch");
    }
}
