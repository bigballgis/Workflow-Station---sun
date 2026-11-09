package com.portal.entity;

import com.portal.enums.ChangeType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * 数据变更历史实体
 */
@Entity
@Table(name = "up_change_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ChangeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "process_instance_id", nullable = false, length = 64)
    private String processInstanceId;

    @Column(name = "task_instance_id", length = 64)
    private String taskInstanceId;

    @Column(name = "stage_id", length = 255)
    private String stageId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "batch_id", length = 36)
    private String batchId;

    @Column(name = "batch_time")
    private Instant batchTime;

    @Column(name = "field_name", nullable = false, length = 255)
    private String fieldName;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 30)
    private ChangeType changeType;

    @Column(name = "sub_table_name", length = 255)
    private String subTableName;

    @Column(name = "row_identifier", length = 255)
    private String rowIdentifier;

    /**
     * Why the assignment changed (platform access model): {@code POOL_MEMBERSHIP_LOST} (D-40, system: the holder no
     * longer holds the pool's BU + Role), {@code ASSIGNEE_DEPARTED} (D-42, system: the holder left), or
     * {@code HOLDER_CHANGED} on a {@code DELEGATION_ENDED} row (D-59, by the user or the system: the task changed hands
     * or its hold ended); null otherwise.
     */
    @Column(name = "reason", length = 40)
    private String reason;

    @Column(name = "is_concurrent", nullable = false)
    @Builder.Default
    private Boolean isConcurrent = false;
}
