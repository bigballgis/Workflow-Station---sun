package com.platform.common.assignment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Direct whole-request delegation only. The Engine binds the nominal holder to the real task. */
public record AssignmentDelegationSnapshot(int contractVersion, Request binding, long authorityRevision,
        boolean granted, Long ruleId, Instant validUntil) {
    public record Request(@NotNull UUID caseId, @NotBlank @Size(max=64) String taskId,
            @NotBlank @Size(max=64) String instanceId, @NotBlank @Size(max=255) String definitionId,
            @NotBlank @Size(max=64) String actor, @NotBlank @Size(max=64) String nominalHolder) { }
}
