package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * 异步操作结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncOperationResult<T> {
    
    /**
     * 操作ID
     */
    private String operationId;
    
    /**
     * 操作状态
     */
    private OperationStatus status;
    
    /**
     * 操作结果
     */
    private T result;
    
    /**
     * 错误消息
     */
    private String errorMessage;
    
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 完成时间
     */
    private LocalDateTime completionTime;
    
    /**
     * 执行时间（毫秒）
     */
    private Long executionTimeMs;
    
    /**
     * 操作状态枚举
     */
    public enum OperationStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
    
    /**
     * 创建成功结果
     */
    public static <T> AsyncOperationResult<T> success(String operationId, T result, long executionTimeMs) {
        return AsyncOperationResult.<T>builder()
                .operationId(operationId)
                .status(OperationStatus.COMPLETED)
                .result(result)
                .completionTime(LocalDateTime.now())
                .executionTimeMs(executionTimeMs)
                .build();
    }
    
    /**
     * 创建失败结果
     */
    public static <T> AsyncOperationResult<T> failure(String operationId, String errorMessage) {
        return AsyncOperationResult.<T>builder()
                .operationId(operationId)
                .status(OperationStatus.FAILED)
                .errorMessage(errorMessage)
                .completionTime(LocalDateTime.now())
                .build();
    }
    
    /**
     * 创建待处理结果
     */
    public static <T> AsyncOperationResult<T> pending(String operationId) {
        return AsyncOperationResult.<T>builder()
                .operationId(operationId)
                .status(OperationStatus.PENDING)
                .startTime(LocalDateTime.now())
                .build();
    }
}
