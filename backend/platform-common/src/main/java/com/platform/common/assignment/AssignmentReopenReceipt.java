package com.platform.common.assignment;

import java.util.Map;
import java.util.UUID;

/** Service-only frozen initial data for the new round; the public result omits this snapshot. */
public record AssignmentReopenReceipt(UUID operationId, String sourceInstanceId, String processInstanceId,
        String processDefinitionId, String actor, Map<String,Object> variables, String processState, boolean mirrored, UUID caseId) {
    public record Request(@jakarta.validation.constraints.NotNull UUID operationId,
            @jakarta.validation.constraints.Size(max=64) String actor) { }
    public record Result(UUID operationId, String processInstanceId, String processDefinitionId, String synchronizationState) { }
    public Result result() { return new Result(operationId, processInstanceId, processDefinitionId, mirrored ? "READY" : "PENDING_SYNC"); }
}
