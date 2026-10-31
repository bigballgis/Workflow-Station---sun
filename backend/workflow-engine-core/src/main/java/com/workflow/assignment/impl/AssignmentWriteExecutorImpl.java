package com.workflow.assignment.impl;

import com.platform.common.assignment.AssignmentFailure;
import com.workflow.assignment.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** External qualification preparation precedes the case-locked transaction containing the existing business write. */
@Component
public class AssignmentWriteExecutorImpl implements AssignmentWriteExecutor {
    private final AssignmentWriteGuard guard;
    private final AssignmentTaskStateRepository states;
    private final TransactionTemplate transaction;
    @org.springframework.beans.factory.annotation.Autowired @org.springframework.context.annotation.Lazy
    private AssignmentLegacyTaskCheck legacyCheck;

    public AssignmentWriteExecutorImpl(AssignmentWriteGuard guard, AssignmentTaskStateRepository states, PlatformTransactionManager manager) {
        this.guard = guard; this.states = states;
        transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override public <T> T execute(AssignmentWriteGuard.Command command, Supplier<T> write) {
        if (states.find(command.taskId()).isEmpty()) { legacyCheck.requireLegacy(command.taskId()); return write.get(); }
        // A protected write must never suspend an already-mutating transaction and commit independently of it.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        }
        var prepared = guard.prepare(command).orElseThrow(() -> new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED));
        return transaction.execute(status -> {
            var authorized = guard.validate(prepared);
            AssignmentMutationScope.establish(authorized);
            T result = write.get();
            if (command.operation() == AssignmentMutationScope.Operation.CLAIM || command.operation() == AssignmentMutationScope.Operation.TRANSFER
                    || command.operation() == AssignmentMutationScope.Operation.ASSIGN || command.operation()==AssignmentMutationScope.Operation.REASSIGN) states.held(command.taskId(), prepared.decision().pool().source());
            if (command.operation()==AssignmentMutationScope.Operation.HANDOFF) states.handedOff(command.taskId(),command.actor(),command.target(),prepared.decision().pool().source());
            if (command.operation() == AssignmentMutationScope.Operation.UNCLAIM) states.unheld(command.taskId());
            if (command.operation() != AssignmentMutationScope.Operation.COMPLETE) states.changed(prepared.decision().caseId());
            return result;
        });
    }
}
