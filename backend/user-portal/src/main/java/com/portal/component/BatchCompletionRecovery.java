package com.portal.component;

import com.portal.client.AssignmentOperationClient;
import com.portal.dto.BatchCompleteRequest;
import com.portal.dto.TaskCompleteRequest;
import com.portal.dto.TaskCompletionResult;
import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.function.Supplier;

/** Receipt replay precedes live task/action reads, including a completion racing those reads. */
@Component
public class BatchCompletionRecovery {
    private final AssignmentOperationClient operations;
    private final TaskCompletionEndpoint endpoint;
    public BatchCompletionRecovery(AssignmentOperationClient operations,TaskCompletionEndpoint endpoint) {
        this.operations=operations; this.endpoint=endpoint;
    }
    public TaskCompletionResult run(String task,BatchCompleteRequest batch,String actor,String username,
            Supplier<TaskCompletionResult> firstAttempt) {
        var replay=replay(task,batch,actor,username);
        if (replay.isPresent()) return replay.get();
        try { return firstAttempt.get(); }
        catch (RuntimeException failure) {
            var concurrent=replay(task,batch,actor,username);
            if (concurrent.isPresent()) return concurrent.get();
            throw failure;
        }
    }
    private Optional<TaskCompletionResult> replay(String task,BatchCompleteRequest batch,String actor,String username) {
        var operation=batch.operationIds().get(task);
        if (operation==null) return Optional.empty(); // Legacy clients do not carry protected completion identities.
        return operations.find(operation).map(receipt -> {
            if (!java.util.Set.of("APPROVE","REJECT").contains(receipt.operation())) {
                throw new com.platform.common.assignment.AssignmentFailure(
                        com.platform.common.assignment.AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
            }
            return endpoint.complete(request(task,batch,receipt.operation()),actor,username);
        });
    }
    public TaskCompletionResult complete(String task,BatchCompleteRequest batch,String action,String actor,String username) {
        return endpoint.complete(request(task,batch,action),actor,username);
    }
    private TaskCompleteRequest request(String task,BatchCompleteRequest batch,String action) {
        String comment=batch.comment()==null || batch.comment().isBlank()?null:batch.comment().trim();
        return TaskCompleteRequest.builder().taskId(task).operationId(batch.operationIds().get(task))
                .action(action).actionId(batch.actionId()).comment(comment).build();
    }
}
