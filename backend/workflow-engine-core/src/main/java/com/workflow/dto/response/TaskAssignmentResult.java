package com.workflow.dto.response;

import com.workflow.enums.AssignmentType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Task assignment result
 */
@Data
@Builder
public class TaskAssignmentResult {
    
    /**
     * Task ID
     */
    private String taskId;
    
    /**
     * Assignment type
     */
    private AssignmentType assignmentType;
    
    /**
     * Assignment target
     */
    private String assignmentTarget;
    
    /**
     * Operator user ID
     */
    private String operatorUserId;
    
    /**
     * Assignment time
     */
    @Builder.Default
    private LocalDateTime assignmentTime = LocalDateTime.now();
    
    /**
     * Whether successful
     */
    private Boolean success;
    
    /**
     * Check whether successful
     */
    public boolean isSuccess() {
        return Boolean.TRUE.equals(success);
    }
    
    /**
     * Message
     */
    private String message;
    
    /**
     * Error code
     */
    private String errorCode;
    
    // ==================== Static factory methods ====================
    
    /**
     * Create success result
     */
    public static TaskAssignmentResult success(String taskId, AssignmentType assignmentType, 
                                             String assignmentTarget, String operatorUserId, 
                                             String message) {
        return TaskAssignmentResult.builder()
            .taskId(taskId)
            .assignmentType(assignmentType)
            .assignmentTarget(assignmentTarget)
            .operatorUserId(operatorUserId)
            .success(true)
            .message(message)
            .build();
    }
    
    /**
     * Create failure result
     */
    public static TaskAssignmentResult failure(String taskId, AssignmentType assignmentType, 
                                             String assignmentTarget, String operatorUserId, 
                                             String message) {
        return TaskAssignmentResult.builder()
            .taskId(taskId)
            .assignmentType(assignmentType)
            .assignmentTarget(assignmentTarget)
            .operatorUserId(operatorUserId)
            .success(false)
            .message(message)
            .build();
    }
    
    /**
     * Create failure result (with error code)
     */
    public static TaskAssignmentResult failure(String taskId, AssignmentType assignmentType, 
                                             String assignmentTarget, String operatorUserId, 
                                             String message, String errorCode) {
        return TaskAssignmentResult.builder()
            .taskId(taskId)
            .assignmentType(assignmentType)
            .assignmentTarget(assignmentTarget)
            .operatorUserId(operatorUserId)
            .success(false)
            .message(message)
            .errorCode(errorCode)
            .build();
    }
    
    /**
     * Get assignment type description
     */
    public String getAssignmentTypeDescription() {
        return assignmentType != null ? assignmentType.getDescription() : "Unknown";
    }
}