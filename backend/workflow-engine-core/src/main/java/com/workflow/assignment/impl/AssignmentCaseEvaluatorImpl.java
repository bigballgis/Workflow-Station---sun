package com.workflow.assignment.impl;

import com.platform.common.assignment.AssignmentAuthorityFence;
import com.platform.common.assignment.AssignmentCaseFence;
import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.assignment.AssignmentIdentitySnapshot;
import com.workflow.assignment.*;
import org.flowable.task.api.Task;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** No qualification lock survives preparation; the case lock serializes final writes and completion history. */
@Component
public class AssignmentCaseEvaluatorImpl implements AssignmentCaseEvaluator {
    private final AssignmentDefinitionPolicy definitions;
    private final AssignmentResponsibilityRepository records;
    private final AssignmentTaskStateRepository states;
    private final AssignmentQualificationComponent qualification;
    private final JdbcTemplate jdbc;

    public AssignmentCaseEvaluatorImpl(AssignmentDefinitionPolicy definitions, AssignmentResponsibilityRepository records,
            AssignmentTaskStateRepository states, AssignmentQualificationComponent qualification, JdbcTemplate jdbc) {
        this.definitions = definitions; this.records = records; this.states = states;
        this.qualification = qualification; this.jdbc = jdbc;
    }

    @Override
    public Decision prepare(Task task, AssignmentSourceSpec source, List<AssignmentIdentitySnapshot.Reference> additional) {
        var state = states.find(task.getId()).orElseThrow(() -> failure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE));
        if (!state.active() || !state.definition().equals(task.getProcessDefinitionId())
                || !state.instance().equals(task.getProcessInstanceId()) || !state.node().equals(task.getTaskDefinitionKey())) {
            throw failure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED);
        }
        var context = states.caseContext(state.caseId());
        long portalRevision=states.portalRevision(state.caseId());
        var policy = definitions.node(state.definition(), state.node());
        var excluded = AssignmentExclusionEvaluator.excluded(policy, context.initiator(),
                states.functionUnit(state.instance()), records.effective(state.caseId()));
        var prepared = qualification.prepare(new AssignmentQualificationComponent.Request(task, source, policy,
                context, List.copyOf(additional), excluded, "BACKUP".equals(state.holderSource())));
        return new Decision(task.getId(), state.caseId(), context.revision(), policy, excluded, prepared,
                AssignmentExclusionEvaluator.filter(policy, prepared.primary(), prepared.backup(), excluded),portalRevision);
    }

    @Override
    public void checkCurrent(Decision decision) {
        var locked = new AssignmentCaseFence(jdbc).locked(decision.caseId());
        if (locked.synchronizationPending()) throw failure(AssignmentFailure.Reason.ASSIGNMENT_SYNC_PENDING);
        var task = states.find(decision.taskId()).orElseThrow(() -> failure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE));
        if (!task.active() || !task.caseId().equals(decision.caseId()) || locked.revision() != decision.caseRevision()
                || locked.portalRevision()!=decision.portalRevision()) {
            throw failure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED);
        }
        var authority = new AssignmentAuthorityFence(jdbc);
        // The committed epoch read is the authorization instant. Never retain a shared qualification lock here.
        if (authority.revision() != decision.qualification().authorityRevision()
                || !authority.currentTime().isBefore(decision.qualification().validUntil())) {
            throw failure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED);
        }
    }

    private AssignmentFailure failure(AssignmentFailure.Reason reason) { return new AssignmentFailure(reason); }
}
