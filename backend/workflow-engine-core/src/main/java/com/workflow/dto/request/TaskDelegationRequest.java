package com.workflow.dto.request;

import com.workflow.enums.DelegatedTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Single-task delegate request. Target is a user or a paired BU+Role.
 * Does not reassign Flowable assignee.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDelegationRequest {

    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    /**
     * USER target. Required when type is USER (default).
     */
    private String delegatedTo;

    /**
     * USER (default) or BU_ROLE.
     */
    private DelegatedTargetType delegatedTargetType;

    private String delegatedBuCode;

    private String delegatedRoleCode;

    @NotBlank(message = "委托人ID不能为空")
    private String delegatedBy;

    private String delegationReason;

    private LocalDateTime delegationExpiry;

    @Builder.Default
    private Boolean retainOriginalPermission = false;

    @Builder.Default
    private Boolean sendNotification = true;

    private String notificationTemplate;

    private Map<String, Object> extendedProperties;

    private String tenantId;

    public DelegatedTargetType effectiveTargetType() {
        return delegatedTargetType != null ? delegatedTargetType : DelegatedTargetType.USER;
    }

    public boolean isBuRoleTarget() {
        return effectiveTargetType() == DelegatedTargetType.BU_ROLE;
    }

    public boolean hasDelegationExpiry() {
        return delegationExpiry != null;
    }

    public boolean isDelegationExpired() {
        return hasDelegationExpiry() && LocalDateTime.now().isAfter(delegationExpiry);
    }

    public boolean shouldSendNotification() {
        return sendNotification != null && sendNotification;
    }

    public boolean shouldRetainOriginalPermission() {
        return retainOriginalPermission != null && retainOriginalPermission;
    }

    public Object getExtendedProperty(String key) {
        return extendedProperties != null ? extendedProperties.get(key) : null;
    }

    public void setExtendedProperty(String key, Object value) {
        if (extendedProperties == null) {
            extendedProperties = new java.util.HashMap<>();
        }
        extendedProperties.put(key, value);
    }

    public boolean isValid() {
        return getValidationError() == null;
    }

    public String getValidationError() {
        if (taskId == null || taskId.trim().isEmpty()) {
            return "任务ID不能为空";
        }
        if (delegatedBy == null || delegatedBy.trim().isEmpty()) {
            return "委托人ID不能为空";
        }
        if (isBuRoleTarget()) {
            boolean buBlank = delegatedBuCode == null || delegatedBuCode.isBlank();
            boolean roleBlank = delegatedRoleCode == null || delegatedRoleCode.isBlank();
            if (buBlank || roleBlank) {
                return "委托给 BU 和 Role 时必须成对填写";
            }
            return null;
        }
        if (delegatedTo == null || delegatedTo.trim().isEmpty()) {
            return "委托目标用户ID不能为空";
        }
        if (delegatedTo.equals(delegatedBy)) {
            return "不能委托给自己";
        }
        return null;
    }

    public String getEffectiveDelegationReason() {
        return delegationReason != null && !delegationReason.trim().isEmpty()
               ? delegationReason
               : "任务委托";
    }
}
