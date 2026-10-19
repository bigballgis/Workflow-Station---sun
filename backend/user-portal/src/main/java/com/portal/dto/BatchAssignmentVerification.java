package com.portal.dto;

import java.util.List;

/** Read-only observation after the engine action; never a request to retry that action. */
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public record BatchAssignmentVerification(String status, List<Issue> issues, Boolean historyRecorded) {
    public BatchAssignmentVerification(String status, List<Issue> issues) { this(status, issues, null); }
    public record Issue(String taskId, String reasonCode) { }
}
