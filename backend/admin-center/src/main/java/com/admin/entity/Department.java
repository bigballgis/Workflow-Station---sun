package com.admin.entity;

import com.admin.enums.DepartmentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 部门实体
 */
@Entity
@Table(name = "admin_departments")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Department {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;
    
    @Column(name = "parent_id", length = 64)
    private String parentId;
    
    @Column(name = "level", nullable = false)
    @Builder.Default
    private Integer level = 1;
    
    @Column(name = "path", length = 500)
    private String path;
    
    @Column(name = "manager_id", length = 64)
    private String managerId;
    
    @Column(name = "phone", length = 50)
    private String phone;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "cost_center", length = 50)
    private String costCenter;
    
    @Column(name = "location", length = 200)
    private String location;
    
    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DepartmentStatus status = DepartmentStatus.ACTIVE;
    
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    
    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    private String createdBy;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @LastModifiedBy
    @Column(name = "updated_by", length = 64)
    private String updatedBy;
    
    @Transient
    @Builder.Default
    private List<Department> children = new ArrayList<>();
    
    @Transient
    private Long memberCount;
    
    /**
     * 检查是否是根部门
     */
    public boolean isRoot() {
        return parentId == null || parentId.isEmpty();
    }
    
    /**
     * 检查是否是指定部门的祖先
     */
    public boolean isAncestorOf(Department other) {
        if (other == null || other.getPath() == null) return false;
        return other.getPath().startsWith(this.path + "/");
    }
    
    /**
     * 检查是否是指定部门的后代
     */
    public boolean isDescendantOf(Department other) {
        if (other == null || this.path == null) return false;
        return this.path.startsWith(other.getPath() + "/");
    }
}
