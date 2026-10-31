package com.workflow.assignment;

import com.platform.common.assignment.AssignmentOperationReceipt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentOperationRecovery {
    Optional<AssignmentOperationReceipt> find(UUID operationId);
    Optional<AssignmentOperationReceipt> byTask(String taskId);
    List<AssignmentOperationReceipt> pending(int limit);
    void confirm(UUID operationId, String digest);
    void retry(UUID operationId, boolean conflict);
}
