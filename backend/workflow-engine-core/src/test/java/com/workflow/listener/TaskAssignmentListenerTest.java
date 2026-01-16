package com.workflow.listener;

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
    private TaskService taskService;
    
    @Mock
    private RuntimeService runtimeService;
    
    @Mock
    private RepositoryService repositoryService;
    
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
                    eq("FIXED_BU_ROLE"), eq(ROLE_ID), eq(BU_ID), eq(INITIATOR_ID), eq(CURRENT_USER_ID)))
                    .thenReturn(result);
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            listener.onEvent(event);
            
            // Verify resolver was called with correct parameters
            verify(taskAssigneeResolver).resolve("FIXED_BU_ROLE", ROLE_ID, BU_ID, INITIATOR_ID, CURRENT_USER_ID);
            
            // Verify candidate users were set
            verify(taskService).addCandidateUser(TASK_ID, "user-001");
            verify(taskService).addCandidateUser(TASK_ID, "user-002");
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
                    eq("ENTITY_MANAGER"), isNull(), eq(INITIATOR_ID)))
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
            
            // Setup resolver to return error
            TaskAssigneeResolver.ResolveResult result = TaskAssigneeResolver.ResolveResult.builder()
                    .errorMessage("No users found with role")
                    .requiresClaim(true)
                    .build();
            when(taskAssigneeResolver.resolve(anyString(), anyString(), any(), anyString(), anyString()))
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
                    .candidateUsers(Arrays.asList("user-003"))
                    .requiresClaim(true)
                    .build();
            when(taskAssigneeResolver.resolve(
                    eq("INITIATOR_BU_ROLE"), eq(ROLE_ID), isNull(), eq(INITIATOR_ID), eq(CURRENT_USER_ID)))
                    .thenReturn(result);
            
            FlowableEntityEventImpl event = createTaskCreatedEvent(task);
            listener.onEvent(event);
            
            verify(taskAssigneeResolver).resolve("INITIATOR_BU_ROLE", ROLE_ID, null, INITIATOR_ID, CURRENT_USER_ID);
            verify(taskService).addCandidateUser(TASK_ID, "user-003");
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
}
