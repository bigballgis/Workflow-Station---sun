package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * 配置变更历史实体
 */
@Entity
@Table(name = "admin_config_history", indexes = {
        @Index(name = "idx_config_history_key", columnList = "configKey"),
        @Index(name = "idx_config_history_time", columnList = "changedAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigHistory {
    
    @Id
    private String id;
    
    @Column(nullable = false)
    private String configId;
    
    @Column(nullable = false)
    private String configKey;
    
    @Column(columnDefinition = "TEXT")
    private String oldValue;
    
    @Column(columnDefinition = "TEXT")
    private String newValue;
    
    private Integer oldVersion;
    private Integer newVersion;
    private String changeReason;
    private String changedBy;
    
    @CreationTimestamp
    private Instant changedAt;
}
