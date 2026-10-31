package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import org.springframework.stereotype.Component;

/** Implemented runtime contract; each service still verifies the shared write-primary coordination proof. */
@Component
public class AssignmentRuntimeSupport {
    public Capability capability() { return new Capability(1,true); }
    public void require(boolean tracked) {
        var current=capability();
        if (tracked && (!current.ready() || current.contractVersion()<1)) {
            throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_ENGINE_UNSUPPORTED);
        }
    }
    public record Capability(int contractVersion,boolean ready) { }
}
