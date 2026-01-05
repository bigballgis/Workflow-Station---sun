package com.admin.entity;

import com.admin.enums.AuditAction;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * 审计日志实体
 */
@Entity
@Table(name = "admin_audit_logs", indexes = {
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_user", columnList = "userId"),
        @Index(name = "idx_audit_resource", columnList = "resourceType,resourceId"),
        @Index(name = "idx_audit_timestamp", columnList = "timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    
    @Id
    private String id;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;
    
    @Column(nullable = false)
    private String resourceType;
    
    private String resourceId;
    private String resourceName;
    
    @Column(nullable = false)
    private String userId;
    
    private String userName;
    private String ipAddress;
    private String userAgent;
    
    @Column(columnDefinition = "TEXT")
    private String oldValue;
    
    @Column(columnDefinition = "TEXT")
    private String newValue;
    
    @Column(columnDefinition = "TEXT")
    private String changeDetails;
    
    private Boolean success;
    private String failureReason;
    
    @CreationTimestamp
    private Instant timestamp;
}
