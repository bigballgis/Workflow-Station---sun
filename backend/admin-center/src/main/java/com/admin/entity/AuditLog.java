package com.admin.entity;

import com.admin.enums.AuditAction;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    
    @JsonProperty("username")
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

    /** 前端兼容：result = SUCCESS/FAILED */
    @JsonGetter("result")
    public String getResult() {
        return Boolean.TRUE.equals(success) ? "SUCCESS" : "FAILED";
    }

    /** 前端兼容：createdAt */
    @JsonGetter("createdAt")
    public String getCreatedAt() {
        return timestamp != null ? timestamp.toString() : null;
    }

    /** 前端兼容：description */
    @JsonGetter("description")
    public String getDescription() {
        return changeDetails;
    }

    /** 前端兼容：duration (ms)，实体无此字段时返回 0 */
    @JsonGetter("duration")
    public int getDuration() {
        return 0;
    }

    /** 前端兼容：requestMethod，实体无此字段 */
    @JsonGetter("requestMethod")
    public String getRequestMethod() {
        return null;
    }

    /** 前端兼容：requestPath，实体无此字段 */
    @JsonGetter("requestPath")
    public String getRequestPath() {
        return null;
    }

    /** 前端兼容：requestParams，实体无此字段 */
    @JsonGetter("requestParams")
    public Object getRequestParams() {
        return null;
    }

    /** 前端兼容：errorMessage */
    @JsonGetter("errorMessage")
    public String getErrorMessage() {
        return failureReason;
    }
}
