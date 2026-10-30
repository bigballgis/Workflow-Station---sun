package com.workflow.assignment;

public interface AssignmentProjectionCoordinator {
    void apply(AssignmentCaseEvaluator.Decision decision);
    void retireMissing(String taskId);
}
