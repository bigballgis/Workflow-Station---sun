package com.workflow.assignment;

import com.platform.common.assignment.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.UUID;

/** Read hints use the same current qualification and short final transaction as their corresponding commands. */
@Component
public class AssignmentActionAvailabilityRead {
    private final AssignmentWriteGuard guard;
    private final AssignmentReopenPreparation reopen;
    private final AssignmentCaseRepository cases;
    private final AssignmentLegacyHistoryCheck legacy;
    private final TransactionTemplate transaction;
    public AssignmentActionAvailabilityRead(AssignmentWriteGuard guard,AssignmentReopenPreparation reopen,AssignmentCaseRepository cases,
            AssignmentLegacyHistoryCheck legacy,PlatformTransactionManager manager) {
        this.guard=guard; this.reopen=reopen; this.cases=cases; this.legacy=legacy; transaction=new TransactionTemplate(manager);
        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public AssignmentActionAvailability handoff(String task,String actor) {
        try {
            var prepared=guard.prepareHandoff(task,actor);
            if (prepared.isEmpty()) return result(task,actor,false,List.of());
            return transaction.execute(status->{
                guard.validate(prepared.get());
                return result(task,actor,true,prepared.get().decision().pool().users());
            });
        } catch (AssignmentFailure failure) { return denied(task,actor,failure); }
    }
    public AssignmentActionAvailability reopen(String source,String actor) {
        if (cases.find(source).isEmpty()) { legacy.requireLegacyInstance(source); return result(source,actor,false,List.of()); }
        try {
            var prepared=reopen.prepare(source,new AssignmentReopenReceipt.Request(UUID.randomUUID(),actor));
            return transaction.execute(status->{
                var state=cases.lockCase(prepared.source().caseId());
                reopen.check(prepared);
                boolean allowed=!state.synchronizationPending() && !cases.hasActiveRoot(prepared.source().caseId())
                        && !"ACTIVE".equals(cases.sourceState(source).state());
                return result(source,actor,allowed,List.of());
            });
        } catch (AssignmentFailure failure) { return denied(source,actor,failure); }
    }
    private AssignmentActionAvailability denied(String resource,String actor,AssignmentFailure failure) {
        if (!AssignmentFailure.Reason.ASSIGNMENT_EXCLUDED.name().equals(failure.getDetails().get("reason"))) throw failure;
        return result(resource,actor,false,List.of());
    }
    private AssignmentActionAvailability result(String resource,String actor,boolean allowed,List<String> targets) {
        return new AssignmentActionAvailability(1,resource,actor,allowed,targets);
    }
}
