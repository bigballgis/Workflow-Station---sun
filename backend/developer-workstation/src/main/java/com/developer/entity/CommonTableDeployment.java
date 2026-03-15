package com.developer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "dw_common_table_deployments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class CommonTableDeployment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "common_table_id", nullable = false)
    private CommonTableDefinition commonTable;

    @Column(name = "version", nullable = false, length = 20)
    private String version;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "COMPLETED";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_snapshot", columnDefinition = "jsonb")
    private String fieldSnapshot;

    @Column(name = "deployed_at", nullable = false)
    private Instant deployedAt;

    @Column(name = "deployed_by", length = 100)
    private String deployedBy;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
