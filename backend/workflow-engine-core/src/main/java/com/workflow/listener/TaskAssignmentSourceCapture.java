package com.workflow.listener;

import com.platform.common.recordnote.SubTaskAssignmentMode;
import com.workflow.assignment.AssignmentCreationBuffer;
import com.workflow.assignment.AssignmentSourceSpec;
import com.workflow.component.BpmnActionParser;
import org.flowable.task.api.Task;

/** Captures the configured source even when Flowable evaluated the native MI assignee before TASK_CREATED. */
final class TaskAssignmentSourceCapture {
    private TaskAssignmentSourceCapture() { }
    static com.workflow.assignment.AssignmentTaskStateRepository.CaseContext context(Task task,com.workflow.assignment.AssignmentTaskStateRepository states) {
        if (!AssignmentCreationBuffer.active(task.getId())) return null; // The original listener also serves positively untracked tasks.
        var state=states.find(task.getId()).orElseThrow(()->new com.platform.common.assignment.AssignmentFailure(
                com.platform.common.assignment.AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE));
        var context=states.caseContext(state.caseId());
        if (context.initiator()==null || context.initiator().isBlank()) throw new com.platform.common.assignment.AssignmentFailure(
                com.platform.common.assignment.AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        return context;
    }
    static boolean preassigned(BpmnActionParser parser,Task task) {
        if (!AssignmentCreationBuffer.active(task.getId())) return true;
        String definition=task.getProcessDefinitionId(), node=task.getTaskDefinitionKey();
        String type=parser.getUserTaskExtensionPropertyValue(definition,node,"assigneeType");
        var kind=com.workflow.enums.AssigneeType.fromCode(type);
        if (kind!=null && (kind.requiresAnchorUserId() || kind==com.workflow.enums.AssigneeType.BU_ROLE)) return false;
        if (kind==com.workflow.enums.AssigneeType.PROCESS_INITIATOR) {
            AssignmentCreationBuffer.record(AssignmentSourceSpec.resolver(new AssignmentSourceSpec.Resolver(type,java.util.List.of(),null,null,null,null)));
        } else if (kind==com.workflow.enums.AssigneeType.ASSIGNEE_FROM_VARIABLE) {
            String variable=parser.getUserTaskExtensionPropertyValue(definition,node,"assigneeVariable");
            if (variable==null || variable.isBlank()) variable=parser.getUserTaskExtensionPropertyValue(definition,node,"assigneeValue");
            AssignmentCreationBuffer.record(AssignmentSourceSpec.variable(variable));
        } else if (kind==com.workflow.enums.AssigneeType.MANUAL_ASSIGN) {
            AssignmentCreationBuffer.record(AssignmentSourceSpec.manual(
                    parser.getUserTaskExtensionPropertyValue(definition,node,"manualAssignVariable"),
                    parser.getUserTaskExtensionPropertyValue(definition,node,"manualAssignBuVariable"),
                    parser.getUserTaskExtensionPropertyValue(definition,node,"manualAssignRoleVariable")));
        } else if (kind==com.workflow.enums.AssigneeType.ELEMENT_VARIABLE) row(parser.getUserTaskExtensionPropertyValue(definition,node,"assigneeField"),
                parser.getUserTaskExtensionPropertyValue(definition,node,"roleField"),
                parser.getUserTaskExtensionPropertyValue(definition,node,"buField"),
                parser.getUserTaskExtensionPropertyValue(definition,node,"assigneeMode"));
        return true;
    }
    static void row(String user,String role,String bu,String mode) {
        AssignmentCreationBuffer.record(AssignmentSourceSpec.row(SubTaskAssignmentMode.allowsUser(mode)?user:null,
                SubTaskAssignmentMode.allowsRole(mode)?role:null,bu));
    }
}
