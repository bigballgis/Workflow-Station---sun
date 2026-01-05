package com.admin.entity;

import com.admin.enums.LogLevel;
import com.admin.enums.LogType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * 系统日志实体
 */
@Entity
@Table(name = "admin_system_logs", indexes = {
        @Index(name = "idx_log_type", columnList = "logType"),
        @Index(name = "idx_log_level", columnList = "logLevel"),
        @Index(name = "idx_log_timestamp", columnList = "timestamp"),
        @Index(name = "idx_log_user", columnList = "userId"),
        @Index(name = "idx_log_module", columnList = "module")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemLog {
    
    @Id
    private String id;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LogType logType;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LogLevel logLevel;
    
    private String module;
    private String action;
    
    @Column(columnDefinition = "TEXT")
    private String message;
    
    @Column(columnDefinition = "TEXT")
    private String stackTrace;
    
    private String userId;
    private String userName;
    private String ipAddress;
    private String userAgent;
    private String requestUrl;
    private String requestMethod;
    private Long responseTime;
    private Integer responseStatus;
    
    @Column(columnDefinition = "TEXT")
    private String requestBody;
    
    @Column(columnDefinition = "TEXT")
    private String responseBody;
    
    @Column(columnDefinition = "TEXT")
    private String extraData;
    
    @CreationTimestamp
    private Instant timestamp;
}
