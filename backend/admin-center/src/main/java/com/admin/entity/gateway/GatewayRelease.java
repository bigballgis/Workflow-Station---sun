package com.admin.entity.gateway;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "ac_gateway_release")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class GatewayRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "environment_id", nullable = false)
    private Long environmentId;

    @Column(name = "release_no", nullable = false, length = 64)
    private String releaseNo;

    @Column(name = "state", nullable = false, length = 32)
    @Builder.Default
    private String state = "DRAFT";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> snapshotJson = Map.of();

    @Column(name = "snapshot_hash", nullable = false, length = 128)
    private String snapshotHash;

    @Column(name = "source_release_id")
    private Long sourceReleaseId;

    @Column(name = "promoted_from_env_id")
    private Long promotedFromEnvId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @CreatedBy
    @Column(name = "created_by", length = 64)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
