package com.portal.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * 公共表部署记录实体（user-portal 只读，用于读取字段快照）
 */
@Entity
@Table(name = "dw_common_table_deployments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class CommonTableDeployment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "common_table_id")
    private Long commonTableId;

    @Column(name = "version", length = 20)
    private String version;

    @Column(name = "status", length = 20)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_snapshot", columnDefinition = "jsonb")
    private String fieldSnapshot;

    @Column(name = "deployed_at")
    private Instant deployedAt;

    @Column(name = "deployed_by", length = 100)
    private String deployedBy;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    private Instant createdAt;
}
