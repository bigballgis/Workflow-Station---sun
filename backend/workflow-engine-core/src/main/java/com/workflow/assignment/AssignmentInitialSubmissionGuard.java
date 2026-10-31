package com.workflow.assignment;

import com.platform.common.assignment.AssignmentCompletionRequest;
import com.platform.common.assignment.AssignmentFailure;
import com.workflow.component.BpmnActionParser;
import com.workflow.enums.AssigneeType;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Component;

/** SUBMIT is reserved for the first initiator task of a top-level round, checked under its case lock. */
@Component
public class AssignmentInitialSubmissionGuard {
    private final TaskService tasks;
    private final HistoryService history;
    private final AssignmentCaseRepository cases;
    private final AssignmentDefinitionPolicy definitions;
    private final BpmnActionParser parser;
    public AssignmentInitialSubmissionGuard(TaskService tasks, HistoryService history, AssignmentCaseRepository cases,
            AssignmentDefinitionPolicy definitions, BpmnActionParser parser) {
        this.tasks=tasks; this.history=history; this.cases=cases; this.definitions=definitions; this.parser=parser;
    }
    public void require(AssignmentCompletionRequest request) {
        var task=tasks.createTaskQuery().taskId(request.taskId()).singleResult();
        if (task==null || request.representedUser()!=null || !request.actor().equals(task.getAssignee())) throw denied();
        var registration=cases.find(task.getProcessInstanceId()).orElseThrow(this::denied);
        if (!cases.sourceState(task.getProcessInstanceId()).root()
                || !request.actor().equals(cases.origin(registration.caseId()).initiator())) throw denied();
        String definition=task.getProcessDefinitionId(), node=task.getTaskDefinitionKey();
        boolean topLevel=definitions.model(definition).getMainProcess().getFlowElements().stream()
                .anyMatch(element -> element instanceof UserTask && node.equals(element.getId()));
        if (!topLevel || AssigneeType.fromCode(parser.getUserTaskExtensionPropertyValue(definition,node,"assigneeType"))
                !=AssigneeType.PROCESS_INITIATOR
                || history.createHistoricTaskInstanceQuery().processInstanceId(task.getProcessInstanceId()).finished().count()!=0) throw denied();
    }
    private AssignmentFailure denied() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_POLICY_INVALID); }
}
