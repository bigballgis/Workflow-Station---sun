package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Relation Table 审计日志实体
 * 记录数据变更操作（ADD、UPDATE、DELETE、STATUS_CHANGE）的日志
 */
@Entity
@Table(name = "rt_audit_logs", indexes = {
        @Index(name = "idx_rt_audit_table", columnList = "table_id"),
        @Index(name = "idx_rt_audit_action", columnList = "action"),
        @Index(name = "idx_rt_audit_operator", columnList = "operator_id"),
        @Index(name = "idx_rt_audit_time", columnList = "operated_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class RelationTableAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 64)
    private String id;

    @Column(name = "table_id", nullable = false)
    private Long tableId;

    @Column(name = "table_name", nullable = false, length = 100)
    private String tableName;

    @Column(name = "row_id", length = 100)
    private String rowId;

    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "operator_id", nullable = false, length = 64)
    private String operatorId;

    @Column(name = "operator_name", length = 100)
    private String operatorName;

    @Column(name = "operated_at", nullable = false)
    private Instant operatedAt;
}
