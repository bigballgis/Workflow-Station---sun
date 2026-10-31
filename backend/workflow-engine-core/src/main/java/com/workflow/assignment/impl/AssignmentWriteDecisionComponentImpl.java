package com.workflow.assignment.impl;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.assignment.AssignmentWriteDecision;
import com.workflow.assignment.*;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Preparation calls Admin outside a transaction; only the final local recheck takes the case lock. */
@Component
public class AssignmentWriteDecisionComponentImpl implements AssignmentWriteDecisionComponent {
    private final TaskService tasks;
    private final AssignmentCaseRegistry registry;
    private final AssignmentWriteGuard guard;
    private final AssignmentLegacyTaskCheck legacy;
    private final TransactionTemplate transaction;
    private static final java.util.Set<String> OPERATIONS = java.util.Set.of("SAVE", "ACTION_FORM", "SUBTABLE_ASSIGN", "RECORD_NOTE", "ATTACHMENT", "COMPLETE");

    public AssignmentWriteDecisionComponentImpl(TaskService tasks, AssignmentCaseRegistry registry, AssignmentWriteGuard guard,
            AssignmentLegacyTaskCheck legacy, PlatformTransactionManager manager) {
        this.tasks = tasks; this.registry = registry; this.guard = guard; this.legacy = legacy;
        transaction = new TransactionTemplate(manager); transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override public AssignmentWriteDecision prepare(AssignmentWriteDecision.Request request) {
        if (TransactionSynchronizationManager.isActualTransactionActive() || !OPERATIONS.contains(request.operation())) throw unavailable();
        boolean tracked = Boolean.TRUE.equals(transaction.execute(status -> {
            var task = tasks.createTaskQuery().taskId(request.taskId()).singleResult();
            if (task == null) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED);
            if (!task.getProcessInstanceId().equals(request.instanceId())) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_OPERATION_CONFLICT);
            boolean registered=registry.instance(task.getProcessInstanceId()).isPresent();
            if (!registered) legacy.requireLegacy(task);
            return registered;
        }));
        if (!tracked) return AssignmentWriteDecision.legacy(request);
        var command = new AssignmentWriteGuard.Command(request.taskId(), "COMPLETE".equals(request.operation())
                ? AssignmentMutationScope.Operation.COMPLETE : AssignmentMutationScope.Operation.WRITE,
                request.actor(), request.representedUser(), null);
        var prepared = guard.prepare(command).orElseThrow(this::unavailable);
        return transaction.execute(status -> {
            guard.validate(prepared);
            var decision = prepared.decision();
            return new AssignmentWriteDecision(1, true, request.taskId(), request.actor(), request.representedUser(), request.operation(),
                    request.requestDigest(), decision.caseId(), decision.caseRevision(), decision.portalRevision(),
                    decision.qualification().authorityRevision(), decision.qualification().validUntil(), request.instanceId());
        });
    }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE); }
}
