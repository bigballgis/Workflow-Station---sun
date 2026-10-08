package com.admin.dto;

/** A portal recalculation job as recorded in {@code up_sla_recalc_jobs}. */
public record SlaRecalcJobResponse(
        String id,
        Integer policyVersion,
        Integer leadTimeDays,
        String triggeredBy,
        String status,
        int totalCount,
        int updatedCount,
        int unchangedCount,
        int skippedCount,
        int failedCount,
        String errorMessage,
        String submittedAt,
        String startedAt,
        String finishedAt) {
}
