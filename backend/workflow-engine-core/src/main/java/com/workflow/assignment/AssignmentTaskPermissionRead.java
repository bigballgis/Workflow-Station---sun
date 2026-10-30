package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.assignment.AssignmentTaskWriteReadSnapshot;
import com.platform.common.assignment.AssignmentTaskReadSnapshot.Stamp;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Single-task informational write veto, using the same actor/nominal-holder check as actual writes. */
@Component
public class AssignmentTaskPermissionRead {
    private final AssignmentWriteGuard guard;
    private final TransactionTemplate transaction;
    public AssignmentTaskPermissionRead(AssignmentWriteGuard guard,PlatformTransactionManager manager) {
        this.guard=guard; transaction=new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public AssignmentTaskWriteReadSnapshot read(String task,String actor) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw unavailable();
        try {
            var prepared=guard.prepare(new AssignmentWriteGuard.Command(task,AssignmentMutationScope.Operation.WRITE,actor,null,null));
            if (prepared.isEmpty()) return new AssignmentTaskWriteReadSnapshot(1,false,true,"LEGACY",null);
            return transaction.execute(status->{
                guard.validate(prepared.get());
                var decision=prepared.get().decision(); var qualification=decision.qualification();
                return new AssignmentTaskWriteReadSnapshot(1,true,true,"CURRENT",
                        new Stamp(decision.caseRevision(),qualification.authorityRevision(),qualification.validUntil()));
            });
        } catch (AssignmentFailure failure) {
            // FALLBACK(ux): a denied or unavailable informational read is read-only, never legacy authorization.
            return new AssignmentTaskWriteReadSnapshot(1,null,false,(String)failure.getDetails().get("reason"),null);
        }
    }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE); }
}
