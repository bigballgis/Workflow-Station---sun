package com.platform.security.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Role entity representing a user role with associated permissions.
 * Maps to sys_roles table.
 */
@Entity
@Table(name = "sys_roles", indexes = {
    @Index(name = "idx_sys_roles_code", columnList = "code"),
    @Index(name = "idx_sys_roles_type", columnList = "type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(nullable = false, unique = true, length = 50)
    private String code;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(length = 20, nullable = false)
    @Builder.Default
    private String type = "BU_UNBOUNDED";
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(length = 20)
    @Builder.Default
    private String status = "ACTIVE";
    
    @Column(name = "is_system")
    @Builder.Default
    private Boolean isSystem = false;
    
    @Transient
    private Set<String> permissionCodes;
    
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;
    
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
}
