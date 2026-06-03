package com.admin.entity.module;

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
import java.util.List;

@Entity
@Table(name = "ac_frontend_module_registry")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class FrontendModuleRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "host_app", nullable = false, length = 64)
    private String hostApp;

    @Column(name = "module_code", nullable = false, length = 128)
    private String moduleCode;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "route_path", nullable = false, length = 255)
    private String routePath;

    @Column(name = "icon", length = 64)
    private String icon;

    @Column(name = "order_no", nullable = false)
    @Builder.Default
    private Integer orderNo = 100;

    @Column(name = "remote_entry_url", nullable = false, length = 512)
    private String remoteEntryUrl;

    @Column(name = "exposed_module", nullable = false, length = 128)
    @Builder.Default
    private String exposedModule = "./App";

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_permissions", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<String> requiredPermissions = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tenant_scope", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<String> tenantScope = List.of();

    @Column(name = "env", nullable = false, length = 32)
    private String env;

    @Column(name = "version", nullable = false, length = 64)
    private String version;

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
