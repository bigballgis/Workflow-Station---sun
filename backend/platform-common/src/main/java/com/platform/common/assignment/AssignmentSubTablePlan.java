package com.platform.common.assignment;

/** Server-owned frozen configuration and exact active target, prepared before the Portal write transaction. */
public record AssignmentSubTablePlan(boolean tracked,String taskId,String instanceId,String definitionId,
        String table,String assigneeField,String foreignKey,String target,long authorityRevision) {
    public record Request(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=64) String taskId,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=64) String target) { }
}
