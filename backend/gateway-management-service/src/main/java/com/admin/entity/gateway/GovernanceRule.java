package com.admin.entity.gateway;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "ac_gateway_governance_rule")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class GovernanceRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "rule_code", nullable = false, length = 128)
    private String ruleCode;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "environment_code", length = 32)
    private String environmentCode;

    @Column(name = "rule_type", nullable = false, length = 32)
    private String ruleType;

    @Column(name = "severity", nullable = false, length = 32)
    @Builder.Default
    private String severity = "WARN";

    @Column(name = "expression", nullable = false, columnDefinition = "TEXT")
    private String expression;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @CreatedBy
    @Column(name = "created_by", length = 64)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
