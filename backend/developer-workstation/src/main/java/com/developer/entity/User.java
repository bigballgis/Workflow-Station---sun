package com.developer.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity(name = "DeveloperUser")
@Table(name = "sys_users")
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
    
    @Column(name = "full_name")
    private String fullName;
    
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
