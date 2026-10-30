package com.workflow.assignment.impl;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.assignment.AssignmentIdentitySnapshot;
import com.workflow.assignment.*;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Component;

import java.util.List;

/** Preparation has no business transaction; the separately proxied projection owns its short write transaction. */
@Component
public class AssignmentReconciliationComponentImpl implements AssignmentReconciliationComponent {
    private final TaskService tasks;
    private final AssignmentCaseEvaluator evaluator;
    private final AssignmentTaskStateRepository states;
    private final AssignmentProjectionCoordinator projection;

    public AssignmentReconciliationComponentImpl(TaskService tasks, AssignmentCaseEvaluator evaluator,
            AssignmentTaskStateRepository states, AssignmentProjectionCoordinator projection) {
        this.tasks = tasks; this.evaluator = evaluator; this.states = states; this.projection = projection;
    }

    @Override public void reconcile(String taskId) {
        var state = states.find(taskId).orElseThrow();
        if (!state.active()) return;
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        }
        var task = tasks.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) { projection.retireMissing(taskId); return; }
        if (state.sourceSpec() == null) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        var additional = task.getAssignee() == null ? List.<AssignmentIdentitySnapshot.Reference>of() : List.of(
                new AssignmentIdentitySnapshot.Reference(task.getAssignee(), AssignmentIdentitySnapshot.Kind.ID));
        projection.apply(evaluator.prepare(task, state.sourceSpec(), additional));
    }
}
