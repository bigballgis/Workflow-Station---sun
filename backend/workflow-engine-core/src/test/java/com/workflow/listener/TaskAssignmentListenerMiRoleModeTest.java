package com.workflow.listener;

import com.platform.common.jdbc.PostgresPhysicalTablePrimaryKeys;
import com.workflow.component.BpmnActionParser;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import com.workflow.repository.ExtendedTaskInfoRepository;
import com.workflow.service.TaskAssigneeResolver;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.event.impl.FlowableEntityEventImpl;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MI 子任务 role 模式（assigneeMode=role）分派单测。
 *
 * 覆盖 {@code MultiInstanceTaskWriter.handleRoleModeAssignment} 经 {@code TaskAssignmentListener}
 * ELEMENT_VARIABLE 入口触发的四条路径：
 * <ol>
 *   <li>role 解析出唯一用户 → {@code setAssignee} + ExtendedTaskInfo(USER)</li>
 *   <li>role 解析出多用户 → 每人 {@code addCandidateUser} + ExtendedTaskInfo(CANDIDATE_USERS)（共享认领池）</li>
 *   <li>解析空池/错 → 不落地，任务保持 CREATED</li>
 *   <li>无 BU code（行 buField 空且无 active BU）→ 不解析，任务保持 CREATED</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskAssignmentListener MI role-mode Unit Tests")
class TaskAssignmentListenerMiRoleModeTest {

    @Mock
    private TaskAssigneeResolver taskAssigneeResolver;
    @Mock
    private TaskService taskService;
    @Mock
    private RuntimeService runtimeService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private BpmnActionParser bpmnActionParser;
    @Mock
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;
    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private TaskAssignmentListener listener;

    private static final String TASK_ID = "task-mi-role-001";
    private static final String PROCESS_INSTANCE_ID = "process-role-001";
    private static final String PROCESS_DEFINITION_ID = "process-def-role-001";
    private static final String TASK_DEFINITION_KEY = "MI_UserTask_role";
    private static final String EXECUTION_ID = "execution-role-001";
    private static final String SUB_TABLE_ID = "77";
    private static final String SUB_TABLE_NAME = "fu_role_participants";
    private static final String ROLE_FIELD = "role_code";
    private static final String BU_FIELD = "bu_code";
    private static final String ASSIGNEE_FIELD = "assignee";

    @BeforeEach
    void setUp() {
        listener = new TaskAssignmentListener();
        injectMocks();
        PostgresPhysicalTablePrimaryKeys.clearCache();
        lenient().when(jdbcTemplate.query(
                contains("PRIMARY KEY"),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<String>>any(),
                eq(SUB_TABLE_NAME)))
            .thenReturn(List.of("id"));
        // initiator 解析：进程变量 initiator
        lenient().when(runtimeService.getVariable(PROCESS_INSTANCE_ID, "initiator")).thenReturn("initiator-1");
    }

    private void injectMocks() {
        try {
            injectField("taskAssigneeResolver", taskAssigneeResolver);
            injectField("taskService", taskService);
            injectField("runtimeService", runtimeService);
            injectField("repositoryService", repositoryService);
            injectField("bpmnActionParser", bpmnActionParser);
            injectField("extendedTaskInfoRepository", extendedTaskInfoRepository);
            injectField("jdbcTemplate", jdbcTemplate);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mocks", e);
        }
    }

    private void injectField(String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = TaskAssignmentListener.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(listener, value);
    }

    @Test
    @DisplayName("role 解析出唯一用户 → setAssignee + ExtendedTaskInfo(USER)")
    void singleUserRolePool_setsAssignee() {
        TaskEntity task = createMockTask();
        when(task.getAssignee()).thenReturn(null);
        when(task.getExecutionId()).thenReturn(EXECUTION_ID);
        when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(roleModeBpmn());

        Map<String, Object> currentItem = new HashMap<>();
        currentItem.put("rowId", 201L);
        currentItem.put(ROLE_FIELD, "APPROVER");
        currentItem.put(BU_FIELD, "FIN");
        when(runtimeService.getVariable(EXECUTION_ID, "currentItem")).thenReturn(currentItem);

        when(taskAssigneeResolver.resolveWithRoleIds(eq("BU_ROLE"), any(), eq("FIN"), any(), any(), eq("FIN")))
            .thenReturn(TaskAssigneeResolver.ResolveResult.builder().assignee("u-sole").build());

        listener.onEvent(createTaskCreatedEvent(task));

        verify(taskService).setAssignee(TASK_ID, "u-sole");
        verify(taskService, never()).addCandidateUser(anyString(), anyString());

        ArgumentCaptor<ExtendedTaskInfo> captor = ArgumentCaptor.forClass(ExtendedTaskInfo.class);
        verify(extendedTaskInfoRepository).save(captor.capture());
        ExtendedTaskInfo saved = captor.getValue();
        assertThat(saved.getAssignmentType()).isEqualTo(AssignmentType.USER);
        assertThat(saved.getAssignmentTarget()).isEqualTo("u-sole");
        assertThat(saved.getExtendedProperties()).contains("\"assigneeMode\":\"role\"");
        assertThat(saved.getExtendedProperties()).contains("\"businessUnitCode\":\"FIN\"");
    }

    @Test
    @DisplayName("role 解析出多用户 → addCandidateUser 每人 + ExtendedTaskInfo(CANDIDATE_USERS)")
    void multiUserRolePool_addsCandidateUsers() {
        TaskEntity task = createMockTask();
        when(task.getAssignee()).thenReturn(null);
        when(task.getExecutionId()).thenReturn(EXECUTION_ID);
        when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(roleModeBpmn());

        Map<String, Object> currentItem = new HashMap<>();
        currentItem.put("rowId", 202L);
        currentItem.put(ROLE_FIELD, "REVIEWER");
        currentItem.put(BU_FIELD, "HR");
        when(runtimeService.getVariable(EXECUTION_ID, "currentItem")).thenReturn(currentItem);

        when(taskAssigneeResolver.resolveWithRoleIds(eq("BU_ROLE"), any(), eq("HR"), any(), any(), eq("HR")))
            .thenReturn(TaskAssigneeResolver.ResolveResult.builder()
                    .candidateUsers(new ArrayList<>(Arrays.asList("u1", "u2", "u3")))
                    .requiresClaim(true)
                    .build());

        listener.onEvent(createTaskCreatedEvent(task));

        verify(taskService, never()).setAssignee(anyString(), anyString());
        verify(taskService).addCandidateUser(TASK_ID, "u1");
        verify(taskService).addCandidateUser(TASK_ID, "u2");
        verify(taskService).addCandidateUser(TASK_ID, "u3");

        ArgumentCaptor<ExtendedTaskInfo> captor = ArgumentCaptor.forClass(ExtendedTaskInfo.class);
        verify(extendedTaskInfoRepository).save(captor.capture());
        ExtendedTaskInfo saved = captor.getValue();
        assertThat(saved.getAssignmentType()).isEqualTo(AssignmentType.CANDIDATE_USERS);
        assertThat(saved.getAssignmentTarget()).isEqualTo("u1,u2,u3");
    }

    @Test
    @DisplayName("解析空池 → 任务保持 CREATED（不落地、不写 ExtendedTaskInfo）")
    void emptyRolePool_leavesTaskCreated() {
        TaskEntity task = createMockTask();
        when(task.getAssignee()).thenReturn(null);
        when(task.getExecutionId()).thenReturn(EXECUTION_ID);
        when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(roleModeBpmn());

        Map<String, Object> currentItem = new HashMap<>();
        currentItem.put("rowId", 203L);
        currentItem.put(ROLE_FIELD, "GHOST");
        currentItem.put(BU_FIELD, "FIN");
        when(runtimeService.getVariable(EXECUTION_ID, "currentItem")).thenReturn(currentItem);

        when(taskAssigneeResolver.resolveWithRoleIds(eq("BU_ROLE"), any(), eq("FIN"), any(), any(), eq("FIN")))
            .thenReturn(TaskAssigneeResolver.ResolveResult.builder().errorMessage("no users").build());

        listener.onEvent(createTaskCreatedEvent(task));

        verify(taskService, never()).setAssignee(anyString(), anyString());
        verify(taskService, never()).addCandidateUser(anyString(), anyString());
        verify(extendedTaskInfoRepository, never()).save(any());
    }

    @Test
    @DisplayName("无 BU code（行 buField 空且无 active BU）→ 任务保持 CREATED")
    void noBuCode_leavesTaskCreated() {
        TaskEntity task = createMockTask();
        when(task.getAssignee()).thenReturn(null);
        when(task.getExecutionId()).thenReturn(EXECUTION_ID);
        when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(roleModeBpmn());

        Map<String, Object> currentItem = new HashMap<>();
        currentItem.put("rowId", 204L);
        currentItem.put(ROLE_FIELD, "APPROVER");
        // 无 bu_code，且不 stub active BU → 回退取到 null
        when(runtimeService.getVariable(EXECUTION_ID, "currentItem")).thenReturn(currentItem);
        lenient().when(runtimeService.getVariable(EXECUTION_ID, "activeBusinessUnitId")).thenReturn(null);

        listener.onEvent(createTaskCreatedEvent(task));

        verify(taskService, never()).setAssignee(anyString(), anyString());
        verify(taskService, never()).addCandidateUser(anyString(), anyString());
        verify(extendedTaskInfoRepository, never()).save(any());
    }

    // ==================== Helpers ====================

    private TaskEntity createMockTask() {
        TaskEntity task = mock(TaskEntity.class);
        when(task.getId()).thenReturn(TASK_ID);
        when(task.getProcessInstanceId()).thenReturn(PROCESS_INSTANCE_ID);
        when(task.getProcessDefinitionId()).thenReturn(PROCESS_DEFINITION_ID);
        when(task.getTaskDefinitionKey()).thenReturn(TASK_DEFINITION_KEY);
        when(task.getName()).thenReturn("Role Sub Task");
        return task;
    }

    private FlowableEntityEventImpl createTaskCreatedEvent(TaskEntity task) {
        FlowableEntityEventImpl event = mock(FlowableEntityEventImpl.class);
        when(event.getType()).thenReturn(FlowableEngineEventType.TASK_CREATED);
        when(event.getEntity()).thenReturn(task);
        return event;
    }

    @Test
    @DisplayName("both 模式：行只填 assignee（无 role）→ 走 user 分支 setAssignee，不调 resolver")
    void bothMode_rowWithAssigneeOnly_setsAssigneeDirect() {
        TaskEntity task = createMockTask();
        when(task.getAssignee()).thenReturn(null);
        when(task.getExecutionId()).thenReturn(EXECUTION_ID);
        when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bothModeBpmn());

        Map<String, Object> currentItem = new HashMap<>();
        currentItem.put("rowId", 205L);
        currentItem.put(ASSIGNEE_FIELD, "u-direct");   // 只填人，不填 role
        when(runtimeService.getVariable(EXECUTION_ID, "currentItem")).thenReturn(currentItem);

        listener.onEvent(createTaskCreatedEvent(task));

        // 该行填的是人 → 逐行判定走 user 分支，直接 setAssignee，不触发 BU_ROLE 解析。
        verify(taskService).setAssignee(TASK_ID, "u-direct");
        verify(taskAssigneeResolver, never()).resolveWithRoleIds(anyString(), any(), any(), any(), any(), any());
        verify(taskService, never()).addCandidateUser(anyString(), anyString());
    }

    @Test
    @DisplayName("both 模式：行填 role（无 assignee）→ 走 role 分支 BU_ROLE 池")
    void bothMode_rowWithRoleOnly_resolvesBuRolePool() {
        TaskEntity task = createMockTask();
        when(task.getAssignee()).thenReturn(null);
        when(task.getExecutionId()).thenReturn(EXECUTION_ID);
        when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bothModeBpmn());

        Map<String, Object> currentItem = new HashMap<>();
        currentItem.put("rowId", 206L);
        currentItem.put(ROLE_FIELD, "APPROVER");   // 只填 role
        currentItem.put(BU_FIELD, "FIN");
        when(runtimeService.getVariable(EXECUTION_ID, "currentItem")).thenReturn(currentItem);

        when(taskAssigneeResolver.resolveWithRoleIds(eq("BU_ROLE"), any(), eq("FIN"), any(), any(), eq("FIN")))
            .thenReturn(TaskAssigneeResolver.ResolveResult.builder().assignee("u-sole").build());

        listener.onEvent(createTaskCreatedEvent(task));

        verify(taskService).setAssignee(TASK_ID, "u-sole");
    }

    /** both 模式：同时配 assigneeField + roleField + buField，运行时逐行二选一。 */
    private BpmnModel bothModeBpmn() {
        BpmnModel bpmnModel = new BpmnModel();
        org.flowable.bpmn.model.Process process = new org.flowable.bpmn.model.Process();
        process.setId("Process_both");

        UserTask userTask = new UserTask();
        userTask.setId(TASK_DEFINITION_KEY);
        userTask.setName("Both Sub Task");

        ExtensionElement propertiesElement = new ExtensionElement();
        propertiesElement.setName("properties");
        propertiesElement.setNamespace("http://custom.bpmn.io/schema");

        List<ExtensionElement> props = new ArrayList<>();
        props.add(property("assigneeType", "ELEMENT_VARIABLE"));
        props.add(property("assigneeMode", "both"));
        props.add(property("subTableId", SUB_TABLE_ID));
        props.add(property("subTableName", SUB_TABLE_NAME));
        props.add(property("assigneeField", ASSIGNEE_FIELD));
        props.add(property("roleField", ROLE_FIELD));
        props.add(property("buField", BU_FIELD));

        Map<String, List<ExtensionElement>> childElements = new HashMap<>();
        childElements.put("property", props);
        propertiesElement.setChildElements(childElements);

        Map<String, List<ExtensionElement>> extensionElements = new HashMap<>();
        extensionElements.put("properties", Arrays.asList(propertiesElement));
        userTask.setExtensionElements(extensionElements);

        process.addFlowElement(userTask);
        bpmnModel.addProcess(process);
        return bpmnModel;
    }

    private BpmnModel roleModeBpmn() {
        BpmnModel bpmnModel = new BpmnModel();
        org.flowable.bpmn.model.Process process = new org.flowable.bpmn.model.Process();
        process.setId("Process_role");

        UserTask userTask = new UserTask();
        userTask.setId(TASK_DEFINITION_KEY);
        userTask.setName("Role Sub Task");

        ExtensionElement propertiesElement = new ExtensionElement();
        propertiesElement.setName("properties");
        propertiesElement.setNamespace("http://custom.bpmn.io/schema");

        List<ExtensionElement> props = new ArrayList<>();
        props.add(property("assigneeType", "ELEMENT_VARIABLE"));
        props.add(property("assigneeMode", "role"));
        props.add(property("subTableId", SUB_TABLE_ID));
        props.add(property("subTableName", SUB_TABLE_NAME));
        props.add(property("roleField", ROLE_FIELD));
        props.add(property("buField", BU_FIELD));

        Map<String, List<ExtensionElement>> childElements = new HashMap<>();
        childElements.put("property", props);
        propertiesElement.setChildElements(childElements);

        Map<String, List<ExtensionElement>> extensionElements = new HashMap<>();
        extensionElements.put("properties", Arrays.asList(propertiesElement));
        userTask.setExtensionElements(extensionElements);

        process.addFlowElement(userTask);
        bpmnModel.addProcess(process);
        return bpmnModel;
    }

    private ExtensionElement property(String name, String value) {
        ExtensionElement e = new ExtensionElement();
        e.setName("property");
        e.setNamespace("http://custom.bpmn.io/schema");
        e.addAttribute(attr("name", name));
        e.addAttribute(attr("value", value));
        return e;
    }

    private ExtensionAttribute attr(String name, String value) {
        ExtensionAttribute a = new ExtensionAttribute();
        a.setName(name);
        a.setValue(value);
        return a;
    }
}
