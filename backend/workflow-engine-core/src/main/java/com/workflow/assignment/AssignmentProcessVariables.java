package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import org.flowable.engine.HistoryService;
import java.util.LinkedHashMap;
import java.util.Map;

/** Root execution values only, including automatic start/end changes and excluding task-local inputs. */
public final class AssignmentProcessVariables {
    private AssignmentProcessVariables() { }
    public static Map<String,Object> read(HistoryService history,String processId) {
        if (history.createHistoricProcessInstanceQuery().processInstanceId(processId).singleResult()==null) {
            throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        }
        Map<String,Object> variables=new LinkedHashMap<>();
        history.createHistoricVariableInstanceQuery().processInstanceId(processId).executionId(processId).excludeTaskVariables().list()
                .forEach(variable->variables.put(variable.getVariableName(),variable.getValue()));
        return variables;
    }
}
