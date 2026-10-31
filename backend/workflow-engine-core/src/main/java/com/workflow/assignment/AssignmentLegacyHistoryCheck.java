package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Individual cleanup must not remove a protected case's reopen evidence, including ended called instances. */
@Component
public class AssignmentLegacyHistoryCheck {
    private final HistoryService history;
    private final RuntimeService runtime;
    private final AssignmentCaseRepository cases;
    private final AssignmentDefinitionPolicy definitions;
    private final AssignmentLegacyTaskCheck legacy;
    public AssignmentLegacyHistoryCheck(@Lazy HistoryService history,@Lazy RuntimeService runtime,AssignmentCaseRepository cases,
            @Lazy AssignmentDefinitionPolicy definitions,@Lazy AssignmentLegacyTaskCheck legacy) {
        this.history=history; this.runtime=runtime; this.cases=cases; this.definitions=definitions; this.legacy=legacy;
    }
    public void requireLegacyInstance(String instance) {
        var visited=new java.util.HashSet<String>();
        while (instance!=null && visited.add(instance)) {
            if (cases.find(instance).isPresent()) throw unavailable();
            if (runtime.createProcessInstanceQuery().processInstanceId(instance).singleResult()!=null) {
                legacy.requireLegacyInstance(instance); return; // Actual caller ancestry wins over possibly delayed history.
            }
            var ended=history.createHistoricProcessInstanceQuery().processInstanceId(instance).singleResult();
            if (ended==null || ended.getEndTime()==null || ended.getProcessDefinitionId()==null
                    || definitions.tracked(ended.getProcessDefinitionId())) throw unavailable();
            instance=ended.getSuperProcessInstanceId();
            if (instance==null) return;
        }
        throw unavailable();
    }
    public void requireLegacyTask(String taskId) {
        var task=history.createHistoricTaskInstanceQuery().taskId(taskId).singleResult();
        if (task==null) throw unavailable();
        if (task.getProcessInstanceId()!=null) requireLegacyInstance(task.getProcessInstanceId());
        else if (task.getProcessDefinitionId()!=null) throw unavailable();
        // Both absent positively identify a standalone historic task, as for the existing runtime guard.
    }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE); }
}
