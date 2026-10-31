package com.platform.common.assignment;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

/** Portal-owned committed source values. INITIAL positively means no Portal mirror has existed. */
public record AssignmentSourceSnapshot(int contractVersion, UUID caseId, String instanceId,
        long caseRevision, long portalRevision, State state, JsonNode values) {
    public enum State { INITIAL, READY }
    public record Request(@NotNull UUID caseId, @NotBlank @Size(max=64) String instanceId,
            @NotBlank @Size(max=255) String definitionId,
            @NotNull @Size(min=1,max=16) List<@NotBlank @Size(max=255) String> fields, @Valid Row row) { }
    public record Row(@NotBlank @Size(max=255) String storeKey, @NotNull JsonNode key) { }
}
