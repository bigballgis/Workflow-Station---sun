package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.util.JsonUtils;
import com.workflow.util.BpmnExtensionUtils;
import org.flowable.bpmn.model.CallActivity;
import java.util.List;

/** Server compiler records the entire transitive graph, including unprotected called definitions. */
final class AssignmentFrozenDefinitions {
    private AssignmentFrozenDefinitions() { }
    static List<String> read(org.flowable.bpmn.model.Process process) {
        String value=BpmnExtensionUtils.getExtensionProperty(process,AssignmentDeploymentCompiler.DEPENDENCIES);
        if (value==null && process.findFlowElementsOfType(CallActivity.class,true).isEmpty()) return List.of();
        try {
            var node=JsonUtils.getObjectMapper().readTree(value);
            if (node==null || !node.isArray()) throw unavailable();
            var ids=new java.util.LinkedHashSet<String>();
            for (var id:node) {
                if (!id.isTextual() || id.textValue().isBlank() || id.textValue().length()>255) throw unavailable();
                ids.add(id.textValue());
            }
            return List.copyOf(ids);
        } catch (java.io.IOException | IllegalArgumentException invalid) { throw unavailable(); }
    }
    private static AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE); }
}
