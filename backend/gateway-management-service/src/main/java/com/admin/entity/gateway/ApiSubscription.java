package com.admin.entity.gateway;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "ac_gateway_api_subscription")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ApiSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "api_version_id", nullable = false)
    private Long apiVersionId;

    @Column(name = "environment_id", nullable = false)
    private Long environmentId;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "credential_id")
    private Long credentialId;

    @Column(name = "granted_by", length = 64)
    private String grantedBy;

    @Column(name = "granted_at", nullable = false)
    @Builder.Default
    private Instant grantedAt = Instant.now();

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
