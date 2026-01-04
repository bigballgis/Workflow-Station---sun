package com.workflow.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务完成结果
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
                .message("任务完成成功")
                .completionTime(LocalDateTime.now())
                .build();
    }
    
    public static TaskCompleteResult success(String nextTaskId, boolean processCompleted) {
        return TaskCompleteResult.builder()
                .success(true)
                .message("任务完成成功")
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