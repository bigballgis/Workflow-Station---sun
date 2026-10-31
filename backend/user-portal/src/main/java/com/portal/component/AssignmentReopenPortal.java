package com.portal.component;

import com.platform.common.assignment.AssignmentReopenReceipt;
import java.util.UUID;

public interface AssignmentReopenPortal {
    AssignmentReopenReceipt.Result reopen(String source, UUID operation, String actor);
}
