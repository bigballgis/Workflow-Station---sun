package com.workflow.assignment;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;

/** Server-established authorization inside one write transaction; never populated from process variables. */
public final class AssignmentMutationScope {
    private static final Object KEY = new Object();
    private AssignmentMutationScope() { }

    public static void establish(Authorized authorized) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.hasResource(KEY)) {
            throw new IllegalStateException("Assignment authorization requires its own synchronized write scope");
        }
        TransactionSynchronizationManager.bindResource(KEY, authorized);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void suspend() { TransactionSynchronizationManager.unbindResource(KEY); }
            @Override public void resume() { TransactionSynchronizationManager.bindResource(KEY, authorized); }
            @Override public void afterCompletion(int status) { TransactionSynchronizationManager.unbindResourceIfPossible(KEY); }
        });
    }

    public static Authorized require(String taskId) {
        var authorized = (Authorized) TransactionSynchronizationManager.getResource(KEY);
        if (authorized == null || !authorized.taskId().equals(taskId)) {
            throw new com.platform.common.assignment.AssignmentFailure(
                    com.platform.common.assignment.AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        }
        return authorized;
    }

    public enum Operation { COMPLETE, CLAIM, UNCLAIM, TRANSFER, REASSIGN, DELEGATE, RETURN, ASSIGN, WRITE, HANDOFF }
    public record Authorized(String taskId, Operation operation, String actor, Set<String> responsibleUsers,
            Set<String> allowedHolders) {
        public Authorized {
            if (taskId == null || taskId.isBlank() || operation == null || actor == null || actor.isBlank()
                    || responsibleUsers == null || !responsibleUsers.contains(actor) || allowedHolders == null) {
                throw new IllegalArgumentException("Invalid server assignment authorization");
            }
            responsibleUsers = Set.copyOf(responsibleUsers);
            allowedHolders = Set.copyOf(allowedHolders);
        }
    }
}
