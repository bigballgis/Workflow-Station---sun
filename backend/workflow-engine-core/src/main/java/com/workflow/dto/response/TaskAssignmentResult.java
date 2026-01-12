package com.workflow.dto.response;

import com.workflow.enums.AssignmentType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务分配结果
 */
@Data
@Builder
public class TaskAssignmentResult {
    
    /**
     * 任务ID
     */
    private String taskId;
    
    /**
     * 分配类型
     */
    private AssignmentType assignmentType;
    
    /**
     * 分配目标
     */
    private String assignmentTarget;
    
    /**
     * 操作用户ID
     */
    private String operatorUserId;
    
    /**
     * 分配时间
     */
    @Builder.Default
    private LocalDateTime assignmentTime = LocalDateTime.now();
    
    /**
     * 是否成功
     */
    private Boolean success;
    
    /**
     * 检查是否成功
     */
    public boolean isSuccess() {
        return Boolean.TRUE.equals(success);
    }
    
    /**
     * 消息
     */
    private String message;
    
    /**
     * 错误代码
     */
    private String errorCode;
    
    // ==================== 静态工厂方法 ====================
    
    /**
     * 创建成功结果
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
     * 创建失败结果
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
     * 创建失败结果（带错误代码）
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
     * 获取分配类型描述
     */
    public String getAssignmentTypeDescription() {
        return assignmentType != null ? assignmentType.getDescription() : "未知";
    }
}