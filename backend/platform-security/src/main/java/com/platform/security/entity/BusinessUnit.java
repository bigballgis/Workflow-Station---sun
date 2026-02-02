package com.platform.security.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Business Unit entity (formerly Department).
 * Represents each level of the organizational structure.
 */
@Entity
@Table(name = "sys_business_units", indexes = {
    @Index(name = "idx_bu_code", columnList = "code"),
    @Index(name = "idx_bu_parent", columnList = "parent_id"),
    @Index(name = "idx_bu_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class BusinessUnit {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(nullable = false, unique = true, length = 50)
    private String code;
    
    @Column(name = "parent_id", length = 64)
    private String parentId;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer level = 1;
    
    @Column(length = 500)
    private String path;
    
    @Column(length = 50)
    private String phone;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "cost_center", length = 50)
    private String costCenter;
    
    @Column(length = 200)
    private String location;
    
    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;
    
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
     * Check if this is a root business unit
     */
    public boolean isRoot() {
        return parentId == null || parentId.isEmpty();
    }
    
    /**
     * Check if this is an ancestor of another business unit
     */
    public boolean isAncestorOf(BusinessUnit other) {
        if (other == null || other.getPath() == null) return false;
        return other.getPath().startsWith(this.path + "/");
    }
    
    /**
     * Check if this is a descendant of another business unit
     */
    public boolean isDescendantOf(BusinessUnit other) {
        if (other == null || this.path == null) return false;
        return this.path.startsWith(other.getPath() + "/");
    }
}
