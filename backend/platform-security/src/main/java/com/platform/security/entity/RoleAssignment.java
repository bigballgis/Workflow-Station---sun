package com.platform.security.entity;

import com.platform.security.enums.AssignmentTargetType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 角色分配实体
 * 支持将角色分配给用户、部门、部门层级或虚拟组
 */
@Entity
@Table(name = "sys_role_assignments", 
       uniqueConstraints = @UniqueConstraint(
           name = "uk_role_target", 
           columnNames = {"role_id", "target_type", "target_id"}
       ))
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class RoleAssignment {
    
    @Id
    @Column(length = 64)
    private String id;
    
    /**
     * 角色ID
     */
    @Column(name = "role_id", nullable = false, length = 64)
    private String roleId;
    
    /**
     * 分配目标类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private AssignmentTargetType targetType;
    
    /**
     * 分配目标ID（用户ID/部门ID/虚拟组ID）
     */
    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;
    
    /**
     * 分配时间
     */
    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;
    
    /**
     * 分配人ID
     */
    @Column(name = "assigned_by", length = 64)
    private String assignedBy;
    
    /**
     * 有效期开始时间
     */
    @Column(name = "valid_from")
    private LocalDateTime validFrom;
    
    /**
     * 有效期结束时间
     */
    @Column(name = "valid_to")
    private LocalDateTime validTo;
    
    /**
     * 创建时间
     */
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * 创建人
     */
    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    private String createdBy;
    
    /**
     * 更新时间
     */
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    /**
     * 检查分配是否在有效期内
     */
    public boolean isValid() {
        LocalDateTime now = LocalDateTime.now();
        boolean afterValidFrom = validFrom == null || !now.isBefore(validFrom);
        boolean beforeValidTo = validTo == null || !now.isAfter(validTo);
        return afterValidFrom && beforeValidTo;
    }
}
