package com.workflow.assignment;

import com.platform.common.assignment.AssignmentCompletionRequest;
import com.platform.common.assignment.AssignmentOperationReceipt;

public interface AssignmentAcceptedCompletion {
    AssignmentOperationReceipt complete(AssignmentCompletionRequest request);
}
