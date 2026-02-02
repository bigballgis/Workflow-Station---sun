package com.portal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * User entity for User Portal auth (login/refresh/me).
 * Shared table with Admin Center: projectx.sys_users.
 * Explicit schema is required: without it, in some environments (e.g. docker profile
 * or connection defaulting to public) the query can resolve to public.sys_users and
 * return no rows, causing "user not found" on login.
 */
@Entity
@Table(name = "sys_users", schema = "projectx")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(name = "username", nullable = false, unique = true)
    private String username;
    
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    
    @Column(name = "email")
    private String email;
    
    @Column(name = "display_name")
    private String displayName;
    
    @Column(name = "status")
    private String status;
    
    @Column(name = "language")
    private String language;
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
    @Column(name = "last_login_ip")
    private String lastLoginIp;
    
    @Column(name = "failed_login_count")
    private Integer failedLoginCount;
    
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
    
    public boolean isLocked() {
        return "LOCKED".equals(status) || 
               (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now()));
    }
    
    public void incrementFailedLoginCount() {
        this.failedLoginCount = (this.failedLoginCount == null ? 0 : this.failedLoginCount) + 1;
    }
    
    public void resetFailedLoginCount() {
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }
}
