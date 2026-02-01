package com.admin.entity;

import com.admin.enums.RoleType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * 角色实体
 */
@Entity
@Table(name = "sys_roles")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Role {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private RoleType type;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";
    
    /** 是否为系统角色（系统角色不可删除） */
    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private Boolean isSystem = false;
    
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
    
    @JsonIgnore
    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<RolePermission> rolePermissions = new HashSet<>();
    
    @JsonIgnore
    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<UserRole> userRoles = new HashSet<>();
    
    /**
     * 检查是否是系统角色
     */
    public boolean isSystemRole() {
        return Boolean.TRUE.equals(isSystem);
    }
    
    /**
     * 检查是否是业务角色（BU_BOUNDED 或 BU_UNBOUNDED）
     */
    public boolean isBusinessRole() {
        return type != null && type.isBusinessRole();
    }
    
    /**
     * 检查是否是管理角色
     */
    public boolean isAdminRole() {
        return type == RoleType.ADMIN;
    }
    
    /**
     * 检查是否是开发角色
     */
    public boolean isDeveloperRole() {
        return type == RoleType.DEVELOPER;
    }
}
