package com.platform.security.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * User Role association entity.
 * Direct user-role assignments (not recommended, use Virtual Groups instead).
 * 
 * Recommended Architecture: User → Virtual Group → Role
 */
@Entity
@Table(name = "sys_user_roles",
       uniqueConstraints = @UniqueConstraint(name = "uk_user_role", columnNames = {"user_id", "role_id"}),
       indexes = {
           @Index(name = "idx_user_role_user", columnList = "user_id"),
           @Index(name = "idx_user_role_role", columnList = "role_id")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class UserRole {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;
    
    @Column(name = "role_id", nullable = false, length = 64)
    private String roleId;
    
    @CreationTimestamp
    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;
    
    @Column(name = "assigned_by", length = 64)
    private String assignedBy;
    
    @Column(name = "valid_from")
    private LocalDateTime validFrom;
    
    @Column(name = "valid_to")
    private LocalDateTime validTo;
    
    /**
     * Check if role assignment is currently valid
     */
    public boolean isValid() {
        LocalDateTime now = LocalDateTime.now();
        boolean afterValidFrom = validFrom == null || !now.isBefore(validFrom);
        boolean beforeValidTo = validTo == null || !now.isAfter(validTo);
        return afterValidFrom && beforeValidTo;
    }
}
