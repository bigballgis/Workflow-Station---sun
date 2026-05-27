package com.workflow.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Task completion result
 */
@Data
@Builder
public class TaskCompleteResult {
    
    private boolean success;
    
    private String message;
    
    private LocalDateTime completionTime;
    
    private String nextTaskId;
    
    private boolean processCompleted;
    
    public static TaskCompleteResult success() {
        return TaskCompleteResult.builder()
                .success(true)
                .message("Task completed successfully")
                .completionTime(LocalDateTime.now())
                .build();
    }
    
    public static TaskCompleteResult success(String nextTaskId, boolean processCompleted) {
        return TaskCompleteResult.builder()
                .success(true)
                .message("Task completed successfully")
                .completionTime(LocalDateTime.now())
                .nextTaskId(nextTaskId)
                .processCompleted(processCompleted)
                .build();
    }
    
    public static TaskCompleteResult failure(String message) {
        return TaskCompleteResult.builder()
                .success(false)
                .message(message)
                .completionTime(LocalDateTime.now())
                .build();
    }
}