package com.workflow.assignment;

import com.platform.common.assignment.AssignmentDelegationSnapshot;
import com.platform.common.assignment.AssignmentFailure;
import com.workflow.client.AdminCenterClient;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Objects;

/** Single-task delegates and Portal-owned standing rules share the same final authority fence. */
@Component
public class AssignmentDelegationResolver {
    private final AdminCenterClient admin;
    private final AssignmentDelegationSnapshotClient standing;
    public AssignmentDelegationResolver(AdminCenterClient admin, AssignmentDelegationSnapshotClient standing) {
        this.admin=admin; this.standing=standing;
    }
    public Grant resolve(Task task,AssignmentWriteGuard.Command command,AssignmentWriteGuard.Holding holding,
            AssignmentCaseEvaluator.Decision decision) {
        Instant until=decision.qualification().validUntil();
        if (Objects.equals(command.actor(),holding.holder())) {
            if (command.representedUser()!=null && !command.actor().equals(command.representedUser())) throw denied();
            return new Grant(false,until);
        }
        if (holding.holder()==null || !(command.operation()==AssignmentMutationScope.Operation.COMPLETE
                || command.operation()==AssignmentMutationScope.Operation.WRITE)) {
            if (command.representedUser()!=null) throw denied();
            return new Grant(false,until);
        }
        if (command.representedUser()!=null && !command.representedUser().equals(holding.holder())) throw denied();
        if ("BU_ROLE".equals(holding.delegatedType())) {
            if (holding.delegatedBu()==null || holding.delegatedRole()==null) throw denied();
            if (AssignmentIdentityReadScope.fresh(()->admin.getUsersByBusinessUnitAndRole(
                    holding.delegatedBu(),holding.delegatedRole())).contains(command.actor())) return new Grant(true,until);
        } else if (command.actor().equals(holding.delegatedUser())) return new Grant(true,until);
        var snapshot=standing.read(new AssignmentDelegationSnapshot.Request(decision.caseId(),task.getId(),
                task.getProcessInstanceId(),task.getProcessDefinitionId(),command.actor(),holding.holder()));
        if (snapshot.authorityRevision()!=decision.qualification().authorityRevision()) {
            throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED);
        }
        return new Grant(snapshot.granted(),snapshot.validUntil().isBefore(until)?snapshot.validUntil():until);
    }
    public record Grant(boolean delegated,Instant validUntil) { }
    private AssignmentFailure denied() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_EXCLUDED); }
}
