package com.admin.entity.gateway;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "ac_gateway_compliance_check")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ComplianceCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "release_id", nullable = false)
    private Long releaseId;

    @Column(name = "passed", nullable = false)
    private Boolean passed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "violations_json", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> violationsJson = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings_json", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> warningsJson = List.of();

    @Column(name = "checked_at", nullable = false)
    @Builder.Default
    private Instant checkedAt = Instant.now();

    @Column(name = "checked_by", length = 64)
    private String checkedBy;
}
