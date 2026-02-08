package com.developer.util;

import com.platform.common.exception.VersionValidationError;
import com.platform.common.version.SemanticVersion;

/**
 * Utility class for input validation.
 * Provides common validation methods for function unit versioning operations.
 */
public class ValidationUtils {
    
    private ValidationUtils() {
        // Utility class, prevent instantiation
    }
    
    /**
     * Validate function unit name.
     * 
     * @param functionUnitName the function unit name to validate
     * @throws IllegalArgumentException if the name is invalid
     */
    public static void validateFunctionUnitName(String functionUnitName) {
        if (functionUnitName == null || functionUnitName.trim().isEmpty()) {
            throw new IllegalArgumentException("Function unit name cannot be null or empty");
        }
        
        if (functionUnitName.length() > 100) {
            throw new IllegalArgumentException(
                String.format("Function unit name too long: %d characters (max 100)", 
                        functionUnitName.length())
            );
        }
        
        // Check for valid characters (alphanumeric, dash, underscore)
        if (!functionUnitName.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException(
                String.format("Function unit name contains invalid characters: '%s'. " +
                        "Only alphanumeric characters, dashes, and underscores are allowed", 
                        functionUnitName)
            );
        }
    }
    
    /**
     * Validate change type.
     * 
     * @param changeType the change type to validate
     * @throws IllegalArgumentException if the change type is invalid
     */
    public static void validateChangeType(String changeType) {
        if (changeType == null || changeType.trim().isEmpty()) {
            throw new IllegalArgumentException("Change type cannot be null or empty");
        }
        
        if (!changeType.equals("major") && !changeType.equals("minor") && !changeType.equals("patch")) {
            throw new IllegalArgumentException(
                String.format("Invalid change type: '%s'. Must be 'major', 'minor', or 'patch'", 
                        changeType)
            );
        }
    }
    
    /**
     * Validate semantic version format.
     * 
     * @param version the version string to validate
     * @throws VersionValidationError if the version format is invalid
     */
    public static void validateVersionFormat(String version) {
        if (version == null || version.trim().isEmpty()) {
            throw new IllegalArgumentException("Version cannot be null or empty");
        }
        
        if (!SemanticVersion.isValid(version)) {
            throw VersionValidationError.invalidVersionFormat(version);
        }
    }
    
    /**
     * Validate version ID.
     * 
     * @param versionId the version ID to validate
     * @throws IllegalArgumentException if the version ID is invalid
     */
    public static void validateVersionId(Long versionId) {
        if (versionId == null) {
            throw new IllegalArgumentException("Version ID cannot be null");
        }
        
        if (versionId <= 0) {
            throw new IllegalArgumentException(
                String.format("Version ID must be positive: %d", versionId)
            );
        }
    }
    
    /**
     * Validate BPMN XML content.
     * 
     * @param bpmnXml the BPMN XML to validate
     * @throws IllegalArgumentException if the BPMN XML is invalid
     */
    public static void validateBpmnXml(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.trim().isEmpty()) {
            throw new IllegalArgumentException("BPMN XML cannot be null or empty");
        }
        
        // Basic XML validation - check for opening tag
        if (!bpmnXml.trim().startsWith("<")) {
            throw new IllegalArgumentException("BPMN XML must be valid XML content");
        }
    }
}
