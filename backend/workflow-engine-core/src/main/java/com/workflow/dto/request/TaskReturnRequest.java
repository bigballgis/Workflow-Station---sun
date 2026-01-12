package com.workflow.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 任务回退请求DTO
 * 支持将任务回退到指定的历史节点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskReturnRequest {

    /**
     * 任务ID
     */
    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    /**
     * 目标活动节点ID（回退到的节点）
     */
    @NotBlank(message = "目标节点ID不能为空")
    private String targetActivityId;

    /**
     * 操作用户ID
     */
    @NotBlank(message = "用户ID不能为空")
    private String userId;

    /**
     * 回退原因
     */
    private String reason;

    /**
     * 是否发送回退通知
     */
    @Builder.Default
    private Boolean sendNotification = true;

    /**
     * 通知消息模板
     */
    private String notificationTemplate;

    /**
     * 回退时携带的流程变量
     */
    private Map<String, Object> variables;

    /**
     * 扩展属性
     */
    private Map<String, Object> extendedProperties;

    /**
     * 租户ID
     */
    private String tenantId;

    // ==================== 业务方法 ====================

    /**
     * 检查是否需要发送通知
     */
    public boolean shouldSendNotification() {
        return sendNotification != null && sendNotification;
    }

    /**
     * 获取扩展属性值
     */
    public Object getExtendedProperty(String key) {
        return extendedProperties != null ? extendedProperties.get(key) : null;
    }

    /**
     * 设置扩展属性值
     */
    public void setExtendedProperty(String key, Object value) {
        if (extendedProperties == null) {
            extendedProperties = new java.util.HashMap<>();
        }
        extendedProperties.put(key, value);
    }

    /**
     * 验证请求的完整性
     */
    public boolean isValid() {
        return taskId != null && !taskId.trim().isEmpty() &&
               targetActivityId != null && !targetActivityId.trim().isEmpty() &&
               userId != null && !userId.trim().isEmpty();
    }

    /**
     * 获取验证错误信息
     */
    public String getValidationError() {
        if (taskId == null || taskId.trim().isEmpty()) {
            return "任务ID不能为空";
        }
        if (targetActivityId == null || targetActivityId.trim().isEmpty()) {
            return "目标节点ID不能为空";
        }
        if (userId == null || userId.trim().isEmpty()) {
            return "用户ID不能为空";
        }
        return null;
    }

    /**
     * 获取回退原因（如果为空则返回默认原因）
     */
    public String getEffectiveReason() {
        return reason != null && !reason.trim().isEmpty() 
               ? reason 
               : "任务回退";
    }
}
