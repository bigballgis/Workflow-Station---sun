package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 角色权限关联实体
 */
@Entity
@Table(name = "sys_role_permissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class RolePermission {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;
    
    @Column(name = "condition_type", length = 50)
    private String conditionType;
    
    @Column(name = "condition_value", columnDefinition = "JSONB")
    private String conditionValue;
    
    @Column(name = "granted_at")
    private Instant grantedAt;
    
    @Column(name = "granted_by", length = 64)
    private String grantedBy;
    
    /**
     * 检查是否有条件限制
     */
    public boolean hasCondition() {
        return conditionType != null && !conditionType.isEmpty();
    }
}
