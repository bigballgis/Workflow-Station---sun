package com.workflow.email.action;

import com.platform.messaging.event.ActionEmailRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds the variable map a post-action email is rendered against: the process instance's
 * variables (runtime while it runs, history once the Action ended it) plus the Action context.
 */
@Component
@RequiredArgsConstructor
public class ActionEmailVariableLoader {

    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    @org.springframework.beans.factory.annotation.Autowired
    private com.workflow.assignment.AssignmentPostActionSnapshotClient assignmentSnapshots;

    public Map<String, Object> load(ActionEmailRequestedEvent event) {
        String processInstanceId = event.getProcessInstanceId();
        Map<String, Object> variables = new HashMap<>();
        if (event.getAssignmentOperationId()!=null || event.getAssignmentRuleIndex()!=null) {
            if (event.getAssignmentOperationId()==null || event.getAssignmentRuleIndex()==null) throw new IllegalArgumentException("Incomplete completion mail identity");
            variables.putAll(assignmentSnapshots.read(new com.platform.common.assignment.AssignmentPostAction.SnapshotRequest(
                    event.getAssignmentOperationId(),event.getAssignmentRuleIndex(),event.getEventId(),processInstanceId,event.getOperatorId())));
        } else variables.putAll(legacyVariables(processInstanceId));
        variables.put("actionName", nullToEmpty(event.getActionName()));
        variables.put("actionType", nullToEmpty(event.getActionType()));
        variables.put("actionOperator", nullToEmpty(event.getOperatorId()));
        variables.put("actionComment", nullToEmpty(event.getComment()));
        return variables;
    }

    private Map<String,Object> legacyVariables(String processInstanceId) {
        Map<String,Object> variables=new HashMap<>();
        boolean running = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).count() > 0;
        if (running) {
            variables.putAll(runtimeService.getVariables(processInstanceId));
        } else {
            for (HistoricVariableInstance v : historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(processInstanceId).list()) {
                if (v.getTaskId() == null) {
                    variables.put(v.getVariableName(), v.getValue());
                }
            }
        }
        return variables;
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
