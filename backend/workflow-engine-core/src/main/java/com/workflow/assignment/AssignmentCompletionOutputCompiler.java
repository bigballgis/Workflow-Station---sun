package com.workflow.assignment;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.platform.common.assignment.*;
import com.platform.common.util.JsonUtils;
import org.flowable.bpmn.model.CallActivity;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

/** Compiles declared call outputs against the original Portal baselines; stores narrow, content-checked patches only. */
@Component
public class AssignmentCompletionOutputCompiler {
    private final AssignmentTaskStateRepository positions;
    private final AssignmentDefinitionPolicy definitions;
    public AssignmentCompletionOutputCompiler(AssignmentTaskStateRepository positions,AssignmentDefinitionPolicy definitions) {
        this.positions=positions; this.definitions=definitions;
    }
    public List<AssignmentRelatedVariablePatch> compile(AssignmentCompletionRequest request,List<AssignmentAcceptedProcessState> outcome) {
        return compile(request,outcome,Map.of());
    }
    public List<AssignmentRelatedVariablePatch> compile(AssignmentCompletionRequest request,List<AssignmentAcceptedProcessState> outcome,
            Map<String,ObjectNode> engineBefore) {
        var states=outcome.stream().collect(Collectors.toMap(AssignmentAcceptedProcessState::instanceId,value->value));
        var baselines=new LinkedHashMap<String,AssignmentCompletionRequest.InstanceBaseline>();
        for (var value:request.effect().baselines()) if (baselines.putIfAbsent(value.instanceId(),value)!=null) throw changed();
        var completed=outcome.stream().filter(value->value.parentInstanceId()!=null && "COMPLETED".equals(value.state())).toList();
        var registered=completed.isEmpty()?Map.<String,AssignmentTaskStateRepository.RoundInstance>of():positions.roundPositions(request.effect().instanceId()).stream()
                .collect(Collectors.toMap(AssignmentTaskStateRepository.RoundInstance::instance,value->value));
        var before=new LinkedHashMap<String,ObjectNode>(); var after=new LinkedHashMap<String,ObjectNode>();
        for (var child:completed) {
            var parent=states.get(child.parentInstanceId());
            if (parent==null) throw changed();
            if (!baselines.containsKey(parent.instanceId()) && AssignmentCompletionChanges.isNew(parent.instanceId())) continue;
            ObjectNode initial=before.computeIfAbsent(parent.instanceId(),id->baseline(request,parent,baselines.get(id)));
            ObjectNode accepted=after.computeIfAbsent(parent.instanceId(),id->initial.deepCopy());
            var node=registered.get(child.instanceId());
            if (node==null || node.callActivity()==null || !(definitions.model(parent.definitionId()).getFlowElement(node.callActivity()) instanceof CallActivity call)) throw changed();
            apply(AssignmentCallOutputMapping.read(call),node,child,parent,accepted);
        }
        for (var entry:engineBefore.entrySet()) {
            var state=states.get(entry.getKey());
            if (state==null) throw changed();
            ObjectNode initial=before.computeIfAbsent(state.instanceId(),id->baseline(request,state,baselines.get(id)));
            ObjectNode accepted=after.computeIfAbsent(state.instanceId(),id->initial.deepCopy());
            AssignmentTransitionVariableChanges.apply(entry.getValue(),state.variables(),accepted);
        }
        return after.entrySet().stream().map(value->new AssignmentRelatedVariablePatch(value.getKey(),states.get(value.getKey()).definitionId(),
                AssignmentVariablePatch.between(before.get(value.getKey()),value.getValue())))
                .filter(value->!value.variablePatch().isEmpty()).toList();
    }
    private ObjectNode baseline(AssignmentCompletionRequest request,AssignmentAcceptedProcessState parent,AssignmentCompletionRequest.InstanceBaseline original) {
        if (original==null || !original.definitionId().equals(parent.definitionId()) || !(original.values() instanceof ObjectNode values)) throw changed();
        return parent.instanceId().equals(request.effect().instanceId())?AssignmentVariablePatch.apply(values,request.effect().variablePatch()):values.deepCopy();
    }
    private void apply(AssignmentCallOutputMapping.Plan plan,AssignmentTaskStateRepository.RoundInstance position,
            AssignmentAcceptedProcessState child,AssignmentAcceptedProcessState parent,ObjectNode target) {
        ObjectNode frozen=JsonUtils.getObjectMapper().valueToTree(parent.variables());
        if (!"CANCELLED".equals(parent.state())) for (String field:plan.nativeTargets()) if (frozen.has(field)) target.set(field,frozen.get(field).deepCopy());
        var selected=plan.rows().stream().filter(mapping->child.variables().containsKey(mapping.from()))
                .map(mapping->new AssignmentCallRowOutputs.Mapping(mapping.to(),mapping.to())).toList();
        if (selected.isEmpty()) return;
        if (!(target.get("__subTables__") instanceof ObjectNode tables) || plan.rowStore()==null) throw changed();
        try {
            var returned=AssignmentCallRowOutputs.read(frozen.path("__subTables__").path(plan.rowStore()),position.row());
            tables.set(plan.rowStore(),AssignmentCallRowOutputs.apply(tables.get(plan.rowStore()),position.row(),selected,returned));
        } catch (IllegalArgumentException malformed) { throw changed(); }
    }
    private AssignmentFailure changed() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED); }
}
