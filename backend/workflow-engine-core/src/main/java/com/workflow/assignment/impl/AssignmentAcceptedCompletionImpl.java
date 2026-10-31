package com.workflow.assignment.impl;

import com.platform.common.assignment.*;
import com.platform.common.util.JsonUtils;
import com.workflow.assignment.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/** Completion and its original accepted effect commit together; response delivery is outside that transaction. */
@Component
public class AssignmentAcceptedCompletionImpl implements AssignmentAcceptedCompletion {
    private final AssignmentWriteExecutor writes;
    private final AssignmentOperationRepository operations;
    private final AssignmentCompletionExecution completion;
    private final AssignmentTaskStateRepository states;
    private final JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired private AssignmentInitialSubmissionGuard initialSubmission;

    public AssignmentAcceptedCompletionImpl(AssignmentWriteExecutor writes, AssignmentOperationRepository operations,
            AssignmentCompletionExecution completion, AssignmentTaskStateRepository states, JdbcTemplate jdbc) {
        this.writes = writes; this.operations = operations; this.completion = completion; this.states = states; this.jdbc = jdbc;
    }

    @Override public AssignmentOperationReceipt complete(AssignmentCompletionRequest request) {
        validate(request);
        String digest = AssignmentRequestDigest.of(request);
        var accepted = operations.find(request.operationId());
        if (accepted.isPresent()) return replay(request, digest, accepted.get());
        var state = states.find(request.taskId()).orElseThrow(() -> failure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE));
        if (!state.instance().equals(request.effect().instanceId())) throw failure(AssignmentFailure.Reason.ASSIGNMENT_OPERATION_CONFLICT);
        if (request.effect().stageId()!=null && !state.node().equals(request.effect().stageId())) throw failure(AssignmentFailure.Reason.ASSIGNMENT_OPERATION_CONFLICT);
        if (operations.byTask(request.taskId()).isPresent()) throw failure(AssignmentFailure.Reason.ASSIGNMENT_OPERATION_CONFLICT);
        var command = new AssignmentWriteGuard.Command(request.taskId(), AssignmentMutationScope.Operation.COMPLETE,
                request.actor(), request.representedUser(), null);
        try {
            return writes.execute(command, () -> {
            // Recheck the idempotency key under the case lock, before touching the task or its data.
            var duplicate = operations.find(request.operationId());
            if (duplicate.isPresent()) return replay(request, digest, duplicate.get());
            if ("SUBMIT".equals(request.action())) initialSubmission.require(request);
            var fence = new AssignmentCaseFence(jdbc).locked(state.caseId());
            if (fence.portalRevision() != request.portalBaseRevision()) throw failure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED);
            var outcomes=completion.complete(request);
            Map<String,Object> effect = JsonUtils.getObjectMapper().convertValue(request.effect(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() { });
            effect.put(AssignmentAcceptedProcessState.EFFECT_KEY,JsonUtils.getObjectMapper().convertValue(outcomes.states(),
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<Map<String,Object>>>() { }));
            effect.put(AssignmentRelatedVariablePatch.EFFECT_KEY,JsonUtils.getObjectMapper().convertValue(outcomes.patches(),
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<Map<String,Object>>>() { }));
            effect.put("baselines",java.util.List.of()); // Keep only selected field expectations, not unrelated caller business data.
            return operations.accept(new AssignmentOperationRepository.AcceptedCompletion(request.operationId(), state.caseId(),
                    request.taskId(), request.actor(), request.representedUser(), request.action(), digest, request.portalBaseRevision(), effect));
            });
        } catch (AssignmentFailure | org.springframework.dao.DataIntegrityViolationException rejected) {
            // A concurrent retry can observe the task's completion before entering its own write transaction.
            var concurrent = operations.find(request.operationId());
            if (concurrent.isPresent()) return replay(request, digest, concurrent.get());
            throw rejected;
        }
    }

    private AssignmentOperationReceipt replay(AssignmentCompletionRequest request, String digest, AssignmentOperationReceipt receipt) {
        if (!receipt.taskId().equals(request.taskId()) || !receipt.actorUserId().equals(request.actor())
                || !Objects.equals(receipt.representedUserId(), request.representedUser()) || !receipt.requestDigest().equals(digest)) {
            throw failure(AssignmentFailure.Reason.ASSIGNMENT_OPERATION_CONFLICT);
        }
        return receipt;
    }

    private void validate(AssignmentCompletionRequest request) {
        if (request == null || request.operationId() == null || request.taskId() == null || request.actor() == null
                || request.variables() == null || request.effect() == null || request.portalBaseRevision() < 0
                || !("APPROVE".equals(request.action()) || "REJECT".equals(request.action()) || "SUBMIT".equals(request.action()))) {
            throw failure(AssignmentFailure.Reason.ASSIGNMENT_POLICY_INVALID);
        }
        if (!request.action().equals(request.variables().get("action"))) throw failure(AssignmentFailure.Reason.ASSIGNMENT_OPERATION_CONFLICT);
    }

    private AssignmentFailure failure(AssignmentFailure.Reason reason) { return new AssignmentFailure(reason); }
}
