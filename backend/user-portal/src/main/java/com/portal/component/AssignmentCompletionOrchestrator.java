package com.portal.component;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.platform.common.assignment.*;
import com.platform.common.util.JsonUtils;
import com.portal.client.AssignmentOperationClient;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.TaskCompleteRequest;
import com.portal.dto.TaskInfo;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Optional;
import java.util.UUID;

/** No outer transaction: remote acceptance and the local receipt application have separate commit boundaries. */
@Component
public class AssignmentCompletionOrchestrator {
    private final WorkflowEngineClient tasks;
    private final AssignmentPortalWriteGuard guard;
    private final AssignmentCompletionEffectBuilder effects;
    private final AssignmentOperationClient operations;
    private final AssignmentPortalSync sync;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(AssignmentCompletionOrchestrator.class);
    public AssignmentCompletionOrchestrator(WorkflowEngineClient tasks,AssignmentPortalWriteGuard guard,
            AssignmentCompletionEffectBuilder effects,AssignmentOperationClient operations,AssignmentPortalSync sync) {
        this.tasks=tasks; this.guard=guard; this.effects=effects; this.operations=operations; this.sync=sync;
    }
    public Optional<Result> complete(TaskCompleteRequest request,String actor,String username) {
        if ("SUBMIT".equals(request.getAction())) throw failure(AssignmentFailure.Reason.ASSIGNMENT_POLICY_INVALID);
        return completeRequest(request,actor,username);
    }
    Optional<Result> completeInitialSubmission(TaskCompleteRequest request,String actor,String username) {
        if (!"SUBMIT".equals(request.getAction())) throw failure(AssignmentFailure.Reason.ASSIGNMENT_POLICY_INVALID);
        return completeRequest(request,actor,username);
    }
    private Optional<Result> completeRequest(TaskCompleteRequest request,String actor,String username) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw failure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        if (!("APPROVE".equals(request.getAction()) || "REJECT".equals(request.getAction()) || "SUBMIT".equals(request.getAction()))) return Optional.empty();
        String digest=clientDigest(request);
        if (request.getOperationId()!=null) {
            var accepted=operations.find(request.getOperationId());
            if (accepted.isPresent()) return Optional.of(resume(request,actor,digest,accepted.get()));
        }
        try { return prepareAndComplete(request,actor,username,digest); }
        catch (RuntimeException rejected) {
            if (request.getOperationId()!=null) {
                var concurrent=operations.find(request.getOperationId());
                if (concurrent.isPresent()) return Optional.of(resume(request,actor,digest,concurrent.get()));
            }
            throw rejected;
        }
    }
    private Optional<Result> prepareAndComplete(TaskCompleteRequest request,String actor,String username,String digest) {
        TaskInfo task=tasks.getTaskById(request.getTaskId()).map(com.portal.util.WorkflowEnginePayloadHelper::singleTaskFromPayload)
                .map(EngineTaskMapper::convertMapToTaskInfo)
                .orElseThrow(()->failure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED));
        if (!request.getTaskId().equals(task.getTaskId()) || task.getProcessInstanceId()==null) throw failure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        var command=AssignmentPortalWriteGuard.Request.of(request.getTaskId(),actor,"COMPLETE",request,task.getProcessInstanceId());
        var decision=guard.prepare(command);
        if (!decision.tracked()) return Optional.empty();
        com.portal.security.AssignmentPortalActor.require(actor);
        if (request.getOperationId()==null) throw failure(AssignmentFailure.Reason.ASSIGNMENT_OPERATION_CONFLICT);
        var prepared=effects.prepare(task,request,actor,username);
        var effect=effects.build(prepared,digest);
        var receipt=accept(new AssignmentCompletionRequest(request.getOperationId(),request.getTaskId(),actor,
                prepared.representedUser(),request.getAction(),prepared.engineVariables(),decision.portalRevision(),effect));
        return Optional.of(resume(request,actor,digest,receipt));
    }
    public Status status(UUID operation,String actor) {
        com.portal.security.AssignmentPortalActor.require(actor);
        var located=operations.find(operation);
        if (located.isEmpty()) return new Status(operation,null,"NOT_ACCEPTED"); // A dependency failure still throws; only explicit absence has this state.
        var receipt=located.get();
        if (!actor.equals(receipt.actorUserId())) throw failure(AssignmentFailure.Reason.ASSIGNMENT_EXCLUDED);
        String state=switch (receipt.state()) {
            case "APPLIED" -> "READY";
            case "PENDING_SYNC", "SYNC_CONFLICT" -> receipt.state();
            default -> throw failure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        };
        return new Status(receipt.operationId(),receipt.taskId(),state);
    }
    private AssignmentOperationReceipt accept(AssignmentCompletionRequest request) {
        try { return operations.complete(request); }
        catch (AssignmentFailure rejected) {
            if (!"ASSIGNMENT_DEPENDENCY_UNAVAILABLE".equals(rejected.getDetails().get("reason"))) throw rejected;
            var accepted=operations.find(request.operationId());
            if (accepted.isPresent()) return accepted.get();
            throw rejected;
        }
    }
    private Result resume(TaskCompleteRequest request,String actor,String digest,AssignmentOperationReceipt receipt) {
        com.portal.security.AssignmentPortalActor.require(actor);
        if (!request.getOperationId().equals(receipt.operationId()) || !request.getTaskId().equals(receipt.taskId())
                || !actor.equals(receipt.actorUserId()) || !request.getAction().equals(receipt.operation())
                || receipt.acceptedEffect()==null || !digest.equals(receipt.acceptedEffect().get("clientRequestDigest"))) {
            throw failure(AssignmentFailure.Reason.ASSIGNMENT_OPERATION_CONFLICT);
        }
        if ("SYNC_CONFLICT".equals(receipt.state())) return new Result(receipt.operationId(),"SYNC_CONFLICT",null);
        if (!("PENDING_SYNC".equals(receipt.state()) || "APPLIED".equals(receipt.state()))) throw failure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        try {
            var observation=observation(receipt);
            sync.apply(receipt);
            operations.confirm(receipt);
            return new Result(receipt.operationId(),"READY",observation);
        } catch (RuntimeException rejected) {
            boolean conflict=rejected instanceof AssignmentFailure typed && java.util.Set.of("ASSIGNMENT_SYNC_CONFLICT",
                    "ASSIGNMENT_OPERATION_CONFLICT").contains(typed.getDetails().get("reason"));
            // FALLBACK(external): Engine acceptance is durable. Report pending recovery instead of completing the task again.
            LOG.warn("Accepted completion awaiting Portal synchronization: operationId={}, conflict={}",receipt.operationId(),conflict);
            recordRecovery(receipt,conflict);
            return new Result(receipt.operationId(),conflict?"SYNC_CONFLICT":"PENDING_SYNC",null);
        }
    }
    private void recordRecovery(AssignmentOperationReceipt receipt,boolean conflict) {
        try { operations.retry(receipt,conflict); }
        catch (RuntimeException unavailable) {
            // FALLBACK(external): The original durable receipt remains recoverable if bookkeeping cannot be acknowledged.
            LOG.warn("Accepted completion recovery bookkeeping unavailable: operationId={}",receipt.operationId());
        }
    }
    private SubTableBatchInitialMiOutcome.Plan observation(AssignmentOperationReceipt receipt) {
        try {
            var effect=JsonUtils.getObjectMapper().convertValue(receipt.acceptedEffect(),AssignmentCompletionRequest.Effect.class);
            if (effect.observation().isEmpty()) return null;
            var plan=JsonUtils.getObjectMapper().convertValue(effect.observation(),SubTableBatchInitialMiOutcome.Plan.class);
            if (!effect.instanceId().equals(plan.instanceId()) || plan.definitionId()==null || plan.expected()==null) {
                throw failure(AssignmentFailure.Reason.ASSIGNMENT_SYNC_CONFLICT);
            }
            return plan;
        } catch (IllegalArgumentException malformed) { throw failure(AssignmentFailure.Reason.ASSIGNMENT_SYNC_CONFLICT); }
    }
    static String clientDigest(TaskCompleteRequest request) {
        ObjectNode payload=JsonUtils.getObjectMapper().valueToTree(request);
        payload.remove("operationId");
        return AssignmentRequestDigest.of(payload);
    }
    public record Result(UUID operationId,String state,SubTableBatchInitialMiOutcome.Plan observation) { }
    public record Status(UUID operationId,String taskId,String state) { }
    private AssignmentFailure failure(AssignmentFailure.Reason reason) { return new AssignmentFailure(reason); }
}
