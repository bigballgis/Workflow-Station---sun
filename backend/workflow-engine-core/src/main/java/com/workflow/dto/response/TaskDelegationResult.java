package com.workflow.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务委托结果
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
                .message("任务委托成功")
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