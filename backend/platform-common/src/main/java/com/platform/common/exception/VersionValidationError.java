package com.platform.common.exception;

import com.platform.common.enums.ErrorCode;
import lombok.Getter;

import java.util.Map;

/**
 * Exception for validation failures specific to versioned deployment operations.
 * Used when version-related validation fails (format, duplicates, constraints).
 * Maps to HTTP 400 Bad Request.
 * 
 * Examples:
 * - Invalid semantic version format
 * - Duplicate version number
 * - Invalid change type
 * - Rollback to non-existent or already-active version
 */
@Getter
public class VersionValidationError extends PlatformException {
    
    private final String field;
    private final Object value;
    private final String constraint;
    
    /**
     * Create a version validation error with field, value, and constraint
     * 
     * @param field The field that failed validation
     * @param value The invalid value
     * @param constraint Description of the constraint that was violated
     */
    public VersionValidationError(String field, Object value, String constraint) {
        super(ErrorCode.VALIDATION_FAILED,
              String.format("Validation failed for %s: %s", field, constraint),
              createDetailsMap(field, value, constraint));
        this.field = field;
        this.value = value;
        this.constraint = constraint;
    }
    
    /**
     * Helper method to create details map that handles null values
     */
    private static Map<String, Object> createDetailsMap(String field, Object value, String constraint) {
        Map<String, Object> details = new java.util.HashMap<>();
        details.put("field", field);
        details.put("value", value);
        details.put("constraint", constraint);
        return details;
    }
    
    /**
     * Create a version validation error with custom message
     * 
     * @param field The field that failed validation
     * @param value The invalid value
     * @param constraint Description of the constraint
     * @param message Custom error message
     */
    public VersionValidationError(String field, Object value, String constraint, String message) {
        super(ErrorCode.VALIDATION_FAILED,
              message,
              createDetailsMap(field, value, constraint));
        this.field = field;
        this.value = value;
        this.constraint = constraint;
    }
    
    /**
     * Create error for invalid semantic version format
     * 
     * @param version The invalid version string
     * @return VersionValidationError instance
     */
    public static VersionValidationError invalidVersionFormat(String version) {
        return new VersionValidationError(
            "version",
            version,
            "Must match format MAJOR.MINOR.PATCH (e.g., 1.0.0)",
            String.format("Invalid semantic version format: '%s'. Expected format: MAJOR.MINOR.PATCH", version)
        );
    }
    
    /**
     * Create error for duplicate version
     * 
     * @param functionUnitName The function unit name
     * @param version The duplicate version
     * @return VersionValidationError instance
     */
    public static VersionValidationError duplicateVersion(String functionUnitName, String version) {
        return new VersionValidationError(
            "version",
            version,
            "Version must be unique for function unit",
            String.format("Version '%s' already exists for function unit '%s'", version, functionUnitName)
        );
    }
    
    /**
     * Create error for invalid change type
     * 
     * @param changeType The invalid change type
     * @return VersionValidationError instance
     */
    public static VersionValidationError invalidChangeType(String changeType) {
        return new VersionValidationError(
            "changeType",
            changeType,
            "Must be one of: major, minor, patch",
            String.format("Invalid change type: '%s'. Must be 'major', 'minor', or 'patch'", changeType)
        );
    }
    
    /**
     * Create error for rollback to non-existent version
     * 
     * @param versionId The non-existent version ID
     * @return VersionValidationError instance
     */
    public static VersionValidationError rollbackVersionNotFound(Long versionId) {
        return new VersionValidationError(
            "versionId",
            versionId,
            "Target version must exist",
            String.format("Cannot rollback to version ID %d: version not found", versionId)
        );
    }
    
    /**
     * Create error for rollback to already-active version
     * 
     * @param version The version string
     * @return VersionValidationError instance
     */
    public static VersionValidationError rollbackToActiveVersion(String version) {
        return new VersionValidationError(
            "version",
            version,
            "Cannot rollback to already-active version",
            String.format("Version '%s' is already active, cannot rollback to it", version)
        );
    }
    
    /**
     * Create error for empty or null function unit name
     * 
     * @return VersionValidationError instance
     */
    public static VersionValidationError emptyFunctionUnitName() {
        return new VersionValidationError(
            "functionUnitName",
            null,
            "Function unit name is required",
            "Function unit name cannot be null or empty"
        );
    }
    
    /**
     * Create error for invalid rollback operation
     * 
     * @param version The target version
     * @param reason The reason why rollback is invalid
     * @return VersionValidationError instance
     */
    public static VersionValidationError invalidRollback(String version, String reason) {
        return new VersionValidationError(
            "version",
            version,
            "Rollback operation is invalid",
            String.format("Cannot rollback to version '%s': %s", version, reason)
        );
    }
}
