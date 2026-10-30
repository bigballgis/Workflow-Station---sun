package com.workflow.assignment;

import com.platform.common.assignment.AssignmentReopenReceipt;
import java.util.List;
import java.util.UUID;

public interface AssignmentReopenComponent {
    AssignmentReopenReceipt reopen(String source, AssignmentReopenReceipt.Request request);
    List<AssignmentReopenReceipt> pending(int limit);
    void mirrored(UUID operation);
    void retry(UUID operation, boolean conflict);
}
