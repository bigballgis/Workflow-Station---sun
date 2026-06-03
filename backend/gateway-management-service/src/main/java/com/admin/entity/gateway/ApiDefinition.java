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
@Table(name = "ac_gateway_api_definition")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ApiDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "api_code", nullable = false, length = 128)
    private String apiCode;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "domain", length = 128)
    private String domain;

    @Column(name = "base_path", nullable = false, length = 512)
    private String basePath;

    @Column(name = "protocol", nullable = false, length = 32)
    @Builder.Default
    private String protocol = "HTTP";

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "DRAFT";

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
