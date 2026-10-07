package com.admin.dto;

/**
 * One row of the SLA policy list: every Function Unit whose main table declares an SLA due date
 * mapping, with its lead time (null = not configured yet) and latest recalculation job status.
 */
public record SlaPolicyRow(
        String functionUnitCode,
        String functionUnitName,
        Integer leadTimeDays,
        Integer version,
        String updatedBy,
        String updatedAt,
        String latestJobStatus,
        String latestJobId) {
}
