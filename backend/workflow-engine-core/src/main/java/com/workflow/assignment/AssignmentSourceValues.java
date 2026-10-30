package com.workflow.assignment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.platform.common.assignment.*;
import com.platform.common.subtable.SubTableStoreKeys;
import com.platform.common.util.JsonUtils;
import com.workflow.util.MultiInstanceTaskItem;
import org.flowable.bpmn.model.Activity;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.RuntimeService;
import org.flowable.task.api.Task;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

/** Uses in-flight Engine values during creation and committed Portal-owned values during later decisions. */
@Component
public class AssignmentSourceValues {
    private final RuntimeService runtime;
    private final AssignmentDefinitionPolicy definitions;
    private final AssignmentTaskStateRepository states;
    private final AssignmentSourceSnapshotClient portal;
    public AssignmentSourceValues(@Lazy RuntimeService runtime, AssignmentDefinitionPolicy definitions,
            AssignmentTaskStateRepository states, AssignmentSourceSnapshotClient portal) {
        this.runtime=runtime; this.definitions=definitions; this.states=states; this.portal=portal;
    }
    public Map<String,Object> variables(Task task,List<String> fields) {
        if (AssignmentCreationBuffer.active(task.getId())) return runtime.getVariables(task.getProcessInstanceId());
        var snapshot = snapshot(task,fields,null);
        return snapshot.state()==AssignmentSourceSnapshot.State.INITIAL ? runtime.getVariables(task.getProcessInstanceId()) : values(snapshot.values());
    }
    public Map<String,Object> row(Task task,List<String> fields) {
        if (AssignmentCreationBuffer.active(task.getId())) return runtimeRow(task);
        FlowElement scope = definitions.model(task.getProcessDefinitionId()).getFlowElement(task.getTaskDefinitionKey());
        while (scope!=null && !(scope instanceof Activity activity && activity.getLoopCharacteristics()!=null)) scope=scope.getSubProcess();
        if (scope==null) throw unavailable();
        String store = SubTableStoreKeys.dwKey(AssignmentMiRowSource.table((Activity)scope));
        if (store==null) throw unavailable();
        JsonNode key = null;
        var ancestry = states.position(task.getId()).path("multiInstances");
        if (!ancestry.isArray()) throw unavailable();
        for (JsonNode item : ancestry) if (scope.getId().equals(item.path("scope").asText())) {
            if (key!=null) throw unavailable();
            key=item.get("row");
        }
        if (key==null || !key.isObject() || key.isEmpty()) throw unavailable();
        var snapshot = snapshot(task,fields,new AssignmentSourceSnapshot.Row(store,key));
        return snapshot.state()==AssignmentSourceSnapshot.State.INITIAL ? runtimeRow(task) : values(snapshot.values());
    }
    private AssignmentSourceSnapshot snapshot(Task task,List<String> fields,AssignmentSourceSnapshot.Row row) {
        var state = states.find(task.getId()).orElseThrow(this::unavailable);
        long revision = states.caseContext(state.caseId()).revision();
        var snapshot = portal.read(new AssignmentSourceSnapshot.Request(state.caseId(),state.instance(),state.definition(),fields,row));
        if (snapshot.caseRevision()!=revision || states.caseContext(state.caseId()).revision()!=revision
                || states.portalRevision(state.caseId())!=snapshot.portalRevision()) {
            throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED);
        }
        return snapshot;
    }
    private Map<String,Object> runtimeRow(Task task) {
        Object raw = MultiInstanceTaskItem.resolve(definitions.model(task.getProcessDefinitionId()),runtime,task);
        if (!(raw instanceof Map<?,?> row) || row.keySet().stream().anyMatch(key -> !(key instanceof String))) throw unavailable();
        @SuppressWarnings("unchecked") Map<String,Object> typed = (Map<String,Object>) row;
        return typed;
    }
    private Map<String,Object> values(JsonNode node) { return JsonUtils.getObjectMapper().convertValue(node,new TypeReference<>() { }); }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE); }
}
