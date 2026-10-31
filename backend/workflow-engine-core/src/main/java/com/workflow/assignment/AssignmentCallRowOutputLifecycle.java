package com.workflow.assignment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.platform.common.assignment.AssignmentCallRowOutputs;
import com.platform.common.assignment.AssignmentFailure;
import org.flowable.bpmn.model.CallActivity;
import org.flowable.common.engine.api.delegate.event.*;
import org.flowable.engine.RuntimeService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import java.util.Map;

/** A protected caller sees declared row outputs before Flowable starts its synchronous successor. */
@Component
public class AssignmentCallRowOutputLifecycle implements FlowableEventListener {
    private final AssignmentCaseRepository cases;
    private final AssignmentDefinitionPolicy definitions;
    private final RuntimeService runtime;
    private final ObjectMapper json;
    public AssignmentCallRowOutputLifecycle(AssignmentCaseRepository cases,@Lazy AssignmentDefinitionPolicy definitions,
            @Lazy RuntimeService runtime,ObjectMapper json) { this.cases=cases; this.definitions=definitions; this.runtime=runtime; this.json=json; }
    @Override public void onEvent(FlowableEvent event) {
        if (event.getType()!=FlowableEngineEventType.PROCESS_COMPLETED) return;
        if (!(event instanceof FlowableEngineEvent completed)) throw unavailable();
        var child=cases.find(completed.getProcessInstanceId());
        if (child.isEmpty()) return; // Pre-contract workflows retain their existing Portal output path.
        String parentId=cases.sourceState(child.get().instance()).parent();
        if (parentId==null) return;
        var parent=cases.find(parentId).orElseThrow(this::unavailable);
        if (child.get().calls().isEmpty()) throw unavailable();
        var position=child.get().calls().get(child.get().calls().size()-1);
        Object node=position.get("node");
        if (!(node instanceof String key) || !(definitions.model(parent.definition()).getFlowElement(key) instanceof CallActivity call)) throw unavailable();
        try {
            var plan=AssignmentCallOutputMapping.read(call);
            if (plan.rows().isEmpty()) return;
            com.fasterxml.jackson.databind.JsonNode raw=json.valueToTree(runtime.getVariable(parentId,"__subTables__"));
            if (!(raw instanceof ObjectNode tables) || plan.rowStore()==null) throw unavailable();
            var updated=AssignmentCallRowOutputs.apply(tables.get(plan.rowStore()),json.valueToTree(position.get("row")),plan.rows(),
                    json.valueToTree(runtime.getVariables(child.get().instance())));
            tables.set(plan.rowStore(),updated);
            runtime.setVariable(parentId,"__subTables__",json.convertValue(tables,new TypeReference<Map<String,Object>>() { }));
        } catch (IllegalArgumentException malformed) { throw unavailable(); }
    }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE); }
    @Override public boolean isFailOnException() { return true; }
    @Override public boolean isFireOnTransactionLifecycleEvent() { return false; }
    @Override public String getOnTransaction() { return null; }
}
