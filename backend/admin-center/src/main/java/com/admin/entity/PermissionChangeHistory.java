package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 权限变更历史实体
 */
@Entity
@Table(name = "admin_permission_change_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionChangeHistory {
    
    @Id
    @Column(length = 36)
    private String id;
    
    /**
     * 变更类型: ROLE_ASSIGNED, ROLE_REMOVED, PERMISSION_GRANTED, PERMISSION_REVOKED, DELEGATION_CREATED, DELEGATION_REVOKED
     */
    @Column(name = "change_type", nullable = false, length = 50)
    private String changeType;
    
    /**
     * 目标用户ID
     */
    @Column(name = "target_user_id", length = 36)
    private String targetUserId;
    
    /**
     * 目标角色ID
     */
    @Column(name = "target_role_id", length = 36)
    private String targetRoleId;
    
    /**
     * 目标权限ID
     */
    @Column(name = "target_permission_id", length = 36)
    private String targetPermissionId;
    
    /**
     * 变更前值
     */
    @Column(name = "old_value", length = 500)
    private String oldValue;
    
    /**
     * 变更后值
     */
    @Column(name = "new_value", length = 500)
    private String newValue;
    
    /**
     * 变更原因
     */
    @Column(name = "reason", length = 500)
    private String reason;
    
    /**
     * 操作人ID
     */
    @Column(name = "changed_by", nullable = false, length = 36)
    private String changedBy;
    
    /**
     * 变更时间
     */
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;
    
    /**
     * IP地址
     */
    @Column(name = "ip_address", length = 50)
    private String ipAddress;
    
    /**
     * 用户代理
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @PrePersist
    protected void onCreate() {
        if (changedAt == null) {
            changedAt = Instant.now();
        }
    }
}
