package com.workflow.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 流程实例控制操作结果
 */
@Data
@Builder
public class ProcessInstanceControlResult {
    
    /**
     * 操作是否成功
     */
    private boolean success;
    
    /**
     * 流程实例ID
     */
    private String processInstanceId;
    
    /**
     * 执行的操作类型
     */
    private String action;
    
    /**
     * 操作时间
     */
    private LocalDateTime operationTime;
    
    /**
     * 操作用户ID
     */
    private String userId;
    
    /**
     * 操作结果消息
     */
    private String message;
    
    /**
     * 流程实例当前状态
     */
    private String currentState;
    
    /**
     * 创建成功结果
     */
    public static ProcessInstanceControlResult success(String processInstanceId, String action, 
                                                     String userId, String currentState) {
        return ProcessInstanceControlResult.builder()
                .success(true)
                .processInstanceId(processInstanceId)
                .action(action)
                .operationTime(LocalDateTime.now())
                .userId(userId)
                .currentState(currentState)
                .message("流程实例" + getActionName(action) + "成功")
                .build();
    }
    
    /**
     * 创建失败结果
     */
    public static ProcessInstanceControlResult failure(String processInstanceId, String action, 
                                                     String userId, String message) {
        return ProcessInstanceControlResult.builder()
                .success(false)
                .processInstanceId(processInstanceId)
                .action(action)
                .operationTime(LocalDateTime.now())
                .userId(userId)
                .message("流程实例" + getActionName(action) + "失败: " + message)
                .build();
    }
    
    private static String getActionName(String action) {
        return switch (action) {
            case "suspend" -> "暂停";
            case "activate" -> "恢复";
            case "terminate" -> "终止";
            default -> "操作";
        };
    }
}