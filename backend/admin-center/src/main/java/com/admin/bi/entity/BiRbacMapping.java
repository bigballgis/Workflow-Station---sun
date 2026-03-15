package com.admin.bi.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Sys_Role 与 Superset_Role 映射实体
 * 存储系统角色与 Superset 角色之间的多对多映射关系
 */
@Entity
@Table(name = "bi_rbac_mapping",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"sys_role_id", "superset_role_id"})
        },
        indexes = {
                @Index(name = "idx_bi_rbac_sys_role", columnList = "sys_role_id")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class BiRbacMapping {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "sys_role_id", nullable = false, length = 64)
    private String sysRoleId;

    @Column(name = "superset_role_id", nullable = false)
    private Integer supersetRoleId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
