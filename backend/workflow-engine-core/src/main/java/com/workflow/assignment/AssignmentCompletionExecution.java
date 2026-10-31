package com.workflow.assignment;

import com.platform.common.assignment.AssignmentAcceptedProcessState;
import com.platform.common.assignment.AssignmentCompletionRequest;
import com.workflow.component.TaskCompletionService;
import org.springframework.stereotype.Component;
import java.util.List;

/** Runs the existing business operation and freezes the resulting Engine projection before its transaction commits. */
@Component
public class AssignmentCompletionExecution {
    private final TaskCompletionService completion;
    private final AssignmentCompletionOutcomeSnapshot outcomes;
    private final AssignmentCompletionOutputCompiler outputs;
    public AssignmentCompletionExecution(TaskCompletionService completion,AssignmentCompletionOutcomeSnapshot outcomes,AssignmentCompletionOutputCompiler outputs) {
        this.completion=completion; this.outcomes=outcomes; this.outputs=outputs;
    }
    public Result complete(AssignmentCompletionRequest request) {
        try (var changes=AssignmentCompletionChanges.open(request.effect().instanceId())) {
            var before=outcomes.variablesBefore(request.effect().instanceId());
            var source=before.get(request.effect().instanceId());
            if (source==null) throw new com.platform.common.assignment.AssignmentFailure(
                    com.platform.common.assignment.AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
            request.variables().forEach((key,value)->source.set(key,com.platform.common.util.JsonUtils.getObjectMapper().valueToTree(value)));
            var variables=new java.util.LinkedHashMap<>(request.variables());
            variables.put("currentUserId",request.actor()); // Protected actors are already canonical; stale form snapshots cannot impersonate them.
            completion.completeTask(request.taskId(),request.actor(),variables,true,request.representedUser());
            var states=outcomes.capture(request.effect().instanceId());
            return new Result(states,outputs.compile(request,states,before));
        }
    }
    public record Result(List<AssignmentAcceptedProcessState> states,List<com.platform.common.assignment.AssignmentRelatedVariablePatch> patches) { }
}
