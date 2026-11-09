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
    
    /** No default: a role is created with an explicit type (platform access model §5.3). */
    @Column(length = 20, nullable = false)
    private String type;
    
    @Column(name = "display_name", columnDefinition = "TEXT")
    private String displayName;
    
    @Column(length = 20)
    @Builder.Default
    private String status = "ACTIVE";
    
    @Column(name = "is_system")
    @Builder.Default
    private Boolean isSystem = false;
    
    @Transient
    private Set<String> permissionCodes;
    
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
     * Version field for optimistic locking.
     * JPA will automatically increment this on each update and check for concurrent modifications.
     */
    @jakarta.persistence.Version
    @Column(name = "lock_version")
    private Long lockVersion;
}
