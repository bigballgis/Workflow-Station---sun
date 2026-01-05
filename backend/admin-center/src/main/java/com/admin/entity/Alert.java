package com.admin.entity;

import com.admin.enums.AlertSeverity;
import com.admin.enums.AlertStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "admin_alerts", indexes = {
        @Index(name = "idx_alert_status", columnList = "status"),
        @Index(name = "idx_alert_severity", columnList = "severity")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Alert {
    
    @Id
    @Column(length = 36)
    private String id;
    
    @Column(name = "rule_id", length = 36)
    private String ruleId;
    
    @Column(nullable = false, length = 200)
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String message;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertSeverity severity;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertStatus status;
    
    @Column
    private Double metricValue;
    
    @Column(length = 36)
    private String acknowledgedBy;
    
    @Column
    private Instant acknowledgedAt;
    
    @Column(length = 36)
    private String resolvedBy;
    
    @Column
    private Instant resolvedAt;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
