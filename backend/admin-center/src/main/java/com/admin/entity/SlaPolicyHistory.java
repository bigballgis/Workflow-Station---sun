package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** One SLA lead time change and the recalculation job it dispatched to the portal. */
@Entity
@Table(name = "ac_sla_policy_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class SlaPolicyHistory {

    public enum DispatchStatus { PENDING, DISPATCHED, DISPATCH_FAILED }

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "function_unit_code", nullable = false, length = 100)
    private String functionUnitCode;

    @Column(name = "old_lead_time_days")
    private Integer oldLeadTimeDays;

    @Column(name = "new_lead_time_days", nullable = false)
    private Integer newLeadTimeDays;

    @Column(name = "old_version")
    private Integer oldVersion;

    @Column(name = "new_version", nullable = false)
    private Integer newVersion;

    @Column(name = "change_reason", length = 500)
    private String changeReason;

    @Column(name = "changed_by", length = 64)
    private String changedBy;

    @CreationTimestamp
    @Column(name = "changed_at", updatable = false)
    private Instant changedAt;

    @Column(name = "recalc_job_id", length = 36)
    private String recalcJobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispatch_status", nullable = false, length = 20)
    private DispatchStatus dispatchStatus;

    @Column(name = "dispatch_error", length = 1000)
    private String dispatchError;
}
