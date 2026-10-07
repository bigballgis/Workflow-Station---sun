package com.admin.dto;

/** One case a recalculation job updated, skipped or failed on. */
public record SlaRecalcJobItemResponse(
        String processInstanceId,
        String outcome,
        String oldDueDate,
        String newDueDate,
        String reason,
        String createdAt) {
}
