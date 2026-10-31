package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.assignment.AssignmentReopenReceipt;
import com.workflow.dto.request.StartProcessRequest;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Ended-source data and current owner/administrator qualification are prepared before acquiring the case lock. */
@Component
public class AssignmentReopenPreparation {
    private final AssignmentCaseRepository cases;
    private final AssignmentRootQualification qualification;
    private final AssignmentIdentityClient identities;
    private final HistoryService history;
    private final RepositoryService definitions;

    public AssignmentReopenPreparation(AssignmentCaseRepository cases, AssignmentRootQualification qualification,
            AssignmentIdentityClient identities, HistoryService history, RepositoryService definitions) {
        this.cases = cases; this.qualification = qualification; this.identities = identities; this.history = history; this.definitions = definitions;
    }

    public Prepared prepare(String sourceId, AssignmentReopenReceipt.Request request) {
        var source = cases.find(sourceId).orElseThrow(() -> failure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE));
        var origin = cases.origin(source.caseId());
        var start = new StartProcessRequest(); start.setStartUserId(request.actor());
        var actor = qualification.prepare(start);
        if (!actor.actor().equals(origin.initiator())) {
            var management = identities.management(actor.actor());
            if (management.authorityRevision() != actor.authorityRevision()) throw failure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED);
            if (!management.active() || !management.systemAdministrator()) throw failure(AssignmentFailure.Reason.ASSIGNMENT_EXCLUDED);
        }
        var process = history.createHistoricProcessInstanceQuery().processInstanceId(sourceId).singleResult();
        if (process == null || process.getEndTime() == null || !source.definition().equals(process.getProcessDefinitionId())
                || !cases.sourceState(sourceId).root()) throw failure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED);
        var definition = definitions.createProcessDefinitionQuery().processDefinitionId(source.definition()).singleResult();
        if (definition == null || definition.isSuspended()) throw failure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        Map<String,Object> variables = AssignmentWorkflowValues.historic(history,java.util.Set.of(sourceId)).get(sourceId);
        for (String outcome : java.util.List.of("action", "decision", "approvalStatus", "approverComments", "currentUserId")) variables.remove(outcome);
        variables.put("initiator", origin.initiator());
        if (origin.startBuId() == null) variables.remove("activeBusinessUnitId");
        else variables.put("activeBusinessUnitId", origin.startBuId());
        return new Prepared(source, actor, variables, process.getBusinessKey(), process.getName());
    }

    public void check(Prepared prepared) { qualification.check(prepared.actor()); }
    public Map<String,Object> snapshot(String processId) {
        return AssignmentProcessVariables.read(history,processId);
    }
    private AssignmentFailure failure(AssignmentFailure.Reason reason) { return new AssignmentFailure(reason); }
    public record Prepared(AssignmentCaseRegistry.Registration source, AssignmentRootQualification.Prepared actor,
            Map<String,Object> variables, String businessKey, String name) { }
}
