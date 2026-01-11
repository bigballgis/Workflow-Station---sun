package com.platform.security.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * User entity for authentication and authorization.
 * Uses unified sys_users table with varchar(64) ID.
 * Validates: Requirements 1.1, 1.2, 1.4, 1.5
 */
@Entity
@Table(name = "sys_users", indexes = {
    @Index(name = "idx_user_username", columnList = "username"),
    @Index(name = "idx_user_status", columnList = "status"),
    @Index(name = "idx_user_email", columnList = "email")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class User {

    @Id
    @Column(length = 64)
    private String id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(length = 100)
    private String email;

    @Column(name = "display_name", length = 50)
    private String displayName;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(name = "employee_id", length = 50)
    private String employeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "department_id", length = 50)
    private String departmentId;

    @Column(length = 100)
    private String position;

    @Column(name = "entity_manager_id", length = 64)
    private String entityManagerId;

    @Column(name = "function_manager_id", length = 64)
    private String functionManagerId;

    @Column(length = 10)
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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sys_user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role_id")
    @Builder.Default
    private Set<String> roles = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "deleted")
    @Builder.Default
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by", length = 64)
    private String deletedBy;

    /**
     * Check if user account is active and can login.
     */
    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    /**
     * Check if user account is locked.
     */
    public boolean isLocked() {
        return status == UserStatus.LOCKED || 
               (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now()));
    }

    /**
     * Check if user account is inactive.
     */
    public boolean isInactive() {
        return status == UserStatus.INACTIVE;
    }

    /**
     * Add a role to the user.
     */
    public void addRole(String roleCode) {
        if (roles == null) {
            roles = new HashSet<>();
        }
        roles.add(roleCode);
    }

    /**
     * Remove a role from the user.
     */
    public void removeRole(String roleCode) {
        if (roles != null) {
            roles.remove(roleCode);
        }
    }

    /**
     * Increment failed login count.
     */
    public void incrementFailedLoginCount() {
        this.failedLoginCount = (this.failedLoginCount == null ? 0 : this.failedLoginCount) + 1;
    }

    /**
     * Reset failed login count.
     */
    public void resetFailedLoginCount() {
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }
}
