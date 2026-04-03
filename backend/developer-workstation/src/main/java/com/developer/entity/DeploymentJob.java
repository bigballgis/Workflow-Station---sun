package com.developer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 一键部署到管理中心的任务状态（PostgreSQL 持久化，支持多实例与重启后查询）
 */
@Entity
@Table(name = "dw_deployment_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentJob {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "function_unit_id", nullable = false)
    private Long functionUnitId;

    @Column(name = "target_admin_url", length = 1024)
    private String targetAdminUrl;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "progress")
    private Integer progress;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "version_number", length = 64)
    private String versionNumber;

    @Column(name = "change_log", columnDefinition = "TEXT")
    private String changeLog;

    @Column(name = "steps_json", columnDefinition = "TEXT")
    private String stepsJson;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (startedAt == null) {
            startedAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
