package com.workflow.controller;

import com.workflow.component.TaskManagerComponent;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricActivityInstanceQuery;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TaskHistoryAssembler Send Email history")
class TaskHistoryAssemblerSendEmailTest {

    private static final String PROCESS_INSTANCE_ID = "pi-1";
    private static final String PROCESS_DEFINITION_ID = "pd:email-1";
    private static final String SEND_EMAIL_ACTIVITY_ID = "Activity_SendEmail";
    private static final String AP_ACTIVITY_ID = "Activity_Activepieces";

    @Mock
    private HistoryService historyService;
    @Mock
    private TaskService taskService;
    @Mock
    private TaskManagerComponent taskManagerComponent;
    @Mock
    private RepositoryService repositoryService;

    @InjectMocks
    private TaskHistoryAssembler assembler;

    @BeforeEach
    void stubCommonQueries() {
        // Build activity mocks before stubbing the query list (avoid nested unfinished stubbing).
        List<HistoricActivityInstance> activities = List.of(
                completedServiceTask(SEND_EMAIL_ACTIVITY_ID, "Send Email"),
                completedServiceTask(AP_ACTIVITY_ID, "Activepieces Task"),
                completedUserTask()
        );

        HistoricActivityInstanceQuery activityQuery = mock(HistoricActivityInstanceQuery.class);
        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(activityQuery);
        when(activityQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(activityQuery);
        when(activityQuery.orderByHistoricActivityInstanceStartTime()).thenReturn(activityQuery);
        when(activityQuery.asc()).thenReturn(activityQuery);
        when(activityQuery.list()).thenReturn(activities);

        HistoricTaskInstanceQuery taskQuery = mock(HistoricTaskInstanceQuery.class);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.<HistoricTaskInstance>of());

        when(taskService.getProcessInstanceComments(PROCESS_INSTANCE_ID)).thenReturn(List.of());

        HistoricProcessInstance processInstance = mock(HistoricProcessInstance.class);
        when(processInstance.getStartUserId()).thenReturn("user-1");
        when(processInstance.getProcessDefinitionId()).thenReturn(PROCESS_DEFINITION_ID);

        HistoricProcessInstanceQuery processQuery = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processQuery);
        when(processQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(processQuery);
        when(processQuery.singleResult()).thenReturn(processInstance);

        when(taskManagerComponent.resolveUserDisplayNames(any())).thenReturn(Map.of("user-1", "Alice"));
        when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnWithSendAndApTasks());
    }

    @Test
    @DisplayName("includes completed Send Email serviceTask as SEND with operator system")
    void includesSendEmailServiceTaskAsSendBySystem() {
        List<Map<String, Object>> history = assembler.assembleProcessInstanceHistory(PROCESS_INSTANCE_ID);

        Map<String, Object> sendRow = history.stream()
                .filter(item -> SEND_EMAIL_ACTIVITY_ID.equals(item.get("activityId")))
                .findFirst()
                .orElseThrow();

        assertThat(sendRow.get("operationType")).isEqualTo("SEND");
        assertThat(sendRow.get("operatorId")).isEqualTo("system");
        assertThat(sendRow.get("operatorName")).isEqualTo("system");
        assertThat(sendRow.get("activityType")).isEqualTo("serviceTask");
        assertThat(sendRow.get("activityName")).isEqualTo("Send Email");
    }

    @Test
    @DisplayName("excludes Activepieces serviceTask from flow history")
    void excludesNonSendEmailServiceTask() {
        List<Map<String, Object>> history = assembler.assembleProcessInstanceHistory(PROCESS_INSTANCE_ID);

        Set<Object> activityIds = history.stream()
                .map(item -> item.get("activityId"))
                .collect(java.util.stream.Collectors.toSet());

        assertThat(activityIds).contains(SEND_EMAIL_ACTIVITY_ID, "Activity_UserTask");
        assertThat(activityIds).doesNotContain(AP_ACTIVITY_ID);
    }

    @Test
    @DisplayName("includes nested subprocess Send Email serviceTask as SEND")
    void includesNestedSubProcessSendEmail() {
        String nestedSendId = "Activity_NestedSendEmail";
        List<HistoricActivityInstance> activities = List.of(
                completedServiceTask(nestedSendId, "Nested Send Email"),
                completedUserTask()
        );

        HistoricActivityInstanceQuery activityQuery = mock(HistoricActivityInstanceQuery.class);
        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(activityQuery);
        when(activityQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(activityQuery);
        when(activityQuery.orderByHistoricActivityInstanceStartTime()).thenReturn(activityQuery);
        when(activityQuery.asc()).thenReturn(activityQuery);
        when(activityQuery.list()).thenReturn(activities);

        when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnWithNestedSendEmail(nestedSendId));

        List<Map<String, Object>> history = assembler.assembleProcessInstanceHistory(PROCESS_INSTANCE_ID);

        Map<String, Object> sendRow = history.stream()
                .filter(item -> nestedSendId.equals(item.get("activityId")))
                .findFirst()
                .orElseThrow();

        assertThat(sendRow.get("operationType")).isEqualTo("SEND");
    }

    @Test
    @DisplayName("omits Send Email rows when BPMN model is unavailable")
    void omitsSendEmailWhenBpmnModelMissing() {
        when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(null);

        List<Map<String, Object>> history = assembler.assembleProcessInstanceHistory(PROCESS_INSTANCE_ID);

        assertThat(history.stream().map(item -> item.get("activityId")))
                .doesNotContain(SEND_EMAIL_ACTIVITY_ID, AP_ACTIVITY_ID);
    }

    private static HistoricActivityInstance completedServiceTask(String activityId, String name) {
        HistoricActivityInstance activity = mock(HistoricActivityInstance.class);
        when(activity.getId()).thenReturn("hi-" + activityId);
        when(activity.getActivityId()).thenReturn(activityId);
        when(activity.getActivityName()).thenReturn(name);
        when(activity.getActivityType()).thenReturn("serviceTask");
        when(activity.getTaskId()).thenReturn(null);
        when(activity.getAssignee()).thenReturn(null);
        Date end = new Date();
        when(activity.getEndTime()).thenReturn(end);
        when(activity.getStartTime()).thenReturn(end);
        when(activity.getDurationInMillis()).thenReturn(120L);
        return activity;
    }

    private static HistoricActivityInstance completedUserTask() {
        HistoricActivityInstance activity = mock(HistoricActivityInstance.class);
        when(activity.getId()).thenReturn("hi-user");
        when(activity.getActivityId()).thenReturn("Activity_UserTask");
        when(activity.getActivityName()).thenReturn("Approve");
        when(activity.getActivityType()).thenReturn("userTask");
        when(activity.getTaskId()).thenReturn("task-1");
        when(activity.getAssignee()).thenReturn("user-1");
        Date end = new Date();
        when(activity.getEndTime()).thenReturn(end);
        when(activity.getStartTime()).thenReturn(end);
        when(activity.getDurationInMillis()).thenReturn(300L);
        return activity;
    }

    private static BpmnModel bpmnWithSendAndApTasks() {
        ServiceTask sendTask = new ServiceTask();
        sendTask.setId(SEND_EMAIL_ACTIVITY_ID);
        sendTask.setName("Send Email");
        sendTask.setImplementation("${sendEmailTaskDelegate}");

        ServiceTask apTask = new ServiceTask();
        apTask.setId(AP_ACTIVITY_ID);
        apTask.setName("Activepieces Task");
        apTask.setImplementation("${activepiecesTaskDelegate}");

        Process process = new Process();
        process.setId("Process_1");
        process.addFlowElement(sendTask);
        process.addFlowElement(apTask);

        BpmnModel model = new BpmnModel();
        model.addProcess(process);
        return model;
    }

    private static BpmnModel bpmnWithNestedSendEmail(String nestedSendId) {
        ServiceTask sendTask = new ServiceTask();
        sendTask.setId(nestedSendId);
        sendTask.setName("Nested Send Email");
        sendTask.setImplementation("${sendEmailTaskDelegate}");

        SubProcess subProcess = new SubProcess();
        subProcess.setId("SubProcess_Email");
        subProcess.setName("Email Step");
        subProcess.addFlowElement(sendTask);

        Process process = new Process();
        process.setId("Process_1");
        process.addFlowElement(subProcess);

        BpmnModel model = new BpmnModel();
        model.addProcess(process);
        return model;
    }
}
