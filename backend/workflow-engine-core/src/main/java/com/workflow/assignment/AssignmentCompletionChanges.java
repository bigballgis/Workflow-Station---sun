package com.workflow.assignment;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.LinkedHashSet;
import java.util.Set;

/** Transaction-local affected instances; a completion does not freeze every unrelated MI sibling or earlier round. */
public final class AssignmentCompletionChanges implements AutoCloseable {
    private static final Object KEY=new Object();
    private final Set<String> instances=new LinkedHashSet<>();
    private final Set<String> created=new LinkedHashSet<>();
    private boolean closed;
    private AssignmentCompletionChanges(String source) { instances.add(source); }
    public static AssignmentCompletionChanges open(String source) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.hasResource(KEY)) throw new IllegalStateException("Completion projection requires its own transaction scope");
        var scope=new AssignmentCompletionChanges(source);
        TransactionSynchronizationManager.bindResource(KEY,scope);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void suspend() { if (!scope.closed) TransactionSynchronizationManager.unbindResource(KEY); }
            @Override public void resume() { if (!scope.closed) TransactionSynchronizationManager.bindResource(KEY,scope); }
            @Override public void afterCompletion(int status) { TransactionSynchronizationManager.unbindResourceIfPossible(KEY); }
        });
        return scope;
    }
    public static void changed(String instance) {
        var scope=(AssignmentCompletionChanges)TransactionSynchronizationManager.getResource(KEY);
        if (scope!=null) scope.instances.add(instance);
    }
    public static void created(String instance) {
        var scope=(AssignmentCompletionChanges)TransactionSynchronizationManager.getResource(KEY);
        if (scope!=null) { scope.instances.add(instance); scope.created.add(instance); }
    }
    public static boolean isNew(String instance) {
        var scope=(AssignmentCompletionChanges)TransactionSynchronizationManager.getResource(KEY);
        return scope!=null && scope.created.contains(instance);
    }
    public static Set<String> instances(String source) {
        var scope=(AssignmentCompletionChanges)TransactionSynchronizationManager.getResource(KEY);
        if (scope==null || !scope.instances.contains(source)) throw new IllegalStateException("No accepted completion projection scope");
        return Set.copyOf(scope.instances);
    }
    @Override public void close() {
        closed=true;
        if (TransactionSynchronizationManager.getResource(KEY)==this) TransactionSynchronizationManager.unbindResource(KEY);
    }
}
