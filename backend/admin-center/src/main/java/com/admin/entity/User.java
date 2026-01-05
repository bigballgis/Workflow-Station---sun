package com.admin.entity;

import com.admin.enums.UserStatus;
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
 * 用户实体
 */
@Entity
@Table(name = "admin_users")
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
    
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    
    @Column(name = "email", nullable = false)
    private String email;
    
    @Column(name = "phone", length = 50)
    private String phone;
    
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;
    
    @Column(name = "employee_id", length = 50)
    private String employeeId;
    
    @Column(name = "department_id", length = 64)
    private String departmentId;
    
    @Column(name = "position", length = 100)
    private String position;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;
    
    @Column(name = "must_change_password")
    @Builder.Default
    private Boolean mustChangePassword = false;
    
    @Column(name = "password_expired_at")
    private Instant passwordExpiredAt;
    
    @Column(name = "last_login_at")
    private Instant lastLoginAt;
    
    @Column(name = "last_login_ip", length = 50)
    private String lastLoginIp;
    
    @Column(name = "failed_login_count")
    @Builder.Default
    private Integer failedLoginCount = 0;
    
    @Column(name = "locked_until")
    private Instant lockedUntil;
    
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
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<UserRole> userRoles = new HashSet<>();
    
    /**
     * 检查用户是否被锁定
     */
    public boolean isLocked() {
        return status == UserStatus.LOCKED || 
               (lockedUntil != null && lockedUntil.isAfter(Instant.now()));
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
