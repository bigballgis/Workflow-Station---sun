package com.portal.component;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.security.util.SecurityContextUtils;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.TaskCompleteRequest;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Initial submission uses the same durable completion and Portal synchronization as a To Do. */
@Component
public class AssignmentInitialTaskCompletion {
    private final AssignmentCompletionOrchestrator completion;
    private final WorkflowEngineClient engine;
    public AssignmentInitialTaskCompletion(AssignmentCompletionOrchestrator completion, WorkflowEngineClient engine) {
        this.completion = completion; this.engine = engine;
    }
    public Optional<AssignmentCompletionOrchestrator.Result> complete(String task, String actor) {
        // A server-owned identity for this one initial submission, stable across retries.
        var operation = UUID.nameUUIDFromBytes(("assignment:initial-submit:" + task).getBytes(StandardCharsets.UTF_8));
        return completion.completeInitialSubmission(TaskCompleteRequest.builder().taskId(task).action("SUBMIT").operationId(operation)
                .formData(Map.of()).variables(Map.of()).build(), actor, SecurityContextUtils.getCurrentUsername().orElse(null));
    }
    public void claimIfUnassigned(Map<String, Object> task, String actor) {
        String holder = EngineTaskMapper.engineStringField(task.get("currentAssignee"));
        if (holder != null) {
            if (!holder.equals(actor)) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_EXCLUDED);
            return;
        }
        if (engine.claimTask((String) task.get("taskId"), actor).isEmpty()) {
            throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE);
        }
    }
}
