package com.platform.security.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Login audit entity for tracking authentication events.
 * Validates: Requirements 2.5, 3.4
 */
@Entity
@Table(name = "sys_login_audit", indexes = {
    @Index(name = "idx_login_audit_user", columnList = "user_id"),
    @Index(name = "idx_login_audit_username", columnList = "username"),
    @Index(name = "idx_login_audit_created", columnList = "created_at"),
    @Index(name = "idx_login_audit_action", columnList = "action")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class LoginAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(nullable = false, length = 50)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditAction action;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(nullable = false)
    @Builder.Default
    private boolean success = true;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Audit action types.
     */
    public enum AuditAction {
        LOGIN,
        LOGOUT,
        REFRESH,
        TOKEN_REFRESH
    }
}
