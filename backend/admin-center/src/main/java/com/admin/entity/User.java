package com.admin.entity;

import com.admin.enums.UserStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 用户实体 - 使用统一的 sys_users 表
 */
@Entity
@Table(name = "sys_users")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class User {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;
    
    @JsonIgnore
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    
    @Column(name = "email")
    private String email;
    
    @Column(name = "display_name", length = 50)
    private String displayName;
    
    @Column(name = "full_name", length = 100)
    private String fullName;
    
    @Column(name = "employee_id", length = 50)
    private String employeeId;
    
    @Column(name = "business_unit_id", length = 64)
    private String businessUnitId;
    
    @Column(name = "position", length = 100)
    private String position;
    
    @Column(name = "entity_manager_id", length = 64)
    private String entityManagerId;
    
    @Column(name = "function_manager_id", length = 64)
    private String functionManagerId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;
    
    @Column(name = "language", length = 10)
    @Builder.Default
    private String language = "zh_CN";
    
    @Column(name = "must_change_password")
    @Builder.Default
    private Boolean mustChangePassword = false;
    
    @Column(name = "password_expired_at")
    private LocalDateTime passwordExpiredAt;
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
    @Column(name = "last_login_ip", length = 50)
    private String lastLoginIp;
    
    @Column(name = "failed_login_count")
    @Builder.Default
    private Integer failedLoginCount = 0;
    
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
    
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    private String createdBy;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @LastModifiedBy
    @Column(name = "updated_by", length = 64)
    private String updatedBy;
    
    @Column(name = "deleted")
    @Builder.Default
    private Boolean deleted = false;
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    @Column(name = "deleted_by", length = 64)
    private String deletedBy;
    
    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<UserRole> userRoles = new HashSet<>();
    
    /**
     * 获取ID的字符串形式（兼容旧代码）
     */
    public String getIdAsString() {
        return id != null ? id.toString() : null;
    }
    
    /**
     * 检查用户是否被锁定
     */
    public boolean isLocked() {
        return status == UserStatus.LOCKED || 
               (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now()));
    }
    
    /**
     * 检查用户是否活跃
     */
    public boolean isActive() {
        return status == UserStatus.ACTIVE && !isLocked();
    }
    
    /**
     * 增加登录失败次数
     */
    public void incrementFailedLoginCount() {
        this.failedLoginCount = (this.failedLoginCount == null ? 0 : this.failedLoginCount) + 1;
    }
    
    /**
     * 重置登录失败次数
     */
    public void resetFailedLoginCount() {
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }
}
