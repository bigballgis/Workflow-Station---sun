package com.workflow.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务委托请求DTO
 * 支持任何分配类型的任务委托给其他用户
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDelegationRequest {

    /**
     * 任务ID
     */
    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    /**
     * 委托给的用户ID
     */
    @NotBlank(message = "委托目标用户ID不能为空")
    private String delegatedTo;

    /**
     * 委托人ID（发起委托的用户）
     */
    @NotBlank(message = "委托人ID不能为空")
    private String delegatedBy;

    /**
     * 委托原因
     */
    private String delegationReason;

    /**
     * 委托到期时间（可选）
     */
    private LocalDateTime delegationExpiry;

    /**
     * 是否保留原分配人的处理权限
     */
    @Builder.Default
    private Boolean retainOriginalPermission = false;

    /**
     * 是否发送委托通知
     */
    @Builder.Default
    private Boolean sendNotification = true;

    /**
     * 通知消息模板
     */
    private String notificationTemplate;

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
     * 检查委托是否有到期时间
     */
    public boolean hasDelegationExpiry() {
        return delegationExpiry != null;
    }

    /**
     * 检查委托是否已过期
     */
    public boolean isDelegationExpired() {
        return hasDelegationExpiry() && LocalDateTime.now().isAfter(delegationExpiry);
    }

    /**
     * 检查是否需要发送通知
     */
    public boolean shouldSendNotification() {
        return sendNotification != null && sendNotification;
    }

    /**
     * 检查是否保留原分配人权限
     */
    public boolean shouldRetainOriginalPermission() {
        return retainOriginalPermission != null && retainOriginalPermission;
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
               delegatedTo != null && !delegatedTo.trim().isEmpty() &&
               delegatedBy != null && !delegatedBy.trim().isEmpty() &&
               !delegatedTo.equals(delegatedBy); // 不能委托给自己
    }

    /**
     * 获取验证错误信息
     */
    public String getValidationError() {
        if (taskId == null || taskId.trim().isEmpty()) {
            return "任务ID不能为空";
        }
        if (delegatedTo == null || delegatedTo.trim().isEmpty()) {
            return "委托目标用户ID不能为空";
        }
        if (delegatedBy == null || delegatedBy.trim().isEmpty()) {
            return "委托人ID不能为空";
        }
        if (delegatedTo.equals(delegatedBy)) {
            return "不能委托给自己";
        }
        return null;
    }

    /**
     * 获取委托原因（如果为空则返回默认原因）
     */
    public String getEffectiveDelegationReason() {
        return delegationReason != null && !delegationReason.trim().isEmpty() 
               ? delegationReason 
               : "任务委托";
    }
}