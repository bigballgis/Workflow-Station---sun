package com.workflow.assignment;

import com.platform.common.assignment.AssignmentCompletionRequest;
import com.platform.common.assignment.AssignmentFailure;
import com.workflow.component.BpmnActionParser;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssignmentInitialSubmissionGuardTest {
    final TaskService tasks=mock(TaskService.class,RETURNS_DEEP_STUBS);
    final HistoryService history=mock(HistoryService.class,RETURNS_DEEP_STUBS);
    final AssignmentCaseRepository cases=mock(AssignmentCaseRepository.class);
    final AssignmentDefinitionPolicy definitions=mock(AssignmentDefinitionPolicy.class);
    final BpmnActionParser parser=mock(BpmnActionParser.class);
    final Task task=mock(Task.class);
    final org.flowable.task.api.TaskQuery taskQuery=mock(org.flowable.task.api.TaskQuery.class,RETURNS_SELF);
    final org.flowable.task.api.history.HistoricTaskInstanceQuery historicQuery=mock(org.flowable.task.api.history.HistoricTaskInstanceQuery.class,RETURNS_SELF);
    final AssignmentInitialSubmissionGuard guard=new AssignmentInitialSubmissionGuard(tasks,history,cases,definitions,parser);
    final UUID caseId=UUID.randomUUID();
    final AssignmentCompletionRequest request=new AssignmentCompletionRequest(UUID.randomUUID(),"task","actor",null,"SUBMIT",Map.of(),0,null);
    @BeforeEach void configure() {
        when(tasks.createTaskQuery()).thenReturn(taskQuery); when(taskQuery.singleResult()).thenReturn(task);
        when(history.createHistoricTaskInstanceQuery()).thenReturn(historicQuery);
        when(task.getAssignee()).thenReturn("actor"); when(task.getProcessInstanceId()).thenReturn("instance");
        when(task.getProcessDefinitionId()).thenReturn("frozen"); when(task.getTaskDefinitionKey()).thenReturn("intake");
        when(cases.find("instance")).thenReturn(Optional.of(new AssignmentCaseRegistry.Registration("instance",caseId,UUID.randomUUID(),"frozen",List.of())));
        when(cases.sourceState("instance")).thenReturn(new AssignmentCaseRepository.SourceState(true,"ACTIVE",null));
        when(cases.origin(caseId)).thenReturn(new AssignmentCaseRepository.Origin("actor","bu","bu-id"));
        var model=new BpmnModel(); var process=new org.flowable.bpmn.model.Process(); process.setId("process");
        var intake=new UserTask(); intake.setId("intake"); process.addFlowElement(intake); model.addProcess(process);
        when(definitions.model("frozen")).thenReturn(model);
        when(parser.getUserTaskExtensionPropertyValue("frozen","intake","assigneeType")).thenReturn("PROCESS_INITIATOR");
    }
    @Test void initialRootInitiatorCanSubmit() { assertDoesNotThrow(()->guard.require(request)); }
    @Test void ordinaryApprovalCannotUseSubmit() {
        when(parser.getUserTaskExtensionPropertyValue("frozen","intake","assigneeType")).thenReturn("BU_ROLE");
        assertThrows(AssignmentFailure.class,()->guard.require(request));
    }
    @Test void laterInitiatorTaskCannotUseSubmit() {
        when(historicQuery.count()).thenReturn(1L);
        assertThrows(AssignmentFailure.class,()->guard.require(request));
    }
    @Test void calledProcessAndAnotherActorCannotUseRootSubmit() {
        when(cases.sourceState("instance")).thenReturn(new AssignmentCaseRepository.SourceState(false,"ACTIVE","parent"));
        assertThrows(AssignmentFailure.class,()->guard.require(request));
        when(task.getAssignee()).thenReturn("another");
        assertThrows(AssignmentFailure.class,()->guard.require(request));
    }
}
