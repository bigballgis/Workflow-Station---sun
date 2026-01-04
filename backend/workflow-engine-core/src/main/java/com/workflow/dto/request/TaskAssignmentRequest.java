package com.workflow.dto.request;

import com.workflow.enums.AssignmentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务分配请求DTO
 * 支持多维度任务分配：用户、虚拟组、部门角色
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskAssignmentRequest {

    /**
     * 任务ID
     */
    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    /**
     * 分配类型
     */
    @NotNull(message = "分配类型不能为空")
    private AssignmentType assignmentType;

    /**
     * 分配目标
     * - 当assignmentType为USER时，存储用户ID
     * - 当assignmentType为VIRTUAL_GROUP时，存储虚拟组ID
     * - 当assignmentType为DEPT_ROLE时，存储"部门ID:角色ID"格式
     */
    @NotBlank(message = "分配目标不能为空")
    private String assignmentTarget;

    /**
     * 操作用户ID
     */
    @NotBlank(message = "操作用户ID不能为空")
    private String operatorUserId;

    /**
     * 任务优先级（0-100）
     */
    @Min(value = 0, message = "优先级不能小于0")
    @Max(value = 100, message = "优先级不能大于100")
    private Integer priority;

    /**
     * 任务到期时间
     */
    private LocalDateTime dueDate;

    /**
     * 分配原因
     */
    private String assignmentReason;

    /**
     * 扩展属性
     */
    private Map<String, Object> extendedProperties;

    /**
     * 是否发送通知
     */
    @Builder.Default
    private Boolean sendNotification = true;

    /**
     * 通知消息模板
     */
    private String notificationTemplate;

    /**
     * 租户ID
     */
    private String tenantId;

    // ==================== 业务方法 ====================

    /**
     * 验证分配目标格式
     */
    public boolean isValidAssignmentTarget() {
        if (assignmentTarget == null || assignmentTarget.trim().isEmpty()) {
            return false;
        }

        switch (assignmentType) {
            case USER:
                // 用户ID格式验证（简单的非空验证）
                return !assignmentTarget.trim().isEmpty();
            
            case VIRTUAL_GROUP:
                // 虚拟组ID格式验证（简单的非空验证）
                return !assignmentTarget.trim().isEmpty();
            
            case DEPT_ROLE:
                // 部门角色格式验证：部门ID:角色ID
                return assignmentTarget.contains(":") && 
                       assignmentTarget.split(":").length == 2 &&
                       !assignmentTarget.split(":")[0].trim().isEmpty() &&
                       !assignmentTarget.split(":")[1].trim().isEmpty();
            
            default:
                return false;
        }
    }

    /**
     * 获取部门ID（仅当分配类型为DEPT_ROLE时有效）
     */
    public String getDepartmentId() {
        if (assignmentType == AssignmentType.DEPT_ROLE && isValidAssignmentTarget()) {
            return assignmentTarget.split(":")[0].trim();
        }
        return null;
    }

    /**
     * 获取角色ID（仅当分配类型为DEPT_ROLE时有效）
     */
    public String getRoleId() {
        if (assignmentType == AssignmentType.DEPT_ROLE && isValidAssignmentTarget()) {
            return assignmentTarget.split(":")[1].trim();
        }
        return null;
    }

    /**
     * 获取分配类型描述
     */
    public String getAssignmentTypeDescription() {
        return assignmentType != null ? assignmentType.getDescription() : "未知";
    }

    /**
     * 检查是否需要发送通知
     */
    public boolean shouldSendNotification() {
        return sendNotification != null && sendNotification;
    }

    /**
     * 获取默认优先级
     */
    public int getEffectivePriority() {
        return priority != null ? priority : 50; // 默认优先级为50
    }

    /**
     * 检查任务是否有到期时间
     */
    public boolean hasDueDate() {
        return dueDate != null;
    }

    /**
     * 检查任务是否已过期
     */
    public boolean isOverdue() {
        return hasDueDate() && LocalDateTime.now().isAfter(dueDate);
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
               assignmentType != null &&
               isValidAssignmentTarget() &&
               operatorUserId != null && !operatorUserId.trim().isEmpty() &&
               (priority == null || (priority >= 0 && priority <= 100));
    }

    /**
     * 获取验证错误信息
     */
    public String getValidationError() {
        if (taskId == null || taskId.trim().isEmpty()) {
            return "任务ID不能为空";
        }
        if (assignmentType == null) {
            return "分配类型不能为空";
        }
        if (!isValidAssignmentTarget()) {
            return "分配目标格式不正确";
        }
        if (operatorUserId == null || operatorUserId.trim().isEmpty()) {
            return "操作用户ID不能为空";
        }
        if (priority != null && (priority < 0 || priority > 100)) {
            return "优先级必须在0-100之间";
        }
        return null;
    }
}