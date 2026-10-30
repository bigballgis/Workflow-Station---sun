package com.workflow.assignment;

import com.platform.common.assignment.AssignmentSnapshotMetadata;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Batch root values without initializing task-form archive payloads. Archives remain stored unchanged. */
public final class AssignmentWorkflowValues {
    private AssignmentWorkflowValues() { }
    public static Map<String,Map<String,Object>> runtime(RuntimeService runtime,Set<String> instances) {
        var result=empty(instances);
        if (instances.isEmpty()) return result;
        runtime.createVariableInstanceQuery().executionIds(instances).excludeTaskVariables().excludeVariableInitialization().list()
                .forEach(variable -> {
                    if (!AssignmentSnapshotMetadata.archived(variable.getName())) {
                        result.get(variable.getExecutionId()).put(variable.getName(),variable.getValue());
                    }
                });
        return result;
    }
    public static Map<String,Map<String,Object>> historic(HistoryService history,Set<String> instances) {
        var result=empty(instances);
        if (instances.isEmpty()) return result;
        history.createHistoricVariableInstanceQuery().executionIds(instances).excludeTaskVariables().excludeVariableInitialization().list()
                .forEach(variable -> {
                    if (!AssignmentSnapshotMetadata.archived(variable.getVariableName())) {
                        result.get(variable.getExecutionId()).put(variable.getVariableName(),variable.getValue());
                    }
                });
        return result;
    }
    private static Map<String,Map<String,Object>> empty(Set<String> instances) {
        var result=new LinkedHashMap<String,Map<String,Object>>();
        instances.forEach(id->result.put(id,new LinkedHashMap<>()));
        return result;
    }
}
