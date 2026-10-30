package com.workflow.assignment;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Notification buffer only; this transaction-local resource is never an authorization lock. */
public final class AssignmentCreationBuffer {
    private static final Object KEY = new Object();
    private final Deque<String> stack = new ArrayDeque<>();
    private final Map<String,Pending> tasks = new LinkedHashMap<>();
    private final Set<String> finished = new java.util.HashSet<>();
    private static final class Pending {
        AssignmentSourceSpec source;
        com.platform.common.assignment.AssignmentFailure failure;
        final List<Notification> notifications = new ArrayList<>();
    }
    private record Notification(String recipient, Runnable send) { }

    private AssignmentCreationBuffer() { }

    public static void begin(String taskId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Protected task creation requires a synchronized transaction");
        }
        var buffer = current();
        if (buffer == null) {
            buffer = new AssignmentCreationBuffer();
            TransactionSynchronizationManager.bindResource(KEY, buffer);
            AssignmentCreationBuffer scoped = buffer;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void suspend() { TransactionSynchronizationManager.unbindResource(KEY); }
                @Override public void resume() { TransactionSynchronizationManager.bindResource(KEY, scoped); }
                @Override public void afterCompletion(int status) { TransactionSynchronizationManager.unbindResourceIfPossible(KEY); }
            });
        }
        if (buffer.tasks.putIfAbsent(taskId, new Pending()) != null) throw new IllegalStateException("Duplicate task creation scope");
        buffer.stack.push(taskId);
    }

    public static void record(AssignmentSourceSpec source) {
        var buffer = current();
        if (buffer == null || buffer.stack.isEmpty()) return; // Legacy and ordinary source resolution have no capture scope.
        var pending = buffer.tasks.get(buffer.stack.peek());
        if (pending.source == null || priority(source.kind()) > priority(pending.source.kind())) pending.source = source;
    }

    public static AssignmentSourceSpec source(String taskId, List<String> originalUsers, boolean claimPool) {
        var buffer = require();
        var pending = buffer.tasks.get(taskId);
        if (pending == null) throw new IllegalStateException("Missing task creation source scope");
        if (pending.failure!=null) throw pending.failure;
        return pending.source == null ? AssignmentSourceSpec.fixed(originalUsers, claimPool) : pending.source;
    }
    public static void fail(String taskId,Exception cause) {
        var pending=require().tasks.get(taskId);
        if (pending==null) throw new IllegalStateException("Missing failed task creation scope");
        if (pending.failure==null) pending.failure=cause instanceof com.platform.common.assignment.AssignmentFailure typed?typed
                :new com.platform.common.assignment.AssignmentFailure(com.platform.common.assignment.AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE,cause);
    }

    public static boolean defer(String taskId, String recipient, Runnable send) {
        var buffer = current();
        if (buffer == null || !buffer.tasks.containsKey(taskId)) return false;
        buffer.tasks.get(taskId).notifications.add(new Notification(recipient, send));
        return true;
    }

    public static void finish(String taskId, Set<String> allowedOriginalReferences) {
        var buffer = require();
        var pending = buffer.tasks.remove(taskId);
        if (pending == null || !taskId.equals(buffer.stack.pop())) throw new IllegalStateException("Unbalanced task creation scope");
        buffer.finished.add(taskId);
        for (var notification : pending.notifications) {
            if (allowedOriginalReferences.contains(notification.recipient())) notification.send().run();
        }
    }

    public static boolean active() {
        var buffer = current();
        return buffer != null && !buffer.stack.isEmpty();
    }

    public static boolean active(String taskId) {
        var buffer = current();
        return buffer != null && buffer.tasks.containsKey(taskId);
    }
    public static boolean finished(String taskId) {
        var buffer=current();
        return buffer!=null && buffer.finished.contains(taskId);
    }

    private static AssignmentCreationBuffer require() {
        var buffer = current();
        if (buffer == null) throw new IllegalStateException("Missing task creation buffer");
        return buffer;
    }
    private static AssignmentCreationBuffer current() {
        return (AssignmentCreationBuffer) TransactionSynchronizationManager.getResource(KEY);
    }

    private static int priority(AssignmentSourceSpec.Kind kind) {
        return switch (kind) {
            case FIXED -> 0;
            case RESOLVER -> 1;
            case VARIABLE -> 2;
            case MANUAL -> 3;
            case ROW -> 4;
        };
    }
}
