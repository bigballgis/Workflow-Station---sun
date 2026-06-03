package com.admin.entity.gateway;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "ac_gateway_credential")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Credential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "credential_type", nullable = false, length = 32)
    private String credentialType;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "secret_ref", length = 255)
    private String secretRef;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "expires_at")
    private Instant expiresAt;

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
