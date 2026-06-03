package com.admin.entity.gateway;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "ac_gateway_metrics_snapshot")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class MetricsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "api_definition_id")
    private Long apiDefinitionId;

    @Column(name = "environment_id", nullable = false)
    private Long environmentId;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "qps", precision = 18, scale = 4)
    private BigDecimal qps;

    @Column(name = "p50_latency_ms", precision = 18, scale = 4)
    private BigDecimal p50LatencyMs;

    @Column(name = "p95_latency_ms", precision = 18, scale = 4)
    private BigDecimal p95LatencyMs;

    @Column(name = "error_rate", precision = 8, scale = 6)
    private BigDecimal errorRate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics_json", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metricsJson = Map.of();

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
