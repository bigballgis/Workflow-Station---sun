package com.admin.entity.gateway;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "ac_gateway_catalog_visibility")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class CatalogVisibility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "api_definition_id", nullable = false)
    private Long apiDefinitionId;

    @Column(name = "visibility", nullable = false, length = 32)
    @Builder.Default
    private String visibility = "INTERNAL";

    @Column(name = "visible_in_marketplace", nullable = false)
    @Builder.Default
    private Boolean visibleInMarketplace = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_environments", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<String> allowedEnvironments = List.of();

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
