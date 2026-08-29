package com.portal.dto;

import java.util.List;

/**
 * Result of one Claim All batch (at most {@code ClaimBatchComponent.BATCH_LIMIT} tasks).
 */
public record ClaimBatchResponse(
        int claimed,
        int skipped,
        int failed,
        int remaining,
        List<String> attemptedTaskIds) {

    public ClaimBatchResponse {
        attemptedTaskIds = attemptedTaskIds == null ? List.of() : List.copyOf(attemptedTaskIds);
    }
}
