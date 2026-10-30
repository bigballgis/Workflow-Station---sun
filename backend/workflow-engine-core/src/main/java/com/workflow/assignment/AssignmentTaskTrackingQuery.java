package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import com.workflow.dto.response.TaskListResult;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import java.util.HashSet;

/** Read context is independent of processing eligibility. Only a positively legacy detail may refill Portal data from Engine. */
@Component
public class AssignmentTaskTrackingQuery {
    private final AssignmentCaseRepository cases;
    private final AssignmentDefinitionPolicy definitions;
    private final AssignmentLegacyTaskCheck legacy;
    private final RuntimeService runtime;
    private final HistoryService history;
    public AssignmentTaskTrackingQuery(AssignmentCaseRepository cases,AssignmentDefinitionPolicy definitions,AssignmentLegacyTaskCheck legacy,
            @Lazy RuntimeService runtime,@Lazy HistoryService history) {
        this.cases=cases; this.definitions=definitions; this.legacy=legacy; this.runtime=runtime; this.history=history;
    }
    public boolean tracked(TaskListResult.TaskInfo task) {
        return trackedInstance(task.getProcessInstanceId(),task.getProcessDefinitionId());
    }
    public boolean trackedInstance(String instance,String definition) {
        if (instance==null) { if (definition!=null) throw unavailable(); return false; }
        var known=cases.find(instance);
        if (known.isPresent()) {
            if (!known.get().definition().equals(definition)) throw unavailable();
            return true;
        }
        if (definitions.tracked(definition)) return true;
        var current=runtime.createProcessInstanceQuery().processInstanceId(instance).singleResult();
        if (current!=null) {
            if (!definition.equals(current.getProcessDefinitionId())) throw unavailable();
            legacy.requireLegacyInstance(instance); return false;
        }
        var visited=new HashSet<String>();
        while (visited.add(instance)) {
            if (cases.find(instance).isPresent()) return true;
            var archived=history.createHistoricProcessInstanceQuery().processInstanceId(instance).singleResult();
            if (archived==null) throw unavailable();
            if (definitions.tracked(archived.getProcessDefinitionId())) return true;
            if (archived.getSuperProcessInstanceId()==null) return false;
            instance=archived.getSuperProcessInstanceId();
        }
        throw unavailable();
    }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE); }
}
