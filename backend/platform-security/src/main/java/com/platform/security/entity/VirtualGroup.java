package com.platform.security.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Virtual Group entity for grouping users and assigning roles.
 * Supports project groups, work groups, temporary groups, and task processing groups.
 * Virtual groups are shared across services and users can obtain role permissions through virtual groups.
 * 
 * Architecture: User → Virtual Group → Role
 */
@Entity
@Table(name = "sys_virtual_groups", indexes = {
    @Index(name = "idx_virtual_group_code", columnList = "code"),
    @Index(name = "idx_virtual_group_type", columnList = "type"),
    @Index(name = "idx_virtual_group_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class VirtualGroup {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(nullable = false, unique = true, length = 50)
    private String code;
    
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String type = "STANDARD";
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    /**
     * AD Group name for Active Directory integration
     */
    @Column(name = "ad_group", length = 100)
    private String adGroup;
    
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "created_by", length = 64)
    private String createdBy;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "updated_by", length = 64)
    private String updatedBy;
    
    /**
     * Check if virtual group is active
     */
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
    
    /**
     * Check if virtual group is a system group
     */
    public boolean isSystem() {
        return "SYSTEM".equals(type);
    }
}
