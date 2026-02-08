package com.platform.common.exception;

import com.platform.common.enums.ErrorCode;
import lombok.Getter;

import java.util.Map;

/**
 * Exception for system invariant violations during versioned deployment operations.
 * Used when the system detects an inconsistent state that should never occur.
 * Maps to HTTP 500 Internal Server Error and should trigger critical alerts.
 * 
 * Examples:
 * - Multiple active versions detected for a function unit
 * - No active version found when one is expected
 * - Process instance bound to non-existent version
 */
@Getter
public class StateError extends PlatformException {
    
    private final String invariant;
    private final Object actualState;
    
    /**
     * Create a state error with invariant description and actual state
     * 
     * @param invariant Description of the invariant that was violated
     * @param actualState The actual state that violates the invariant
     */
    public StateError(String invariant, Object actualState) {
        super(ErrorCode.STATE_INVALID,
              String.format("System invariant violated: %s", invariant),
              Map.of("invariant", invariant, "actualState", actualState));
        this.invariant = invariant;
        this.actualState = actualState;
    }
    
    /**
     * Create a state error with invariant description, actual state, and custom message
     * 
     * @param invariant Description of the invariant that was violated
     * @param actualState The actual state that violates the invariant
     * @param message Custom error message with additional context
     */
    public StateError(String invariant, Object actualState, String message) {
        super(ErrorCode.STATE_INVALID,
              String.format("System invariant violated: %s - %s", invariant, message),
              Map.of("invariant", invariant, "actualState", actualState));
        this.invariant = invariant;
        this.actualState = actualState;
    }
    
    /**
     * Create a state error for single active version invariant violation
     * 
     * @param functionUnitName The function unit name
     * @param activeVersionCount The actual number of active versions found
     * @return StateError instance
     */
    public static StateError singleActiveVersionViolation(String functionUnitName, int activeVersionCount) {
        return new StateError(
            "Exactly one active version per function unit",
            Map.of("functionUnitName", functionUnitName, "activeVersionCount", activeVersionCount),
            String.format("Found %d active versions for function unit '%s', expected exactly 1", 
                         activeVersionCount, functionUnitName)
        );
    }
    
    /**
     * Create a state error for missing active version
     * 
     * @param functionUnitName The function unit name
     * @return StateError instance
     */
    public static StateError noActiveVersionFound(String functionUnitName) {
        return new StateError(
            "Active version must exist",
            Map.of("functionUnitName", functionUnitName),
            String.format("No active version found for function unit '%s'", functionUnitName)
        );
    }
    
    /**
     * Create a state error for process bound to non-existent version
     * 
     * @param processInstanceId The process instance ID
     * @param versionId The non-existent version ID
     * @return StateError instance
     */
    public static StateError processBindingInvalid(String processInstanceId, Long versionId) {
        return new StateError(
            "Process must be bound to existing version",
            Map.of("processInstanceId", processInstanceId, "versionId", versionId),
            String.format("Process instance '%s' is bound to non-existent version ID %d", 
                         processInstanceId, versionId)
        );
    }
    
    /**
     * Create a state error for no active version found
     * 
     * @param functionUnitName The function unit name
     * @return StateError instance
     */
    public static StateError noActiveVersion(String functionUnitName) {
        return new StateError(
            "Exactly one active version required",
            Map.of("functionUnitName", functionUnitName, "activeVersionCount", 0),
            String.format("No active version found for function unit '%s'", functionUnitName)
        );
    }
    
    /**
     * Create a state error for multiple active versions found
     * 
     * @param functionUnitName The function unit name
     * @param activeVersions List of active version numbers
     * @return StateError instance
     */
    public static StateError multipleActiveVersions(String functionUnitName, java.util.List<String> activeVersions) {
        return new StateError(
            "Exactly one active version required",
            Map.of("functionUnitName", functionUnitName, 
                   "activeVersionCount", activeVersions.size(),
                   "activeVersions", activeVersions),
            String.format("Multiple active versions found for function unit '%s': %s", 
                         functionUnitName, String.join(", ", activeVersions))
        );
    }
}
