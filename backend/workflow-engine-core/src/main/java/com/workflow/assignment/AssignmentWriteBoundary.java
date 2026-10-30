package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import org.springframework.stereotype.Component;

/** Business collaborators cannot mutate a protected task by bypassing the facade's preparation transaction. */
@Component
public class AssignmentWriteBoundary {
    private final AssignmentTaskStateRepository states;
    @org.springframework.beans.factory.annotation.Autowired @org.springframework.context.annotation.Lazy
    private AssignmentLegacyTaskCheck legacyCheck;
    public AssignmentWriteBoundary(AssignmentTaskStateRepository states) { this.states = states; }

    /** A protected completion must include the Portal's frozen effect and durable operation UUID. */
    public void requireLegacyCompletion(String taskId) {
        if (states.find(taskId).isPresent()) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        legacyCheck.requireLegacy(taskId);
    }

    public void require(String taskId, AssignmentMutationScope.Operation operation, String actor) {
        if (states.find(taskId).isEmpty()) { legacyCheck.requireLegacy(taskId); return; }
        var scope = AssignmentMutationScope.require(taskId);
        if (scope.operation() != operation || !scope.actor().equals(actor)) {
            throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        }
    }

    /** Protected completions use the final-checked server scope, including both delegation parties. */
    public boolean authorizesComplete(org.flowable.task.api.Task task,String actor,String represented) {
        if (task==null) return false;
        if (states.find(task.getId()).isEmpty()) { legacyCheck.requireLegacy(task); return false; }
        require(task.getId(),AssignmentMutationScope.Operation.COMPLETE,actor);
        var scope=AssignmentMutationScope.require(task.getId());
        String holder=task.getAssignee();
        if (holder!=null && !scope.responsibleUsers().contains(holder)
                || represented!=null && !represented.equals(holder)) {
            throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_EXCLUDED);
        }
        return true;
    }
}
