package com.platform.common.assignment;

/** Current management qualification; this never exempts an administrator from task responsibility exclusions. */
public record AssignmentManagementSnapshot(long authorityRevision, String userId, boolean active, boolean systemAdministrator) {
    public record Request(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=64) String userId) { }
}
