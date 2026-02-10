package com.platform.common.exception;

import com.platform.common.enums.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for StateError exception class.
 */
@DisplayName("StateError")
class StateErrorTest {
    
    @Test
    @DisplayName("should create state error with invariant and actual state")
    void shouldCreateWithInvariantAndActualState() {
        Map<String, Object> actualState = Map.of("activeCount", 2);
        StateError error = new StateError("Exactly one active version", actualState);
        
        assertThat(error.getInvariant()).isEqualTo("Exactly one active version");
        assertThat(error.getActualState()).isEqualTo(actualState);
        assertThat(error.getMessage()).contains("System invariant violated");
        assertThat(error.getMessage()).contains("Exactly one active version");
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.STATE_INVALID);
        assertThat(error.getDetails()).containsEntry("invariant", "Exactly one active version");
        assertThat(error.getDetails()).containsEntry("actualState", actualState);
    }
    
    @Test
    @DisplayName("should create state error with invariant, actual state, and custom message")
    void shouldCreateWithInvariantActualStateAndMessage() {
        Map<String, Object> actualState = Map.of("versionId", 123L);
        StateError error = new StateError(
            "Process must be bound to existing version",
            actualState,
            "Version not found in database"
        );
        
        assertThat(error.getInvariant()).isEqualTo("Process must be bound to existing version");
        assertThat(error.getActualState()).isEqualTo(actualState);
        assertThat(error.getMessage()).contains("System invariant violated");
        assertThat(error.getMessage()).contains("Process must be bound to existing version");
        assertThat(error.getMessage()).contains("Version not found in database");
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.STATE_INVALID);
    }
    
    @Test
    @DisplayName("should create single active version violation error")
    void shouldCreateSingleActiveVersionViolation() {
        StateError error = StateError.singleActiveVersionViolation("digital-lending", 2);
        
        assertThat(error.getInvariant()).isEqualTo("Exactly one active version per function unit");
        assertThat(error.getMessage()).contains("Found 2 active versions");
        assertThat(error.getMessage()).contains("digital-lending");
        assertThat(error.getMessage()).contains("expected exactly 1");
        
        Map<String, Object> actualState = (Map<String, Object>) error.getActualState();
        assertThat(actualState).containsEntry("functionUnitName", "digital-lending");
        assertThat(actualState).containsEntry("activeVersionCount", 2);
    }
    
    @Test
    @DisplayName("should create no active version found error")
    void shouldCreateNoActiveVersionFound() {
        StateError error = StateError.noActiveVersionFound("loan-approval");
        
        assertThat(error.getInvariant()).isEqualTo("Active version must exist");
        assertThat(error.getMessage()).contains("No active version found");
        assertThat(error.getMessage()).contains("loan-approval");
        
        Map<String, Object> actualState = (Map<String, Object>) error.getActualState();
        assertThat(actualState).containsEntry("functionUnitName", "loan-approval");
    }
    
    @Test
    @DisplayName("should create process binding invalid error")
    void shouldCreateProcessBindingInvalid() {
        StateError error = StateError.processBindingInvalid("process-123", 456L);
        
        assertThat(error.getInvariant()).isEqualTo("Process must be bound to existing version");
        assertThat(error.getMessage()).contains("Process instance 'process-123'");
        assertThat(error.getMessage()).contains("non-existent version ID 456");
        
        Map<String, Object> actualState = (Map<String, Object>) error.getActualState();
        assertThat(actualState).containsEntry("processInstanceId", "process-123");
        assertThat(actualState).containsEntry("versionId", 456L);
    }
    
    @Test
    @DisplayName("should be instance of PlatformException")
    void shouldBeInstanceOfPlatformException() {
        StateError error = new StateError("Test invariant", Map.of());
        
        assertThat(error).isInstanceOf(PlatformException.class);
        assertThat(error).isInstanceOf(RuntimeException.class);
    }
    
    @Test
    @DisplayName("should include details in exception")
    void shouldIncludeDetailsInException() {
        Map<String, Object> actualState = Map.of(
            "functionUnitName", "test-unit",
            "activeVersionCount", 0
        );
        StateError error = new StateError("Active version required", actualState);
        
        assertThat(error.getDetails()).isNotNull();
        assertThat(error.getDetails()).containsKey("invariant");
        assertThat(error.getDetails()).containsKey("actualState");
        assertThat(error.getDetails().get("actualState")).isEqualTo(actualState);
    }
    
    @Test
    @DisplayName("should handle different actual state types")
    void shouldHandleDifferentActualStateTypes() {
        // Test with Map
        StateError error1 = new StateError("Test", Map.of("key", "value"));
        assertThat(error1.getActualState()).isInstanceOf(Map.class);
        
        // Test with String
        StateError error2 = new StateError("Test", "string state");
        assertThat(error2.getActualState()).isInstanceOf(String.class);
        
        // Test with Integer
        StateError error3 = new StateError("Test", 42);
        assertThat(error3.getActualState()).isInstanceOf(Integer.class);
    }
    
    @Test
    @DisplayName("should format message consistently for factory methods")
    void shouldFormatMessageConsistentlyForFactoryMethods() {
        StateError error1 = StateError.singleActiveVersionViolation("unit1", 3);
        StateError error2 = StateError.noActiveVersionFound("unit2");
        StateError error3 = StateError.processBindingInvalid("proc1", 789L);
        
        // All should start with "System invariant violated"
        assertThat(error1.getMessage()).startsWith("System invariant violated");
        assertThat(error2.getMessage()).startsWith("System invariant violated");
        assertThat(error3.getMessage()).startsWith("System invariant violated");
        
        // All should have ErrorCode.STATE_INVALID
        assertThat(error1.getErrorCode()).isEqualTo(ErrorCode.STATE_INVALID);
        assertThat(error2.getErrorCode()).isEqualTo(ErrorCode.STATE_INVALID);
        assertThat(error3.getErrorCode()).isEqualTo(ErrorCode.STATE_INVALID);
    }
}
