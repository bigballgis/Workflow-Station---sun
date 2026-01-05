package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * 系统配置实体
 */
@Entity
@Table(name = "admin_system_configs", indexes = {
        @Index(name = "idx_config_category", columnList = "category"),
        @Index(name = "idx_config_key", columnList = "configKey")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfig {
    
    @Id
    private String id;
    
    @Column(nullable = false)
    private String category;  // SYSTEM, BUSINESS, PERFORMANCE
    
    @Column(nullable = false, unique = true)
    private String configKey;
    
    @Column(nullable = false)
    private String configName;
    
    @Column(columnDefinition = "TEXT")
    private String configValue;
    
    private String defaultValue;
    private String valueType;  // STRING, INTEGER, BOOLEAN, JSON
    private String description;
    private Boolean encrypted;
    private Boolean editable;
    private Integer version;
    private String environment;  // DEV, TEST, STAGING, PROD
    
    @CreationTimestamp
    private Instant createdAt;
    
    @UpdateTimestamp
    private Instant updatedAt;
    
    private String updatedBy;
}
