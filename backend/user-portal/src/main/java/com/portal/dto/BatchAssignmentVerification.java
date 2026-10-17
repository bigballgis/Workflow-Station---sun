package com.portal.dto;

import java.util.List;

/** Read-only observation after the engine action; never a request to retry that action. */
public record BatchAssignmentVerification(String status, List<Issue> issues) {
    public record Issue(String taskId, String reasonCode) { }
}
