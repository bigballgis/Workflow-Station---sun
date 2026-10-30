package com.workflow.assignment;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** A reconciler may only clear the exact holder it just revalidated; it cannot appoint arbitrary users. */
public final class AssignmentProjectionScope {
    private static final Object KEY = new Object();
    private AssignmentProjectionScope() { }

    public static void releasing(String taskId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.hasResource(KEY)) throw new IllegalStateException("Missing isolated assignment projection transaction");
        TransactionSynchronizationManager.bindResource(KEY, taskId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void suspend() { TransactionSynchronizationManager.unbindResource(KEY); }
            @Override public void resume() { TransactionSynchronizationManager.bindResource(KEY, taskId); }
            @Override public void afterCompletion(int status) { TransactionSynchronizationManager.unbindResourceIfPossible(KEY); }
        });
    }

    public static boolean permitsRelease(String taskId, String newHolder) {
        return newHolder == null && taskId.equals(TransactionSynchronizationManager.getResource(KEY));
    }
}
