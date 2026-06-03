package com.admin.entity.gateway;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "ac_gateway_drift_report")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class DriftReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "environment_id", nullable = false)
    private Long environmentId;

    @Column(name = "sync_mode", nullable = false, length = 32)
    @Builder.Default
    private String syncMode = "REPORT_ONLY";

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "COMPLETED";

    @Column(name = "missing_count", nullable = false)
    @Builder.Default
    private int missingCount = 0;

    @Column(name = "extra_count", nullable = false)
    @Builder.Default
    private int extraCount = 0;

    @Column(name = "mismatch_count", nullable = false)
    @Builder.Default
    private int mismatchCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "report_json", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> reportJson = Map.of();

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
