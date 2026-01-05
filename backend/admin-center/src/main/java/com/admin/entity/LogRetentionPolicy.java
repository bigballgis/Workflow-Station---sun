package com.admin.entity;

import com.admin.enums.LogType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * 日志保留策略实体
 */
@Entity
@Table(name = "admin_log_retention_policies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogRetentionPolicy {
    
    @Id
    private String id;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private LogType logType;
    
    @Column(nullable = false)
    private Integer retentionDays;
    
    private Integer archiveAfterDays;
    private String archiveLocation;
    private Boolean compressionEnabled;
    private Boolean enabled;
    
    @CreationTimestamp
    private Instant createdAt;
    
    @UpdateTimestamp
    private Instant updatedAt;
    
    private String updatedBy;
}
