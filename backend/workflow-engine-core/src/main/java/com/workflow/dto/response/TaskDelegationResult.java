package com.workflow.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Task delegation result
 */
@Data
@Builder
public class TaskDelegationResult {
    
    private boolean success;
    
    private String message;
    
    private LocalDateTime delegationTime;
    
    public static TaskDelegationResult success() {
        return TaskDelegationResult.builder()
                .success(true)
                .message("Task delegated successfully")
                .delegationTime(LocalDateTime.now())
                .build();
    }
    
    public static TaskDelegationResult failure(String message) {
        return TaskDelegationResult.builder()
                .success(false)
                .message(message)
                .delegationTime(LocalDateTime.now())
                .build();
    }
}