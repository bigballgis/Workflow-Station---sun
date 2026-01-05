package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 用户角色关联实体
 */
@Entity
@Table(name = "admin_user_roles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class UserRole {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;
    
    @Column(name = "assigned_at")
    private Instant assignedAt;
    
    @Column(name = "assigned_by", length = 64)
    private String assignedBy;
    
    @Column(name = "valid_from")
    private Instant validFrom;
    
    @Column(name = "valid_to")
    private Instant validTo;
    
    /**
     * 检查角色分配是否有效
     */
    public boolean isValid() {
        Instant now = Instant.now();
        boolean afterValidFrom = validFrom == null || !now.isBefore(validFrom);
        boolean beforeValidTo = validTo == null || !now.isAfter(validTo);
        return afterValidFrom && beforeValidTo;
    }
}
