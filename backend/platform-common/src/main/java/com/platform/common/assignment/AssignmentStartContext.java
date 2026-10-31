package com.platform.common.assignment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Read-only exact definition choice, not actor authorization or permission to start a workflow. */
public record AssignmentStartContext(int contractVersion,String definitionId,String definitionKey,Boolean tracked,String requestedDefinitionId) {
    public static final String HEADER="X-Assignment-Start-Definition";
    public record Request(@NotBlank @Size(max=255) String definitionKey,@Size(max=255) String expectedDefinitionId) { }
}
