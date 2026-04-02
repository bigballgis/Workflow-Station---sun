package com.workflow.listener;

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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TaskAssignmentListener ELEMENT_VARIABLE 处理单元测试
 * 
 * **验证需求 4.2**: TaskAssignmentListener 从 Element_Variable 中读取 assigneeField 值并分配任务
 * **验证需求 4.5**: 处理人 ID 无效时记录异常日志并将任务状态设置为 CREATED
 * 
 * 测试场景：
 * 1. 正常分配场景：currentItem 包含有效的 assigneeId、rowId、rowVersion
 * 2. elementVariable 为 null 时的降级处理：任务保持 CREATED 状态
 * 3. 处理人 ID 无效时的降级处理：setAssignee 抛出异常，任务保持 CREATED 状态
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskAssignmentListener ELEMENT_VARIABLE Unit Tests")
class TaskAssignmentListenerElementVariableTest {
    
    @Mock
    private TaskAssigneeResolver taskAssigneeResolver;
    
    @Mock
    private TaskService taskService;
    
    @Mock
    private RuntimeService runtimeService;
    
    @Mock
    private RepositoryService repositoryService;
    
    @Mock
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;
    
    private TaskAssignmentListener listener;
    
    private static final String TASK_ID = "task-mi-001";
    private static final String PROCESS_INSTANCE_ID = "process-001";
    private static final String PROCESS_DEFINITION_ID = "process-def-001";
    private static final String TASK_DEFINITION_KEY = "MI_UserTask_45";
    private static final String EXECUTION_ID = "execution-001";
    private static final String SUB_TABLE_ID = "45";
    private static final String SUB_TABLE_NAME = "fu_participants";
    
    @BeforeEach
    void setUp() {
        listener = new TaskAssignmentListener();
        injectMocks();
    }
    
    /**
     * 使用反射注入 mock 依赖
     */
    private void injectMocks() {
        try {
            injectField("taskAssigneeResolver", taskAssigneeResolver);
            injectField("taskService", taskService);
            injectField("runtimeService", runtimeService);
            injectField("repositoryService", repositoryService);
            injectField("extendedTaskInfoRepository", extendedTaskInfoRepository);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mocks", e);
        }
    }
    
    private void injectField(String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = TaskAssignmentListener.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(listener, value);
    }
    
    @Nested
    @DisplayName("正常分配场景测试")
    class NormalAssignmentTests {
        
        @Test
        @DisplayName("应该成功分配任务并创建 ExtendedTaskInfo - 验证需求 4.2")
        void shouldAssignTaskAndCreateExtendedTaskInfo() {
            // Given: 创建包含有效 currentItem 的任务
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn(null);
            when(task.getExecutionId()).thenReturn(EXECUTION_ID);
            
            // Setup BPMN model with ELEMENT_VARIABLE
            BpmnModel bpmnModel = createBpmnModelWithElementVariable(SUB_TABLE_ID, SUB_TABLE_NAME);
            when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnModel);
            
            // Setup currentItem with valid data
            Map<String, Object> currentItem = new HashMap<>();
            currentItem.put("rowId", 101L);
            currentItem.put("assigneeId", "user-001");
            currentItem.put("rowVersion", 1L);
            when(runtimeService.getVariable(EXECUTION_ID, "currentItem")).thenReturn(currentItem);
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            
            // When: 触发任务创建事件
            listener.onEvent(event);
            
            // Then: 验证任务被分配
            verify(taskService).setAssignee(TASK_ID, "user-001");
            
            // Then: 验证 ExtendedTaskInfo 被创建
            ArgumentCaptor<ExtendedTaskInfo> captor = ArgumentCaptor.forClass(ExtendedTaskInfo.class);
            verify(extendedTaskInfoRepository).save(captor.capture());
            
            ExtendedTaskInfo savedInfo = captor.getValue();
            assertThat(savedInfo.getTaskId()).isEqualTo(TASK_ID);
            assertThat(savedInfo.getProcessInstanceId()).isEqualTo(PROCESS_INSTANCE_ID);
            assertThat(savedInfo.getAssignmentType()).isEqualTo(AssignmentType.USER);
            assertThat(savedInfo.getAssignmentTarget()).isEqualTo("user-001");
            assertThat(savedInfo.getStatus()).isEqualTo("ASSIGNED");
            
            // Then: 验证 extendedProperties 包含多实例元数据
            String extProps = savedInfo.getExtendedProperties();
            assertThat(extProps).contains("\"multiInstance\":true");
            assertThat(extProps).contains("\"subTableRowId\":101");
            assertThat(extProps).contains("\"subTableRowVersion\":1");
            assertThat(extProps).contains("\"subTableId\":\"45\"");
            assertThat(extProps).contains("\"subTableName\":\"fu_participants\"");
        }
        
        @Test
        @DisplayName("应该处理 rowId 和 rowVersion 为 Number 类型")
        void shouldHandleNumericRowIdAndRowVersion() {
            // Given
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn(null);
            when(task.getExecutionId()).thenReturn(EXECUTION_ID);
            
            BpmnModel bpmnModel = createBpmnModelWithElementVariable(SUB_TABLE_ID, SUB_TABLE_NAME);
            when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnModel);
            
            // currentItem with Integer types (common from JSON deserialization)
            Map<String, Object> currentItem = new HashMap<>();
            currentItem.put("rowId", Integer.valueOf(102));
            currentItem.put("assigneeId", "user-002");
            currentItem.put("rowVersion", Integer.valueOf(2));
            when(runtimeService.getVariable(EXECUTION_ID, "currentItem")).thenReturn(currentItem);
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            
            // When
            listener.onEvent(event);
            
            // Then
            verify(taskService).setAssignee(TASK_ID, "user-002");
            
            ArgumentCaptor<ExtendedTaskInfo> captor = ArgumentCaptor.forClass(ExtendedTaskInfo.class);
            verify(extendedTaskInfoRepository).save(captor.capture());
            
            String extProps = captor.getValue().getExtendedProperties();
            assertThat(extProps).contains("\"subTableRowId\":102");
            assertThat(extProps).contains("\"subTableRowVersion\":2");
        }
        
        @Test
        @DisplayName("应该处理 rowId 和 rowVersion 为字符串类型")
        void shouldHandleStringRowIdAndRowVersion() {
            // Given
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn(null);
            when(task.getExecutionId()).thenReturn(EXECUTION_ID);
            
            BpmnModel bpmnModel = createBpmnModelWithElementVariable(SUB_TABLE_ID, SUB_TABLE_NAME);
            when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnModel);
            
            // currentItem with String types
            Map<String, Object> currentItem = new HashMap<>();
            currentItem.put("rowId", "103");
            currentItem.put("assigneeId", "user-003");
            currentItem.put("rowVersion", "3");
            when(runtimeService.getVariable(EXECUTION_ID, "currentItem")).thenReturn(currentItem);
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            
            // When
            listener.onEvent(event);
            
            // Then
            verify(taskService).setAssignee(TASK_ID, "user-003");
            
            ArgumentCaptor<ExtendedTaskInfo> captor = ArgumentCaptor.forClass(ExtendedTaskInfo.class);
            verify(extendedTaskInfoRepository).save(captor.capture());
            
            String extProps = captor.getValue().getExtendedProperties();
            assertThat(extProps).contains("\"subTableRowId\":103");
            assertThat(extProps).contains("\"subTableRowVersion\":3");
        }
    }
    
    @Nested
    @DisplayName("elementVariable 为 null 时的降级处理测试")
    class NullElementVariableTests {
        
        @Test
        @DisplayName("当 currentItem 为 null 时，应该记录警告并保持任务状态为 CREATED")
        void shouldHandleNullCurrentItem() {
            // Given
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn(null);
            when(task.getExecutionId()).thenReturn(EXECUTION_ID);
            
            BpmnModel bpmnModel = createBpmnModelWithElementVariable(SUB_TABLE_ID, SUB_TABLE_NAME);
            when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnModel);
            
            // currentItem is null
            when(runtimeService.getVariable(EXECUTION_ID, "currentItem")).thenReturn(null);
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            
            // When
            listener.onEvent(event);
            
            // Then: 任务不应该被分配
            verify(taskService, never()).setAssignee(anyString(), anyString());
            
            // Then: ExtendedTaskInfo 不应该被创建
            verify(extendedTaskInfoRepository, never()).save(any());
        }
        
        @Test
        @DisplayName("当 currentItem 不是 Map 类型时，应该记录警告并保持任务状态为 CREATED")
        void shouldHandleNonMapCurrentItem() {
            // Given
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn(null);
            when(task.getExecutionId()).thenReturn(EXECUTION_ID);
            
            BpmnModel bpmnModel = createBpmnModelWithElementVariable(SUB_TABLE_ID, SUB_TABLE_NAME);
            when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnModel);
            
            // currentItem is not a Map
            when(runtimeService.getVariable(EXECUTION_ID, "currentItem")).thenReturn("invalid-type");
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            
            // When
            listener.onEvent(event);
            
            // Then
            verify(taskService, never()).setAssignee(anyString(), anyString());
            verify(extendedTaskInfoRepository, never()).save(any());
        }
        
        @Test
        @DisplayName("当 currentItem 中缺少 assigneeId 时，应该记录警告并保持任务状态为 CREATED")
        void shouldHandleMissingAssigneeId() {
            // Given
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn(null);
            when(task.getExecutionId()).thenReturn(EXECUTION_ID);
            
            BpmnModel bpmnModel = createBpmnModelWithElementVariable(SUB_TABLE_ID, SUB_TABLE_NAME);
            when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnModel);
            
            // currentItem without assigneeId
            Map<String, Object> currentItem = new HashMap<>();
            currentItem.put("rowId", 104L);
            currentItem.put("rowVersion", 1L);
            // assigneeId is missing
            when(runtimeService.getVariable(EXECUTION_ID, "currentItem")).thenReturn(currentItem);
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            
            // When
            listener.onEvent(event);
            
            // Then
            verify(taskService, never()).setAssignee(anyString(), anyString());
            verify(extendedTaskInfoRepository, never()).save(any());
        }
    }
    
    @Nested
    @DisplayName("处理人 ID 无效时的降级处理测试 - 验证需求 4.5")
    class InvalidAssigneeIdTests {
        
        @Test
        @DisplayName("当处理人 ID 无效时，应该记录警告并保持任务状态为 CREATED")
        void shouldHandleInvalidAssigneeId() {
            // Given
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn(null);
            when(task.getExecutionId()).thenReturn(EXECUTION_ID);
            
            BpmnModel bpmnModel = createBpmnModelWithElementVariable(SUB_TABLE_ID, SUB_TABLE_NAME);
            when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnModel);
            
            // currentItem with invalid assigneeId
            Map<String, Object> currentItem = new HashMap<>();
            currentItem.put("rowId", 105L);
            currentItem.put("assigneeId", "invalid-user-999");
            currentItem.put("rowVersion", 1L);
            when(runtimeService.getVariable(EXECUTION_ID, "currentItem")).thenReturn(currentItem);
            
            // Simulate setAssignee throwing exception (user not found)
            doThrow(new RuntimeException("User not found or disabled"))
                .when(taskService).setAssignee(TASK_ID, "invalid-user-999");
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            
            // When
            listener.onEvent(event);
            
            // Then: setAssignee 应该被尝试调用
            verify(taskService).setAssignee(TASK_ID, "invalid-user-999");
            
            // Then: ExtendedTaskInfo 不应该被创建（因为分配失败）
            verify(extendedTaskInfoRepository, never()).save(any());
        }
        
        @Test
        @DisplayName("当处理人被禁用时，应该记录警告并保持任务状态为 CREATED")
        void shouldHandleDisabledUser() {
            // Given
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn(null);
            when(task.getExecutionId()).thenReturn(EXECUTION_ID);
            
            BpmnModel bpmnModel = createBpmnModelWithElementVariable(SUB_TABLE_ID, SUB_TABLE_NAME);
            when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnModel);
            
            // currentItem with disabled user
            Map<String, Object> currentItem = new HashMap<>();
            currentItem.put("rowId", 106L);
            currentItem.put("assigneeId", "disabled-user-001");
            currentItem.put("rowVersion", 1L);
            when(runtimeService.getVariable(EXECUTION_ID, "currentItem")).thenReturn(currentItem);
            
            // Simulate setAssignee throwing exception (user disabled)
            doThrow(new RuntimeException("User is disabled"))
                .when(taskService).setAssignee(TASK_ID, "disabled-user-001");
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            
            // When
            listener.onEvent(event);
            
            // Then
            verify(taskService).setAssignee(TASK_ID, "disabled-user-001");
            verify(extendedTaskInfoRepository, never()).save(any());
        }
    }
    
    @Nested
    @DisplayName("ExtendedTaskInfo 保存失败处理测试")
    class ExtendedTaskInfoSaveFailureTests {
        
        @Test
        @DisplayName("当 ExtendedTaskInfo 保存失败时，任务分配应该仍然成功")
        void shouldContinueWhenExtendedTaskInfoSaveFails() {
            // Given
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn(null);
            when(task.getExecutionId()).thenReturn(EXECUTION_ID);
            
            BpmnModel bpmnModel = createBpmnModelWithElementVariable(SUB_TABLE_ID, SUB_TABLE_NAME);
            when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnModel);
            
            Map<String, Object> currentItem = new HashMap<>();
            currentItem.put("rowId", 107L);
            currentItem.put("assigneeId", "user-004");
            currentItem.put("rowVersion", 1L);
            when(runtimeService.getVariable(EXECUTION_ID, "currentItem")).thenReturn(currentItem);
            
            // Simulate ExtendedTaskInfo save failure
            doThrow(new RuntimeException("Database connection error"))
                .when(extendedTaskInfoRepository).save(any());
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            
            // When
            listener.onEvent(event);
            
            // Then: 任务分配应该成功（不受 ExtendedTaskInfo 保存失败影响）
            verify(taskService).setAssignee(TASK_ID, "user-004");
            
            // Then: save 应该被尝试调用
            verify(extendedTaskInfoRepository).save(any());
        }
    }
    
    @Nested
    @DisplayName("边界情况测试")
    class EdgeCaseTests {
        
        @Test
        @DisplayName("应该处理 rowId 为 null 的情况")
        void shouldHandleNullRowId() {
            // Given
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn(null);
            when(task.getExecutionId()).thenReturn(EXECUTION_ID);
            
            BpmnModel bpmnModel = createBpmnModelWithElementVariable(SUB_TABLE_ID, SUB_TABLE_NAME);
            when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnModel);
            
            // currentItem with null rowId
            Map<String, Object> currentItem = new HashMap<>();
            currentItem.put("rowId", null);
            currentItem.put("assigneeId", "user-005");
            currentItem.put("rowVersion", 1L);
            when(runtimeService.getVariable(EXECUTION_ID, "currentItem")).thenReturn(currentItem);
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            
            // When
            listener.onEvent(event);
            
            // Then: 任务应该被分配（rowId 为 null 不影响分配）
            verify(taskService).setAssignee(TASK_ID, "user-005");
            
            // Then: ExtendedTaskInfo 应该被创建（但不包含 subTableRowId）
            ArgumentCaptor<ExtendedTaskInfo> captor = ArgumentCaptor.forClass(ExtendedTaskInfo.class);
            verify(extendedTaskInfoRepository).save(captor.capture());
            
            String extProps = captor.getValue().getExtendedProperties();
            assertThat(extProps).contains("\"multiInstance\":true");
            assertThat(extProps).doesNotContain("\"subTableRowId\"");
        }
        
        @Test
        @DisplayName("应该处理 rowVersion 为 null 的情况")
        void shouldHandleNullRowVersion() {
            // Given
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn(null);
            when(task.getExecutionId()).thenReturn(EXECUTION_ID);
            
            BpmnModel bpmnModel = createBpmnModelWithElementVariable(SUB_TABLE_ID, SUB_TABLE_NAME);
            when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnModel);
            
            // currentItem with null rowVersion
            Map<String, Object> currentItem = new HashMap<>();
            currentItem.put("rowId", 108L);
            currentItem.put("assigneeId", "user-006");
            currentItem.put("rowVersion", null);
            when(runtimeService.getVariable(EXECUTION_ID, "currentItem")).thenReturn(currentItem);
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            
            // When
            listener.onEvent(event);
            
            // Then
            verify(taskService).setAssignee(TASK_ID, "user-006");
            
            ArgumentCaptor<ExtendedTaskInfo> captor = ArgumentCaptor.forClass(ExtendedTaskInfo.class);
            verify(extendedTaskInfoRepository).save(captor.capture());
            
            String extProps = captor.getValue().getExtendedProperties();
            assertThat(extProps).contains("\"multiInstance\":true");
            assertThat(extProps).contains("\"subTableRowId\":108");
            assertThat(extProps).doesNotContain("\"subTableRowVersion\"");
        }
        
        @Test
        @DisplayName("应该处理无效的 rowId 格式")
        void shouldHandleInvalidRowIdFormat() {
            // Given
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn(null);
            when(task.getExecutionId()).thenReturn(EXECUTION_ID);
            
            BpmnModel bpmnModel = createBpmnModelWithElementVariable(SUB_TABLE_ID, SUB_TABLE_NAME);
            when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnModel);
            
            // currentItem with invalid rowId format
            Map<String, Object> currentItem = new HashMap<>();
            currentItem.put("rowId", "invalid-number");
            currentItem.put("assigneeId", "user-007");
            currentItem.put("rowVersion", 1L);
            when(runtimeService.getVariable(EXECUTION_ID, "currentItem")).thenReturn(currentItem);
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            
            // When
            listener.onEvent(event);
            
            // Then: 任务应该被分配（无效的 rowId 格式不影响分配）
            verify(taskService).setAssignee(TASK_ID, "user-007");
            
            // Then: ExtendedTaskInfo 应该被创建（但不包含 subTableRowId）
            ArgumentCaptor<ExtendedTaskInfo> captor = ArgumentCaptor.forClass(ExtendedTaskInfo.class);
            verify(extendedTaskInfoRepository).save(captor.capture());
            
            String extProps = captor.getValue().getExtendedProperties();
            assertThat(extProps).contains("\"multiInstance\":true");
            assertThat(extProps).doesNotContain("\"subTableRowId\"");
        }
    }
    
    // ==================== Helper Methods ====================
    
    private TaskEntity createMockTask() {
        TaskEntity task = mock(TaskEntity.class);
        when(task.getId()).thenReturn(TASK_ID);
        when(task.getProcessInstanceId()).thenReturn(PROCESS_INSTANCE_ID);
        when(task.getProcessDefinitionId()).thenReturn(PROCESS_DEFINITION_ID);
        when(task.getTaskDefinitionKey()).thenReturn(TASK_DEFINITION_KEY);
        when(task.getName()).thenReturn("Fill Participant Info");
        return task;
    }
    
    private FlowableEntityEventImpl createTaskCreatedEvent(TaskEntity task) {
        FlowableEntityEventImpl event = mock(FlowableEntityEventImpl.class);
        when(event.getType()).thenReturn(FlowableEngineEventType.TASK_CREATED);
        when(event.getEntity()).thenReturn(task);
        return event;
    }
    
    private BpmnModel createBpmnModelWithElementVariable(String subTableId, String subTableName) {
        BpmnModel bpmnModel = new BpmnModel();
        org.flowable.bpmn.model.Process process = new org.flowable.bpmn.model.Process();
        process.setId("Process_1");
        
        UserTask userTask = new UserTask();
        userTask.setId(TASK_DEFINITION_KEY);
        userTask.setName("Fill Participant Info");
        
        // Create extension elements
        Map<String, List<ExtensionElement>> extensionElements = new HashMap<>();
        
        ExtensionElement propertiesElement = new ExtensionElement();
        propertiesElement.setName("properties");
        propertiesElement.setNamespace("http://custom.bpmn.io/schema");
        
        Map<String, List<ExtensionElement>> childElements = new HashMap<>();
        List<ExtensionElement> propertyElements = new ArrayList<>();
        
        // Add assigneeType property
        ExtensionElement assigneeTypeProperty = new ExtensionElement();
        assigneeTypeProperty.setName("property");
        assigneeTypeProperty.setNamespace("http://custom.bpmn.io/schema");
        assigneeTypeProperty.addAttribute(createAttribute("name", "assigneeType"));
        assigneeTypeProperty.addAttribute(createAttribute("value", "ELEMENT_VARIABLE"));
        propertyElements.add(assigneeTypeProperty);
        
        // Add subTableId property
        if (subTableId != null) {
            ExtensionElement subTableIdProperty = new ExtensionElement();
            subTableIdProperty.setName("property");
            subTableIdProperty.setNamespace("http://custom.bpmn.io/schema");
            subTableIdProperty.addAttribute(createAttribute("name", "subTableId"));
            subTableIdProperty.addAttribute(createAttribute("value", subTableId));
            propertyElements.add(subTableIdProperty);
        }
        
        // Add subTableName property
        if (subTableName != null) {
            ExtensionElement subTableNameProperty = new ExtensionElement();
            subTableNameProperty.setName("property");
            subTableNameProperty.setNamespace("http://custom.bpmn.io/schema");
            subTableNameProperty.addAttribute(createAttribute("name", "subTableName"));
            subTableNameProperty.addAttribute(createAttribute("value", subTableName));
            propertyElements.add(subTableNameProperty);
        }
        
        childElements.put("property", propertyElements);
        propertiesElement.setChildElements(childElements);
        
        extensionElements.put("properties", Arrays.asList(propertiesElement));
        userTask.setExtensionElements(extensionElements);
        
        process.addFlowElement(userTask);
        bpmnModel.addProcess(process);
        
        return bpmnModel;
    }
    
    private ExtensionAttribute createAttribute(String name, String value) {
        ExtensionAttribute attr = new ExtensionAttribute();
        attr.setName(name);
        attr.setValue(value);
        return attr;
    }
}
