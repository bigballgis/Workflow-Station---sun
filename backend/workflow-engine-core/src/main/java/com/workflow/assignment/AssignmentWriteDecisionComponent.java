package com.workflow.assignment;

import com.platform.common.assignment.AssignmentWriteDecision;

public interface AssignmentWriteDecisionComponent {
    AssignmentWriteDecision prepare(AssignmentWriteDecision.Request request);
}
