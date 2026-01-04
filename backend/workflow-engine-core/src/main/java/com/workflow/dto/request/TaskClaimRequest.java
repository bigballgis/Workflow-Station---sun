package com.workflow.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 任务认领请求DTO
 * 支持虚拟组和部门角色任务的认领
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskClaimRequest {

    /**
     * 任务ID
     */
    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    /**
     * 认领用户ID
     */
    @NotBlank(message = "认领用户ID不能为空")
    private String claimedBy;

    /**
     * 认领原因
     */
    private String claimReason;

    /**
     * 是否发送认领通知
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
               claimedBy != null && !claimedBy.trim().isEmpty();
    }

    /**
     * 获取验证错误信息
     */
    public String getValidationError() {
        if (taskId == null || taskId.trim().isEmpty()) {
            return "任务ID不能为空";
        }
        if (claimedBy == null || claimedBy.trim().isEmpty()) {
            return "认领用户ID不能为空";
        }
        return null;
    }

    /**
     * 获取认领原因（如果为空则返回默认原因）
     */
    public String getEffectiveClaimReason() {
        return claimReason != null && !claimReason.trim().isEmpty() 
               ? claimReason 
               : "任务认领";
    }
}