package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Action Definition Entity (Production)
 * Stores action definitions deployed with function units
 */
@Entity
@Table(name = "sys_action_definitions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionDefinition {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(name = "function_unit_id", nullable = false, length = 64)
    private String functionUnitId;
    
    @Column(name = "action_name", nullable = false, length = 100)
    private String actionName;
    
    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "config_json", columnDefinition = "JSONB")
    private String configJson;
    
    @Column(name = "icon", length = 50)
    private String icon;
    
    @Column(name = "button_color", length = 20)
    private String buttonColor;
    
    @Column(name = "is_default")
    private Boolean isDefault;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "created_by", length = 64)
    private String createdBy;
    
    @Column(name = "updated_by", length = 64)
    private String updatedBy;
    
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
