package com.admin.entity.gateway;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "ac_gateway_provider_revision")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ProviderRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "release_id", nullable = false)
    private Long releaseId;

    @Column(name = "environment_id", nullable = false)
    private Long environmentId;

    @Column(name = "gateway_provider", nullable = false, length = 32)
    private String gatewayProvider;

    @Column(name = "runtime_revision", length = 128)
    private String runtimeRevision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail_json", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> detailJson = Map.of();

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
