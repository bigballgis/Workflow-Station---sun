package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import org.springframework.stereotype.Component;

/** Fence the case before instance-control commands take Flowable execution/task locks. */
@Component
public class AssignmentProcessWriteBoundary {
    private final AssignmentCaseRepository cases;
    private final AssignmentLegacyTaskCheck legacy;
    public AssignmentProcessWriteBoundary(AssignmentCaseRepository cases,AssignmentLegacyTaskCheck legacy) {
        this.cases=cases; this.legacy=legacy;
    }
    public void enter(String instance) {
        var context=cases.find(instance);
        if (context.isEmpty()) { legacy.requireLegacyInstance(instance); return; }
        var state=cases.lockCase(context.get().caseId());
        if (state.synchronizationPending()) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_SYNC_PENDING);
    }
}
