package com.platform.security.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * User entity for authentication and authorization.
 * Validates: Requirements 1.1, 1.2, 1.4, 1.5
 */
@Entity
@Table(name = "sys_user", indexes = {
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
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(length = 100)
    private String email;

    @Column(name = "display_name", length = 50)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "department_id", length = 50)
    private String departmentId;

    @Column(length = 10)
    @Builder.Default
    private String language = "zh_CN";

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sys_user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role_code")
    @Builder.Default
    private Set<String> roles = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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
        return status == UserStatus.LOCKED;
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
}
