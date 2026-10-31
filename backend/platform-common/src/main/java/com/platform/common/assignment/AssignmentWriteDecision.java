package com.platform.common.assignment;

import java.time.Instant;
import java.util.UUID;

/** Server-to-server permission packet for one exact local Portal write; never accepted from the browser. */
public record AssignmentWriteDecision(int contractVersion, boolean tracked, String taskId, String actor,
        String representedUser, String operation, String requestDigest, UUID caseId, long caseRevision,
        long portalRevision, long authorityRevision, Instant validUntil, String instanceId) {
    public record Request(@jakarta.validation.constraints.NotBlank String taskId,
            @jakarta.validation.constraints.NotBlank String actor, String representedUser,
            @jakarta.validation.constraints.NotBlank String operation,
            @jakarta.validation.constraints.Pattern(regexp="[0-9a-f]{64}") String requestDigest,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=64) String instanceId) { }
    public static AssignmentWriteDecision legacy(Request request) {
        return new AssignmentWriteDecision(1, false, request.taskId(), request.actor(), request.representedUser(),
                request.operation(), request.requestDigest(), null, 0, 0, 0, null, request.instanceId());
    }
}
