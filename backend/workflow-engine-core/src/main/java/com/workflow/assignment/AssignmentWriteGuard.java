package com.workflow.assignment;

import java.util.Optional;

public interface AssignmentWriteGuard {
    Optional<Prepared> prepare(Command command);
    Optional<Prepared> prepareHandoff(String task,String actor);
    AssignmentMutationScope.Authorized validate(Prepared prepared);

    record Command(String taskId, AssignmentMutationScope.Operation operation, String actor, String representedUser,
            String target) { }
    record Holding(String holder, String delegatedUser, String delegatedType, String delegatedBu, String delegatedRole) { }
    record Prepared(Command command, AssignmentCaseEvaluator.Decision decision, Holding holding, boolean verifiedDelegate) { }
}
