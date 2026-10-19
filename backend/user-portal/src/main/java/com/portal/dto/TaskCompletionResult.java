package com.portal.dto;

/** Completion succeeded even when its subsequent assignment observation is not confirmed. */
public record TaskCompletionResult(BatchAssignmentVerification batchAssignmentVerification) { }
