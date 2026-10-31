package com.workflow.assignment.impl;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.assignment.AssignmentReopenReceipt;
import com.workflow.assignment.*;
import org.flowable.engine.RuntimeService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/** A new round and its frozen Portal recovery receipt are created in one Engine transaction. */
@Component
public class AssignmentReopenComponentImpl implements AssignmentReopenComponent {
    private final AssignmentCaseRegistry registry;
    private final AssignmentCaseRepository cases;
    private final AssignmentReopenPreparation preparation;
    private final RuntimeService runtime;
    private final TransactionTemplate transaction;
    @org.springframework.beans.factory.annotation.Autowired private AssignmentRuntimeSupport runtimeSupport;

    public AssignmentReopenComponentImpl(AssignmentCaseRegistry registry, AssignmentCaseRepository cases,
            AssignmentReopenPreparation preparation, RuntimeService runtime, PlatformTransactionManager manager) {
        this.registry = registry; this.cases = cases; this.preparation = preparation; this.runtime = runtime;
        transaction = new TransactionTemplate(manager); transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override public AssignmentReopenReceipt reopen(String source, AssignmentReopenReceipt.Request request) {
        if (TransactionSynchronizationManager.isActualTransactionActive() || request.operationId() == null) throw unavailable();
        var accepted=cases.reopened(request.operationId());
        if (accepted.isPresent()) return replay(source,request.actor(),accepted.get());
        runtimeSupport.require(true);
        var prepared = preparation.prepare(source, request);
        try {
            return transaction.execute(status -> {
            // Registry lookup is immutable; final role/identity qualification is checked after obtaining the source case lock.
            cases.lockCase(prepared.source().caseId());
            var repeated = cases.reopened(request.operationId());
            if (repeated.isPresent()) return replay(source, prepared.actor().actor(), repeated.get());
            var registration = registry.reopen(source);
            preparation.check(prepared);
            String previous = org.flowable.common.engine.impl.identity.Authentication.getAuthenticatedUserId();
            try {
                if (!(prepared.variables().get("initiator") instanceof String initiator) || initiator.isBlank()) throw unavailable();
                org.flowable.common.engine.impl.identity.Authentication.setAuthenticatedUserId(initiator);
                runtime.createProcessInstanceBuilder().processDefinitionId(registration.definition()).predefineProcessInstanceId(registration.instance())
                        .businessKey(prepared.businessKey()).name(prepared.name()).variables(prepared.variables()).start();
                cases.reopened(registration, request.operationId(), prepared.actor().actor(), preparation.snapshot(registration.instance()));
                return cases.reopened(request.operationId()).orElseThrow(this::unavailable);
            } finally { org.flowable.common.engine.impl.identity.Authentication.setAuthenticatedUserId(previous); }
            });
        } catch (org.springframework.dao.DataIntegrityViolationException collision) {
            var repeated = cases.reopened(request.operationId());
            if (repeated.isPresent()) return replay(source, prepared.actor().actor(), repeated.get());
            throw collision;
        }
    }

    @Override public List<AssignmentReopenReceipt> pending(int limit) {
        return cases.pendingReopens(limit).stream().map(id -> cases.reopened(id).orElseThrow(this::unavailable)).toList();
    }
    @Override public void mirrored(UUID operation) { transaction.executeWithoutResult(status -> cases.reopenMirrored(operation)); }
    @Override public void retry(UUID operation, boolean conflict) { transaction.executeWithoutResult(status -> cases.retryReopen(operation, conflict)); }

    private AssignmentReopenReceipt replay(String source, String actor, AssignmentReopenReceipt receipt) {
        if (!source.equals(receipt.sourceInstanceId()) || !actor.equals(receipt.actor())) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_OPERATION_CONFLICT);
        return receipt;
    }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE); }
}
