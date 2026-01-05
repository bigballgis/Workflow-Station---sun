package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * 安全策略实体
 */
@Entity
@Table(name = "admin_security_policies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityPolicy {
    
    @Id
    private String id;
    
    @Column(nullable = false, unique = true)
    private String policyType;  // PASSWORD, LOGIN, SESSION
    
    @Column(nullable = false)
    private String policyName;
    
    @Column(columnDefinition = "TEXT")
    private String policyConfig;  // JSON配置
    
    private Boolean enabled;
    
    @CreationTimestamp
    private Instant createdAt;
    
    @UpdateTimestamp
    private Instant updatedAt;
    
    private String updatedBy;
}
