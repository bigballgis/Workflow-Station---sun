package com.admin.bi.entity;

import com.admin.bi.enums.AssignmentTargetType;
import com.admin.bi.enums.LayoutMode;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Dashboard 分配记录实体
 * 存储 Dashboard 按 User/Role/Business Unit 维度的分配关系
 */
@Entity
@Table(name = "bi_dashboard_assignment",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"dashboard_id", "target_type", "target_id"})
        },
        indexes = {
                @Index(name = "idx_bi_assignment_target", columnList = "target_type, target_id"),
                @Index(name = "idx_bi_assignment_dashboard", columnList = "dashboard_id")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class BiDashboardAssignment {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "dashboard_id", nullable = false, length = 64)
    private String dashboardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private AssignmentTargetType targetType;

    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "layout_mode", nullable = false, length = 20)
    @Builder.Default
    private LayoutMode layoutMode = LayoutMode.SINGLE;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 64)
    private String updatedBy;
}
