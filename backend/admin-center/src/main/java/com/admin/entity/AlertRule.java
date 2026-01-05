package com.admin.entity;

import com.admin.enums.AlertSeverity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "admin_alert_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class AlertRule {
    
    @Id
    @Column(length = 36)
    private String id;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(length = 50)
    private String metricName;
    
    @Column(length = 20)
    private String operator; // GT, LT, EQ, GTE, LTE
    
    @Column
    private Double threshold;
    
    @Column
    private Integer duration; // 持续时间(秒)
    
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AlertSeverity severity;
    
    @Column(length = 500)
    private String notifyChannels; // JSON: ["email", "sms"]
    
    @Column
    @Builder.Default
    private Boolean enabled = true;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
