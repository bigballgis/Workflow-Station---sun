package com.admin.entity.gateway;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "ac_gateway_subscription_request")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class SubscriptionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "environment_id", nullable = false)
    private Long environmentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "api_version_ids", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<Long> apiVersionIds = List.of();

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "justification", columnDefinition = "TEXT")
    private String justification;

    @Column(name = "requester_id", nullable = false, length = 64)
    private String requesterId;

    @Column(name = "workflow_instance_id", length = 128)
    private String workflowInstanceId;

    @Column(name = "decided_by", length = 64)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_comment", columnDefinition = "TEXT")
    private String decisionComment;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
