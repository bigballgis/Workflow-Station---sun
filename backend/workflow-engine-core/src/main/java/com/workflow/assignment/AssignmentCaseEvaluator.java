package com.workflow.assignment;

import com.platform.common.assignment.AssignmentExclusionPolicy;
import com.platform.common.assignment.AssignmentIdentitySnapshot;
import org.flowable.task.api.Task;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Server-local decision preparation followed by a final check in the caller's write transaction. */
public interface AssignmentCaseEvaluator {
    Decision prepare(Task task, AssignmentSourceSpec source, List<AssignmentIdentitySnapshot.Reference> additional);
    void checkCurrent(Decision decision);

    record Decision(String taskId, UUID caseId, long caseRevision, AssignmentExclusionPolicy policy,
            Set<String> excluded, AssignmentQualificationComponent.Prepared qualification,
            AssignmentExclusionEvaluator.Pool pool, long portalRevision) { }
}
