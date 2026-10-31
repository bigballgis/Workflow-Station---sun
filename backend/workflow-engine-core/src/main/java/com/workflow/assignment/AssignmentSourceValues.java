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
        var snapshot = snapshot(task,fields,selector(task));
        return snapshot.state()==AssignmentSourceSnapshot.State.INITIAL ? runtimeRow(task) : values(snapshot.values());
    }
    private AssignmentSourceSnapshot.Row selector(Task task) {
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
        return new AssignmentSourceSnapshot.Row(store,key);
    }
    /** One batched Portal RPC per bounded slice, limited to dynamic presentation source fields. */
    public Map<String,Map<String,Object>> presentationSources(Map<Task,AssignmentTaskStateRepository.State> selected) {
        var pending=new java.util.ArrayList<Presentation>();
        var epochs=new java.util.LinkedHashMap<java.util.UUID,Epoch>();
        for (var entry:selected.entrySet()) {
            var task=entry.getKey(); var state=entry.getValue();
            if (state==null || !state.active() || !task.getId().equals(state.taskId())
                    || !task.getProcessInstanceId().equals(state.instance()) || !task.getProcessDefinitionId().equals(state.definition())
                    || !task.getTaskDefinitionKey().equals(state.node())) throw unavailable();
            var source=state.sourceSpec();
            if (source==null) throw unavailable();
            if (source.kind()!=AssignmentSourceSpec.Kind.ROW && source.kind()!=AssignmentSourceSpec.Kind.MANUAL) continue;
            var fields=source.kind()==AssignmentSourceSpec.Kind.ROW
                    ? java.util.stream.Stream.of(source.assigneeField(),source.roleField(),source.buField())
                        .filter(field->field!=null && !field.isBlank()).distinct().toList()
                    : List.of(manualField(source.manualUserVariable(),"manualAssignee_",task),
                        manualField(source.manualBuVariable(),"manualAssignBu_",task),
                        manualField(source.manualRoleVariable(),"manualAssignRole_",task)).stream().distinct().toList();
            if (fields.isEmpty()) throw unavailable();
            epochs.computeIfAbsent(state.caseId(),id->epoch(id));
            pending.add(new Presentation(task,new AssignmentSourceSnapshot.Request(state.caseId(),state.instance(),state.definition(),fields,
                    source.kind()==AssignmentSourceSpec.Kind.ROW?selector(task):null)));
        }
        var result=new java.util.LinkedHashMap<String,Map<String,Object>>();
        for (int offset=0;offset<pending.size();offset+=1000) {
            var batch=pending.subList(offset,Math.min(offset+1000,pending.size()));
            var snapshots=portal.readMany(batch.stream().map(Presentation::request).toList());
            if (snapshots.size()!=batch.size()) throw unavailable();
            for (int index=0;index<batch.size();index++) {
                var item=batch.get(index); var snapshot=snapshots.get(index); var expected=epochs.get(item.request().caseId());
                if (snapshot.caseRevision()!=expected.caseRevision() || snapshot.portalRevision()!=expected.portalRevision()) throw changed();
                var source=snapshot.state()==AssignmentSourceSnapshot.State.INITIAL
                        ? item.request().row()==null?runtime.getVariables(item.task().getProcessInstanceId()):runtimeRow(item.task())
                        : values(snapshot.values());
                var projected=new java.util.LinkedHashMap<String,Object>();
                for (String field:item.request().fields()) if (source.containsKey(field)) projected.put(field,source.get(field));
                if (result.put(item.task().getId(),java.util.Collections.unmodifiableMap(projected))!=null) throw unavailable();
            }
        }
        epochs.forEach((id,expected)->{if (!expected.equals(epoch(id))) throw changed();});
        return java.util.Collections.unmodifiableMap(result);
    }
    private Epoch epoch(java.util.UUID id) { return new Epoch(states.caseContext(id).revision(),states.portalRevision(id)); }
    private String manualField(String field,String prefix,Task task) { return field==null || field.isBlank()?prefix+task.getTaskDefinitionKey():field; }
    private record Epoch(long caseRevision,long portalRevision) { }
    private record Presentation(Task task,AssignmentSourceSnapshot.Request request) { }
    private AssignmentFailure changed() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED); }
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
