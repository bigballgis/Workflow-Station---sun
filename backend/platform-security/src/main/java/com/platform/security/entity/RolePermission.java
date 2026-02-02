package com.platform.security.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Role Permission association entity.
 * Binds permissions to roles with optional conditions.
 */
@Entity
@Table(name = "sys_role_permissions",
       uniqueConstraints = @UniqueConstraint(name = "uk_role_permission", columnNames = {"role_id", "permission_id"}),
       indexes = {
           @Index(name = "idx_role_perm_role", columnList = "role_id"),
           @Index(name = "idx_role_perm_perm", columnList = "permission_id")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class RolePermission {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(name = "role_id", nullable = false, length = 64)
    private String roleId;
    
    @Column(name = "permission_id", nullable = false, length = 64)
    private String permissionId;
    
    @Column(name = "condition_type", length = 50)
    private String conditionType;
    
    @Column(name = "condition_value", columnDefinition = "JSONB")
    private String conditionValue;
    
    @CreationTimestamp
    @Column(name = "granted_at")
    private LocalDateTime grantedAt;
    
    @Column(name = "granted_by", length = 64)
    private String grantedBy;
    
    /**
     * Check if permission has conditional restrictions
     */
    public boolean hasCondition() {
        return conditionType != null && !conditionType.isEmpty();
    }
}
