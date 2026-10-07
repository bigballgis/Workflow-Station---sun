package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * SLA lead time of one Function Unit (calendar days). The portal stamps every open case's due date
 * as start date + {@link #leadTimeDays}; which fields hold those dates is design-time config
 * ({@code dw_table_definitions.sla_config}). Environment data: never exported with the FU.
 */
@Entity
@Table(name = "ac_sla_policies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "functionUnitCode")
public class SlaPolicy {

    @Id
    @Column(name = "function_unit_code", length = 100)
    private String functionUnitCode;

    @Column(name = "lead_time_days", nullable = false)
    private Integer leadTimeDays;

    /** Bumped on every change; a recalculation job is tied to the version it serves. */
    @Column(nullable = false)
    private Integer version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;
}
