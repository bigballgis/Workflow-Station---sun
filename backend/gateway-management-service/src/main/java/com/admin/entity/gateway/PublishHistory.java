package com.admin.entity.gateway;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "ac_gateway_publish_history")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class PublishHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "release_id", nullable = false)
    private Long releaseId;

    @Column(name = "operation", nullable = false, length = 32)
    private String operation;

    @Column(name = "result", nullable = false, length = 32)
    private String result;

    @Column(name = "runtime_revision", length = 128)
    private String runtimeRevision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail_json", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> detailJson = Map.of();

    @Column(name = "operator", length = 64)
    private String operator;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
