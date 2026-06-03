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
@Table(name = "ac_gateway_environment")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Environment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "env_code", nullable = false, length = 32)
    private String envCode;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "gateway_provider", nullable = false, length = 32)
    @Builder.Default
    private String gatewayProvider = "KONG";

    @Column(name = "mode", nullable = false, length = 32)
    @Builder.Default
    private String mode = "DB";

    @Column(name = "admin_endpoint", nullable = false, length = 512)
    private String adminEndpoint;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

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
