package com.portal.component;

import com.portal.dto.TaskCompleteRequest;
import com.portal.dto.TaskCompletionResult;
import org.springframework.stereotype.Component;

/** Public completion orchestration has no outer transaction around Engine acceptance and Portal sync. */
@Component
public class TaskCompletionEndpoint {
    private final AssignmentCompletionOrchestrator protectedCompletion;
    private final AssignmentAcceptedObservation protectedObservation;
    private final TaskProcessComponent legacy;
    private final ActionPostEmailComponent emails;
    private final SubTableBatchAssignmentHistory history;
    public TaskCompletionEndpoint(AssignmentCompletionOrchestrator protectedCompletion,AssignmentAcceptedObservation protectedObservation,
            TaskProcessComponent legacy,ActionPostEmailComponent emails,SubTableBatchAssignmentHistory history) {
        this.protectedCompletion=protectedCompletion; this.protectedObservation=protectedObservation;
        this.legacy=legacy; this.emails=emails; this.history=history;
    }
    public TaskCompletionResult complete(TaskCompleteRequest request,String actor,String username) {
        var accepted=protectedCompletion.complete(request,actor,username);
        if (accepted.isPresent()) {
            var result=accepted.get();
            return new TaskCompletionResult(protectedObservation.observe(result,actor,request.getTaskId()),result.operationId(),result.state());
        }
        var plan=emails.runWithPostEmail(ActionPostEmailContext.builder().actionId(request.getActionId()).operation(operation(request.getAction()))
                .taskId(request.getTaskId()).operatorId(actor).comment(request.getComment()).build(),
                ()->legacy.completeTaskWithObservationPlan(request,actor,username));
        var observation=legacy.observeCompletion(plan);
        if (plan!=null) observation=history.record(plan,observation,actor,request.getTaskId());
        return observation==null?null:new TaskCompletionResult(observation);
    }
    private String operation(String action) {
        if (action==null) return ActionPostEmailContext.OP_APPROVE;
        return switch (action.trim().toUpperCase()) {
            case "REJECT" -> ActionPostEmailContext.OP_REJECT;
            case "RETURN" -> ActionPostEmailContext.OP_RETURN;
            case "DRAFT" -> ActionPostEmailContext.OP_DRAFT;
            default -> ActionPostEmailContext.OP_APPROVE;
        };
    }
}
