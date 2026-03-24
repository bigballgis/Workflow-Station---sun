package com.developer.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 决策定义实体
 * 存储与功能单元关联的 DMN 决策表定义
 */
@Entity
@Table(name = "dw_decision_definitions",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_decision_fu_key",
           columnNames = {"function_unit_id", "decision_key"}))
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class DecisionDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "function_unit_id", nullable = false)
    private FunctionUnit functionUnit;

    @Column(name = "decision_key", nullable = false, length = 100)
    private String decisionKey;

    @Column(name = "decision_name", length = 200)
    private String decisionName;

    @Column(name = "dmn_xml", columnDefinition = "TEXT")
    private String dmnXml;

    @Column(name = "hit_policy", length = 20)
    private String hitPolicy;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
