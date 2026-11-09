package com.developer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 设计站功能单元与 admin 虚拟组（sys_virtual_groups.id）的分配关系
 */
@Entity
@Table(name = "dw_function_unit_dev_groups",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_dw_fu_dev_group", columnNames = {"function_unit_id", "virtual_group_id"}),
                // A function unit belongs to exactly one workspace (platform access model §6.3)
                @UniqueConstraint(name = "uq_dw_fu_dev_group_fu", columnNames = {"function_unit_id"})})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionUnitDevGroupAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "function_unit_id", nullable = false)
    private Long functionUnitId;

    @Column(name = "virtual_group_id", nullable = false, length = 64)
    private String virtualGroupId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
