package com.workflow.assignment;

import com.platform.common.assignment.*;
import com.workflow.util.BpmnExtensionUtils;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class AssignmentSubTablePlanner {
    private final TaskService tasks;
    private final AssignmentTaskStateRepository states;
    private final AssignmentLegacyTaskCheck legacy;
    private final AssignmentDefinitionPolicy definitions;
    private final AssignmentIdentityClient identities;
    public AssignmentSubTablePlanner(TaskService tasks,AssignmentTaskStateRepository states,AssignmentLegacyTaskCheck legacy,
            AssignmentDefinitionPolicy definitions,AssignmentIdentityClient identities) {
        this.tasks=tasks; this.states=states; this.legacy=legacy; this.definitions=definitions; this.identities=identities;
    }
    public AssignmentSubTablePlan prepare(AssignmentSubTablePlan.Request request) {
        var task=tasks.createTaskQuery().taskId(request.taskId()).singleResult();
        if (task==null) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED);
        var state=states.find(task.getId());
        if (state.isEmpty()) {
            legacy.requireLegacy(task);
            return new AssignmentSubTablePlan(false,task.getId(),task.getProcessInstanceId(),task.getProcessDefinitionId(),null,null,null,null,0);
        }
        if (!state.get().active() || !state.get().definition().equals(task.getProcessDefinitionId())
                || !state.get().instance().equals(task.getProcessInstanceId())) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED);
        var node=definitions.model(task.getProcessDefinitionId()).getFlowElement(task.getTaskDefinitionKey());
        if (!(node instanceof org.flowable.bpmn.model.UserTask)) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        var snapshot=identities.resolve(List.of(new AssignmentIdentitySnapshot.Reference(request.target(),AssignmentIdentitySnapshot.Kind.ID)));
        var target=snapshot.users().get(request.target());
        if (!snapshot.missingReferences().isEmpty() || target==null || !target.active() || !request.target().equals(target.id())) {
            throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_EXCLUDED);
        }
        String table=BpmnExtensionUtils.getExtensionProperty(node,"subTableName"), field=BpmnExtensionUtils.getExtensionProperty(node,"assigneeField");
        if (table==null || table.isBlank() || field==null || field.isBlank()) {
            throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_POLICY_INVALID);
        }
        // The future row task applies its own E. Its assignee need not belong to the current pre-task's pool.
        return new AssignmentSubTablePlan(true,task.getId(),task.getProcessInstanceId(),task.getProcessDefinitionId(),table,field,
                BpmnExtensionUtils.getExtensionProperty(node,"foreignKey"),target.id(),snapshot.authorityRevision());
    }
}
