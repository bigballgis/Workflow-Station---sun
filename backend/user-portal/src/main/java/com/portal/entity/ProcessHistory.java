package com.portal.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 流程历史记录实体
 */
@Entity
@Table(name = "up_process_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "process_instance_id", nullable = false, length = 64)
    private String processInstanceId;

    @Column(name = "task_id", length = 64)
    private String taskId;

    @Column(name = "activity_id", length = 100)
    private String activityId;

    @Column(name = "activity_name", length = 255)
    private String activityName;

    @Column(name = "activity_type", length = 50)
    private String activityType;

    @Column(name = "operation_type", nullable = false, length = 50)
    private String operationType;

    @Column(name = "operator_id", nullable = false, length = 64)
    private String operatorId;

    @Column(name = "operator_name", length = 100)
    private String operatorName;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "duration")
    private Long duration;

    @CreationTimestamp
    @Column(name = "operation_time", updatable = false)
    private LocalDateTime operationTime;
}
