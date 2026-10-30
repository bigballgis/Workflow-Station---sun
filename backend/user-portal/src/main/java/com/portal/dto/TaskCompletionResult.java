package com.portal.dto;

/** Completion succeeded even when its subsequent assignment observation is not confirmed. */
public record TaskCompletionResult(BatchAssignmentVerification batchAssignmentVerification,java.util.UUID operationId,String state) {
    public TaskCompletionResult(BatchAssignmentVerification verification) { this(verification,null,null); }
}
