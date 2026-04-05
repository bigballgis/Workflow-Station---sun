package com.workflow.listener;

import com.workflow.component.BpmnActionParser;
import com.workflow.repository.ExtendedTaskInfoRepository;
import com.workflow.service.LastUserTaskAssigneeQuery;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TaskAssignmentListener 单元测试
 * 测试任务创建时的自动分配逻辑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskAssignmentListener Tests")
class TaskAssignmentListenerTest {
    
    @Mock
    private TaskAssigneeResolver taskAssigneeResolver;

    @Mock
    private LastUserTaskAssigneeQuery lastUserTaskAssigneeQuery;
    
    @Mock
    private TaskService taskService;
    
    @Mock
    private RuntimeService runtimeService;
    
    @Mock
    private RepositoryService repositoryService;

    @Mock
    private BpmnActionParser bpmnActionParser;
    
    @InjectMocks
    private TaskAssignmentListener listener;
    
    private static final String TASK_ID = "task-001";
    private static final String PROCESS_INSTANCE_ID = "process-001";
    private static final String PROCESS_DEFINITION_ID = "process-def-001";
    private static final String TASK_DEFINITION_KEY = "Task_Approval";
    private static final String INITIATOR_ID = "initiator-001";
    private static final String CURRENT_USER_ID = "current-user-001";
    private static final String ROLE_ID = "role-001";
    private static final String BU_ID = "bu-001";
    
    @Nested
    @DisplayName("onEvent Tests")
    class OnEventTests {
        
        @Test
        @DisplayName("Should skip non-TASK_CREATED events")
        void shouldSkipNonTaskCreatedEvents() {
            FlowableEntityEventImpl event = mock(FlowableEntityEventImpl.class);
            when(event.getType()).thenReturn(FlowableEngineEventType.TASK_COMPLETED);
            
            listener.onEvent(event);
            
            verifyNoInteractions(taskAssigneeResolver);
            verifyNoInteractions(taskService);
        }
        
        @Test
        @DisplayName("Should skip if task already has assignee")
        void shouldSkipIfTaskHasAssignee() {
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn("existing-assignee");
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            
            listener.onEvent(event);
            
            verifyNoInteractions(taskAssigneeResolver);
            verify(taskService, never()).setAssignee(anyString(), anyString());
        }
    }
    
    @Nested
    @DisplayName("BPMN Extension Property Parsing Tests")
    class BpmnExtensionParsingTests {
        
        @Test
        @DisplayName("Should parse assigneeType, roleId, businessUnitId from BPMN")
        void shouldParseBpmnExtensionProperties() {
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn(null);
            
            // Setup BPMN model with extension properties
            BpmnModel bpmnModel = createBpmnModelWithExtensions(
                    "FIXED_BU_ROLE", ROLE_ID, BU_ID);
            when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnModel);
            
            // Setup process variables
            Map<String, Object> variables = new HashMap<>();
            variables.put("initiator", INITIATOR_ID);
            variables.put("currentUserId", CURRENT_USER_ID);
            when(runtimeService.getVariables(PROCESS_INSTANCE_ID)).thenReturn(variables);
            
            // Setup resolver result
            TaskAssigneeResolver.ResolveResult result = TaskAssigneeResolver.ResolveResult.builder()
                    .candidateUsers(Arrays.asList("user-001", "user-002"))
                    .requiresClaim(true)
                    .build();
            when(taskAssigneeResolver.resolve(
                    eq("FIXED_BU_ROLE"), eq(ROLE_ID), eq(BU_ID), eq(INITIATOR_ID), isNull(), isNull()))
                    .thenReturn(result);
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            listener.onEvent(event);
            
            verify(taskAssigneeResolver).resolve("FIXED_BU_ROLE", ROLE_ID, BU_ID, INITIATOR_ID, null, null);
            
            // Verify candidate users were set
            verify(taskService).addCandidateUser(TASK_ID, "user-001");
            verify(taskService).addCandidateUser(TASK_ID, "user-002");
        }
        
        @Test
        @DisplayName("Should map legacy expression ${initiator} to INITIATOR and assign")
        void shouldMapLegacyExpressionInitiatorToInitiatorType() {
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn(null);

            BpmnModel bpmnModel = createBpmnModelWithLegacyExpressionInitiator();
            when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnModel);

            Map<String, Object> variables = new HashMap<>();
            variables.put("initiator", INITIATOR_ID);
            when(runtimeService.getVariables(PROCESS_INSTANCE_ID)).thenReturn(variables);

            TaskAssigneeResolver.ResolveResult result = TaskAssigneeResolver.ResolveResult.builder()
                    .assignee(INITIATOR_ID)
                    .requiresClaim(false)
                    .build();
            when(taskAssigneeResolver.resolve(eq("INITIATOR"), isNull(), isNull(), eq(INITIATOR_ID), isNull(), isNull()))
                    .thenReturn(result);

            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            listener.onEvent(event);

            verify(taskService).setAssignee(TASK_ID, INITIATOR_ID);
        }

        @Test
        @DisplayName("Should read assigneeType from custom:properties container name (Flowable XML import)")
        void shouldParseAssigneeTypeWhenPropertiesContainerHasNamespacePrefix() {
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn(null);

            BpmnModel bpmnModel = createBpmnModelWithExtensionsAndPropertiesContainerName("INITIATOR", "custom:properties",
                    "http://workflow.platform/schema/custom");
            when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnModel);

            Map<String, Object> variables = new HashMap<>();
            variables.put("initiator", INITIATOR_ID);
            when(runtimeService.getVariables(PROCESS_INSTANCE_ID)).thenReturn(variables);

            TaskAssigneeResolver.ResolveResult result = TaskAssigneeResolver.ResolveResult.builder()
                    .assignee(INITIATOR_ID)
                    .requiresClaim(false)
                    .build();
            when(taskAssigneeResolver.resolve(eq("INITIATOR"), isNull(), isNull(), eq(INITIATOR_ID), isNull(), isNull()))
                    .thenReturn(result);

            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            listener.onEvent(event);

            verify(taskService).setAssignee(TASK_ID, INITIATOR_ID);
        }

        @Test
        @DisplayName("Should handle direct assignment types")
        void shouldHandleDirectAssignmentTypes() {
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn(null);
            
            // Setup BPMN model with ENTITY_MANAGER type
            BpmnModel bpmnModel = createBpmnModelWithExtensions("ENTITY_MANAGER", null, null);
            when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnModel);
            
            // Setup process variables
            Map<String, Object> variables = new HashMap<>();
            variables.put("initiator", INITIATOR_ID);
            when(runtimeService.getVariables(PROCESS_INSTANCE_ID)).thenReturn(variables);
            
            // Setup resolver result for direct assignment
            // Note: When roleId is null, the listener uses the deprecated resolve method
            TaskAssigneeResolver.ResolveResult result = TaskAssigneeResolver.ResolveResult.builder()
                    .assignee("manager-001")
                    .requiresClaim(false)
                    .build();
            when(taskAssigneeResolver.resolve(
                    eq("ENTITY_MANAGER"), isNull(), isNull(), eq(INITIATOR_ID), eq(INITIATOR_ID), isNull()))
                    .thenReturn(result);
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            listener.onEvent(event);
            
            // Verify assignee was set directly
            verify(taskService).setAssignee(TASK_ID, "manager-001");
            verify(taskService, never()).addCandidateUser(anyString(), anyString());
        }
        
        @Test
        @DisplayName("Should handle resolver error gracefully")
        void shouldHandleResolverErrorGracefully() {
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn(null);
            
            BpmnModel bpmnModel = createBpmnModelWithExtensions("CURRENT_BU_ROLE", ROLE_ID, null);
            when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnModel);
            
            Map<String, Object> variables = new HashMap<>();
            variables.put("initiator", INITIATOR_ID);
            variables.put("currentUserId", CURRENT_USER_ID);
            when(runtimeService.getVariables(PROCESS_INSTANCE_ID)).thenReturn(variables);

            when(lastUserTaskAssigneeQuery.findLastCompletedUserTaskAssignee(PROCESS_INSTANCE_ID))
                    .thenReturn(Optional.of(CURRENT_USER_ID));
            
            TaskAssigneeResolver.ResolveResult result = TaskAssigneeResolver.ResolveResult.builder()
                    .errorMessage("No users found with role")
                    .requiresClaim(true)
                    .build();
            when(taskAssigneeResolver.resolve(eq("CURRENT_BU_ROLE"), eq(ROLE_ID), isNull(), eq(INITIATOR_ID), eq(CURRENT_USER_ID), isNull()))
                    .thenReturn(result);
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            listener.onEvent(event);
            
            // Verify no assignment was made
            verify(taskService, never()).setAssignee(anyString(), anyString());
            verify(taskService, never()).addCandidateUser(anyString(), anyString());
        }
        
        @Test
        @DisplayName("Should skip if no assigneeType defined")
        void shouldSkipIfNoAssigneeType() {
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn(null);
            
            // Setup BPMN model without extension properties
            BpmnModel bpmnModel = createBpmnModelWithoutExtensions();
            when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnModel);
            
            // Setup empty process variables
            when(runtimeService.getVariables(PROCESS_INSTANCE_ID)).thenReturn(new HashMap<>());
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            listener.onEvent(event);
            
            verifyNoInteractions(taskAssigneeResolver);
        }
    }
    
    @Nested
    @DisplayName("Fallback to Process Variables Tests")
    class FallbackToProcessVariablesTests {
        
        @Test
        @DisplayName("Should fallback to process variables when BPMN has no extension")
        void shouldFallbackToProcessVariables() {
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn(null);
            
            // Setup BPMN model without extension properties
            BpmnModel bpmnModel = createBpmnModelWithoutExtensions();
            when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnModel);
            
            // Setup process variables with assignee config
            Map<String, Object> variables = new HashMap<>();
            variables.put("initiator", INITIATOR_ID);
            variables.put("currentUserId", CURRENT_USER_ID);
            variables.put("assigneeType", "INITIATOR_BU_ROLE");
            variables.put("roleId", ROLE_ID);
            when(runtimeService.getVariables(PROCESS_INSTANCE_ID)).thenReturn(variables);
            
            TaskAssigneeResolver.ResolveResult result = TaskAssigneeResolver.ResolveResult.builder()
                    .assignee("user-003")
                    .requiresClaim(false)
                    .build();
            when(taskAssigneeResolver.resolve(
                    eq("INITIATOR_BU_ROLE"), eq(ROLE_ID), isNull(), eq(INITIATOR_ID), eq(INITIATOR_ID), isNull()))
                    .thenReturn(result);
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            listener.onEvent(event);
            
            verify(taskAssigneeResolver).resolve("INITIATOR_BU_ROLE", ROLE_ID, null, INITIATOR_ID, INITIATOR_ID, null);
            // 单人候选人池：直接 setAssignee，避免无意义认领步骤
            verify(taskService).setAssignee(TASK_ID, "user-003");
            verify(taskService, never()).addCandidateUser(anyString(), anyString());
        }
    }
    
    @Nested
    @DisplayName("Listener Configuration Tests")
    class ListenerConfigurationTests {
        
        @Test
        @DisplayName("isFailOnException should return false")
        void isFailOnExceptionShouldReturnFalse() {
            assertThat(listener.isFailOnException()).isFalse();
        }
        
        @Test
        @DisplayName("isFireOnTransactionLifecycleEvent should return false")
        void isFireOnTransactionLifecycleEventShouldReturnFalse() {
            assertThat(listener.isFireOnTransactionLifecycleEvent()).isFalse();
        }
        
        @Test
        @DisplayName("getOnTransaction should return null")
        void getOnTransactionShouldReturnNull() {
            assertThat(listener.getOnTransaction()).isNull();
        }
    }
    
    @Nested
    @DisplayName("ELEMENT_VARIABLE Assignment Tests")
    class ElementVariableAssignmentTests {
        
        private TaskAssignmentListener elementVariableListener;
        
        @Mock
        private ExtendedTaskInfoRepository mockExtendedTaskInfoRepository;
        
        @BeforeEach
        void setupElementVariableTests() {
            // Create a new listener instance with mocked dependencies
            elementVariableListener = new TaskAssignmentListener();
            // Use reflection to inject mocks since fields are private
            try {
                java.lang.reflect.Field taskAssigneeResolverField = TaskAssignmentListener.class.getDeclaredField("taskAssigneeResolver");
                taskAssigneeResolverField.setAccessible(true);
                taskAssigneeResolverField.set(elementVariableListener, taskAssigneeResolver);
                
                java.lang.reflect.Field taskServiceField = TaskAssignmentListener.class.getDeclaredField("taskService");
                taskServiceField.setAccessible(true);
                taskServiceField.set(elementVariableListener, taskService);
                
                java.lang.reflect.Field runtimeServiceField = TaskAssignmentListener.class.getDeclaredField("runtimeService");
                runtimeServiceField.setAccessible(true);
                runtimeServiceField.set(elementVariableListener, runtimeService);
                
                java.lang.reflect.Field repositoryServiceField = TaskAssignmentListener.class.getDeclaredField("repositoryService");
                repositoryServiceField.setAccessible(true);
                repositoryServiceField.set(elementVariableListener, repositoryService);

                java.lang.reflect.Field bpmnActionParserField = TaskAssignmentListener.class.getDeclaredField("bpmnActionParser");
                bpmnActionParserField.setAccessible(true);
                bpmnActionParserField.set(elementVariableListener, bpmnActionParser);
                
                java.lang.reflect.Field extendedTaskInfoRepositoryField = TaskAssignmentListener.class.getDeclaredField("extendedTaskInfoRepository");
                extendedTaskInfoRepositoryField.setAccessible(true);
                extendedTaskInfoRepositoryField.set(elementVariableListener, mockExtendedTaskInfoRepository);
            } catch (Exception e) {
                throw new RuntimeException("Failed to inject mocks", e);
            }
        }
        
        @Test
        @DisplayName("Should handle ELEMENT_VARIABLE assignment with valid currentItem")
        void shouldHandleElementVariableAssignmentWithValidCurrentItem() {
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn(null);
            when(task.getExecutionId()).thenReturn("execution-001");
            
            // Setup BPMN model with ELEMENT_VARIABLE type
            BpmnModel bpmnModel = createBpmnModelWithElementVariable("45", "fu_participants");
            when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnModel);
            
            // Setup currentItem variable
            Map<String, Object> currentItem = new HashMap<>();
            currentItem.put("rowId", 101L);
            currentItem.put("assigneeId", "user-001");
            currentItem.put("rowVersion", 1L);
            when(runtimeService.getVariable("execution-001", "currentItem")).thenReturn(currentItem);
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            elementVariableListener.onEvent(event);
            
            // Verify assignee was set
            verify(taskService).setAssignee(TASK_ID, "user-001");
            
            // Verify ExtendedTaskInfo was created
            verify(mockExtendedTaskInfoRepository).save(argThat(extInfo -> 
                extInfo.getTaskId().equals(TASK_ID) &&
                extInfo.getAssignmentType() == com.workflow.enums.AssignmentType.USER &&
                extInfo.getAssignmentTarget().equals("user-001") &&
                extInfo.getStatus().equals("ASSIGNED") &&
                extInfo.getExtendedProperties().contains("\"multiInstance\":true") &&
                extInfo.getExtendedProperties().contains("\"subTableRowId\":101") &&
                extInfo.getExtendedProperties().contains("\"subTableRowVersion\":1") &&
                extInfo.getExtendedProperties().contains("\"subTableId\":\"45\"") &&
                extInfo.getExtendedProperties().contains("\"subTableName\":\"fu_participants\"")
            ));
        }
        
        @Test
        @DisplayName("Should handle ELEMENT_VARIABLE when currentItem is null")
        void shouldHandleElementVariableWhenCurrentItemIsNull() {
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn(null);
            when(task.getExecutionId()).thenReturn("execution-001");
            
            // Setup BPMN model with ELEMENT_VARIABLE type
            BpmnModel bpmnModel = createBpmnModelWithElementVariable("45", "fu_participants");
            when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnModel);
            
            // currentItem is null
            when(runtimeService.getVariable("execution-001", "currentItem")).thenReturn(null);
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            elementVariableListener.onEvent(event);
            
            // Verify no assignment was made
            verify(taskService, never()).setAssignee(anyString(), anyString());
            verify(mockExtendedTaskInfoRepository, never()).save(any());
        }
        
        @Test
        @DisplayName("Should handle ELEMENT_VARIABLE when assigneeId is invalid")
        void shouldHandleElementVariableWhenAssigneeIdIsInvalid() {
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn(null);
            when(task.getExecutionId()).thenReturn("execution-001");
            
            // Setup BPMN model with ELEMENT_VARIABLE type
            BpmnModel bpmnModel = createBpmnModelWithElementVariable("45", "fu_participants");
            when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnModel);
            
            // Setup currentItem variable with invalid assigneeId
            Map<String, Object> currentItem = new HashMap<>();
            currentItem.put("rowId", 101L);
            currentItem.put("assigneeId", "invalid-user");
            currentItem.put("rowVersion", 1L);
            when(runtimeService.getVariable("execution-001", "currentItem")).thenReturn(currentItem);
            
            // Simulate setAssignee throwing exception (user not found)
            doThrow(new RuntimeException("User not found")).when(taskService).setAssignee(TASK_ID, "invalid-user");
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            elementVariableListener.onEvent(event);
            
            // Verify setAssignee was attempted
            verify(taskService).setAssignee(TASK_ID, "invalid-user");
            
            // Verify ExtendedTaskInfo was NOT created (because assignment failed)
            verify(mockExtendedTaskInfoRepository, never()).save(any());
        }
        
        @Test
        @DisplayName("Should handle ELEMENT_VARIABLE when ExtendedTaskInfo save fails")
        void shouldHandleElementVariableWhenExtendedTaskInfoSaveFails() {
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn(null);
            when(task.getExecutionId()).thenReturn("execution-001");
            
            // Setup BPMN model with ELEMENT_VARIABLE type
            BpmnModel bpmnModel = createBpmnModelWithElementVariable("45", "fu_participants");
            when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnModel);
            
            // Setup currentItem variable
            Map<String, Object> currentItem = new HashMap<>();
            currentItem.put("rowId", 101L);
            currentItem.put("assigneeId", "user-001");
            currentItem.put("rowVersion", 1L);
            when(runtimeService.getVariable("execution-001", "currentItem")).thenReturn(currentItem);
            
            // Simulate ExtendedTaskInfo save failure
            doThrow(new RuntimeException("Database error")).when(mockExtendedTaskInfoRepository).save(any());
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            elementVariableListener.onEvent(event);
            
            // Verify assignee was still set (failure should not affect Flowable task creation)
            verify(taskService).setAssignee(TASK_ID, "user-001");
            
            // Verify save was attempted
            verify(mockExtendedTaskInfoRepository).save(any());
        }
        
        @Test
        @DisplayName("Should handle ELEMENT_VARIABLE with numeric rowId as string")
        void shouldHandleElementVariableWithNumericRowIdAsString() {
            TaskEntity task = createMockTask();
            when(task.getAssignee()).thenReturn(null);
            when(task.getExecutionId()).thenReturn("execution-001");
            
            // Setup BPMN model with ELEMENT_VARIABLE type
            BpmnModel bpmnModel = createBpmnModelWithElementVariable("45", "fu_participants");
            when(repositoryService.getBpmnModel(PROCESS_DEFINITION_ID)).thenReturn(bpmnModel);
            
            // Setup currentItem variable with rowId as string
            Map<String, Object> currentItem = new HashMap<>();
            currentItem.put("rowId", "102");
            currentItem.put("assigneeId", "user-002");
            currentItem.put("rowVersion", "2");
            when(runtimeService.getVariable("execution-001", "currentItem")).thenReturn(currentItem);
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            elementVariableListener.onEvent(event);
            
            // Verify assignee was set
            verify(taskService).setAssignee(TASK_ID, "user-002");
            
            // Verify ExtendedTaskInfo was created with parsed rowId
            verify(mockExtendedTaskInfoRepository).save(argThat(extInfo -> 
                extInfo.getExtendedProperties().contains("\"subTableRowId\":102") &&
                extInfo.getExtendedProperties().contains("\"subTableRowVersion\":2")
            ));
        }
    }
    
    // ==================== Helper Methods ====================
    
    private TaskEntity createMockTask() {
        TaskEntity task = mock(TaskEntity.class);
        when(task.getId()).thenReturn(TASK_ID);
        when(task.getProcessInstanceId()).thenReturn(PROCESS_INSTANCE_ID);
        when(task.getProcessDefinitionId()).thenReturn(PROCESS_DEFINITION_ID);
        when(task.getTaskDefinitionKey()).thenReturn(TASK_DEFINITION_KEY);
        when(task.getName()).thenReturn("Test Task");
        return task;
    }
    
    private FlowableEntityEventImpl createTaskCreatedEvent(TaskEntity task) {
        FlowableEntityEventImpl event = mock(FlowableEntityEventImpl.class);
        when(event.getType()).thenReturn(FlowableEngineEventType.TASK_CREATED);
        when(event.getEntity()).thenReturn(task);
        return event;
    }
    
    /** assigneeType=expression + assigneeValue=${initiator}（旧版 TaskProperties） */
    private BpmnModel createBpmnModelWithLegacyExpressionInitiator() {
        BpmnModel bpmnModel = new BpmnModel();
        org.flowable.bpmn.model.Process process = new org.flowable.bpmn.model.Process();
        process.setId("Process_1");

        UserTask userTask = new UserTask();
        userTask.setId(TASK_DEFINITION_KEY);
        userTask.setName("Test Task");

        Map<String, List<ExtensionElement>> extensionElements = new HashMap<>();

        ExtensionElement propertiesElement = new ExtensionElement();
        propertiesElement.setName("properties");
        propertiesElement.setNamespace("http://custom.bpmn.io/schema");

        Map<String, List<ExtensionElement>> childElements = new HashMap<>();
        List<ExtensionElement> propertyElements = new ArrayList<>();

        ExtensionElement typeProp = new ExtensionElement();
        typeProp.setName("property");
        typeProp.setNamespace("http://custom.bpmn.io/schema");
        typeProp.addAttribute(createAttribute("name", "assigneeType"));
        typeProp.addAttribute(createAttribute("value", "expression"));
        propertyElements.add(typeProp);

        ExtensionElement valueProp = new ExtensionElement();
        valueProp.setName("property");
        valueProp.setNamespace("http://custom.bpmn.io/schema");
        valueProp.addAttribute(createAttribute("name", "assigneeValue"));
        valueProp.addAttribute(createAttribute("value", "${initiator}"));
        propertyElements.add(valueProp);

        childElements.put("property", propertyElements);
        propertiesElement.setChildElements(childElements);

        extensionElements.put("properties", Arrays.asList(propertiesElement));
        userTask.setExtensionElements(extensionElements);

        process.addFlowElement(userTask);
        bpmnModel.addProcess(process);

        return bpmnModel;
    }

    private BpmnModel createBpmnModelWithExtensions(String assigneeType, String roleId, String businessUnitId) {
        BpmnModel bpmnModel = new BpmnModel();
        org.flowable.bpmn.model.Process process = new org.flowable.bpmn.model.Process();
        process.setId("Process_1");
        
        UserTask userTask = new UserTask();
        userTask.setId(TASK_DEFINITION_KEY);
        userTask.setName("Test Task");
        
        // Create extension elements
        Map<String, List<ExtensionElement>> extensionElements = new HashMap<>();
        
        ExtensionElement propertiesElement = new ExtensionElement();
        propertiesElement.setName("properties");
        propertiesElement.setNamespace("http://custom.bpmn.io/schema");
        
        Map<String, List<ExtensionElement>> childElements = new HashMap<>();
        List<ExtensionElement> propertyElements = new ArrayList<>();
        
        // Add assigneeType property
        if (assigneeType != null) {
            ExtensionElement assigneeTypeProperty = new ExtensionElement();
            assigneeTypeProperty.setName("property");
            assigneeTypeProperty.setNamespace("http://custom.bpmn.io/schema");
            assigneeTypeProperty.addAttribute(createAttribute("name", "assigneeType"));
            assigneeTypeProperty.addAttribute(createAttribute("value", assigneeType));
            propertyElements.add(assigneeTypeProperty);
        }
        
        // Add roleId property
        if (roleId != null) {
            ExtensionElement roleIdProperty = new ExtensionElement();
            roleIdProperty.setName("property");
            roleIdProperty.setNamespace("http://custom.bpmn.io/schema");
            roleIdProperty.addAttribute(createAttribute("name", "roleId"));
            roleIdProperty.addAttribute(createAttribute("value", roleId));
            propertyElements.add(roleIdProperty);
        }
        
        // Add businessUnitId property
        if (businessUnitId != null) {
            ExtensionElement businessUnitIdProperty = new ExtensionElement();
            businessUnitIdProperty.setName("property");
            businessUnitIdProperty.setNamespace("http://custom.bpmn.io/schema");
            businessUnitIdProperty.addAttribute(createAttribute("name", "businessUnitId"));
            businessUnitIdProperty.addAttribute(createAttribute("value", businessUnitId));
            propertyElements.add(businessUnitIdProperty);
        }
        
        childElements.put("property", propertyElements);
        propertiesElement.setChildElements(childElements);
        
        extensionElements.put("properties", Arrays.asList(propertiesElement));
        userTask.setExtensionElements(extensionElements);
        
        process.addFlowElement(userTask);
        bpmnModel.addProcess(process);
        
        return bpmnModel;
    }

    private BpmnModel createBpmnModelWithExtensionsAndPropertiesContainerName(
            String assigneeType, String propertiesElementName, String propertiesNamespace) {
        BpmnModel bpmnModel = new BpmnModel();
        org.flowable.bpmn.model.Process process = new org.flowable.bpmn.model.Process();
        process.setId("Process_1");

        UserTask userTask = new UserTask();
        userTask.setId(TASK_DEFINITION_KEY);
        userTask.setName("Test Task");

        Map<String, List<ExtensionElement>> extensionElements = new HashMap<>();

        ExtensionElement propertiesElement = new ExtensionElement();
        propertiesElement.setName(propertiesElementName);
        propertiesElement.setNamespace(propertiesNamespace);

        Map<String, List<ExtensionElement>> childElements = new HashMap<>();
        List<ExtensionElement> propertyElements = new ArrayList<>();

        ExtensionElement assigneeTypeProperty = new ExtensionElement();
        assigneeTypeProperty.setName("property");
        assigneeTypeProperty.setNamespace(propertiesNamespace);
        assigneeTypeProperty.addAttribute(createAttribute("name", "assigneeType"));
        assigneeTypeProperty.addAttribute(createAttribute("value", assigneeType));
        propertyElements.add(assigneeTypeProperty);

        childElements.put("property", propertyElements);
        propertiesElement.setChildElements(childElements);

        extensionElements.put("extensionElements", Arrays.asList(propertiesElement));
        userTask.setExtensionElements(extensionElements);

        process.addFlowElement(userTask);
        bpmnModel.addProcess(process);

        return bpmnModel;
    }
    
    private BpmnModel createBpmnModelWithoutExtensions() {
        BpmnModel bpmnModel = new BpmnModel();
        org.flowable.bpmn.model.Process process = new org.flowable.bpmn.model.Process();
        process.setId("Process_1");
        
        UserTask userTask = new UserTask();
        userTask.setId(TASK_DEFINITION_KEY);
        userTask.setName("Test Task");
        
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
    
    private BpmnModel createBpmnModelWithElementVariable(String subTableId, String subTableName) {
        BpmnModel bpmnModel = new BpmnModel();
        org.flowable.bpmn.model.Process process = new org.flowable.bpmn.model.Process();
        process.setId("Process_1");
        
        UserTask userTask = new UserTask();
        userTask.setId(TASK_DEFINITION_KEY);
        userTask.setName("Test Task");
        
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
}
