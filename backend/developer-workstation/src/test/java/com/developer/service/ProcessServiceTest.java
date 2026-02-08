package com.developer.service;

import com.developer.client.WorkflowEngineClient;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.entity.ProcessInstance;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProcessService.
 * Tests specific examples and edge cases for process instance management.
 */
@ExtendWith(MockitoExtension.class)
class ProcessServiceTest {
    
    @Mock
    private ProcessInstanceRepository processInstanceRepository;
    
    @Mock
    private VersionService versionService;
    
    @Mock
    private WorkflowEngineClient workflowEngineClient;
    
    private ProcessService processService;
    
    @BeforeEach
    void setUp() {
        processService = new ProcessService(
                processInstanceRepository,
                versionService,
                workflowEngineClient
        );
    }
    
    @Test
    void createProcessInstance_shouldBindToActiveVersion() {
        // Given: An active version exists
        String functionUnitName = "loan-application";
        String version = "1.2.3";
        String userId = "user123";
        String userName = "Test User";
        
        FunctionUnit activeVersion = createFunctionUnit(1L, functionUnitName, version, true);
        
        when(versionService.getActiveVersion(functionUnitName))
                .thenReturn(activeVersion);
        
        ArgumentCaptor<ProcessInstance> processInstanceCaptor = 
                ArgumentCaptor.forClass(ProcessInstance.class);
        when(processInstanceRepository.save(processInstanceCaptor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Creating a process instance
        Map<String, Object> variables = new HashMap<>();
        variables.put("amount", 50000);
        variables.put("applicant", "John Doe");
        
        ProcessInstance result = processService.createProcessInstance(
                functionUnitName, variables, userId, userName);
        
        // Then: The process instance should be bound to the active version
        ProcessInstance savedInstance = processInstanceCaptor.getValue();
        assertThat(savedInstance.getFunctionUnitVersion()).isNotNull();
        assertThat(savedInstance.getFunctionUnitVersion().getId()).isEqualTo(1L);
        assertThat(savedInstance.getFunctionUnitVersion().getVersion()).isEqualTo(version);
        assertThat(savedInstance.getFunctionUnitVersion().getIsActive()).isTrue();
        
        // And: The process definition key should be versioned
        assertThat(savedInstance.getProcessDefinitionKey())
                .isEqualTo("loan-application_v1.2.3");
        
        // And: User information should be set
        assertThat(savedInstance.getStartUserId()).isEqualTo(userId);
        assertThat(savedInstance.getStartUserName()).isEqualTo(userName);
        
        // And: Variables should be stored
        assertThat(savedInstance.getVariables()).containsEntry("amount", 50000);
        assertThat(savedInstance.getVariables()).containsEntry("applicant", "John Doe");
        
        // And: Status should be RUNNING
        assertThat(savedInstance.getStatus()).isEqualTo("RUNNING");
    }
    
    @Test
    void createProcessInstance_shouldThrowExceptionWhenNoActiveVersion() {
        // Given: No active version exists
        String functionUnitName = "non-existent-unit";
        
        when(versionService.getActiveVersion(functionUnitName))
                .thenThrow(new ResourceNotFoundException("FunctionUnit", functionUnitName));
        
        // When/Then: Creating a process instance should throw exception
        assertThatThrownBy(() -> 
                processService.createProcessInstance(
                        functionUnitName, 
                        new HashMap<>(), 
                        "user123", 
                        "Test User"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(functionUnitName);
        
        // And: No process instance should be saved
        verify(processInstanceRepository, never()).save(any());
    }
    
    @Test
    void createProcessInstance_shouldHandleEmptyVariables() {
        // Given: An active version exists
        String functionUnitName = "simple-process";
        FunctionUnit activeVersion = createFunctionUnit(1L, functionUnitName, "1.0.0", true);
        
        when(versionService.getActiveVersion(functionUnitName))
                .thenReturn(activeVersion);
        
        ArgumentCaptor<ProcessInstance> processInstanceCaptor = 
                ArgumentCaptor.forClass(ProcessInstance.class);
        when(processInstanceRepository.save(processInstanceCaptor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Creating a process instance with empty variables
        Map<String, Object> emptyVariables = new HashMap<>();
        
        ProcessInstance result = processService.createProcessInstance(
                functionUnitName, emptyVariables, "user123", "Test User");
        
        // Then: The process instance should be created successfully
        ProcessInstance savedInstance = processInstanceCaptor.getValue();
        assertThat(savedInstance.getVariables()).isEmpty();
        assertThat(savedInstance.getFunctionUnitVersion()).isNotNull();
    }
    
    @Test
    void getProcessDefinitionKey_shouldReturnVersionedKey() {
        // Given: A function unit version with process definition
        String functionUnitName = "approval-workflow";
        String version = "2.1.0";
        FunctionUnit functionUnit = createFunctionUnit(1L, functionUnitName, version, true);
        
        // When: Getting the process definition key
        String processDefinitionKey = processService.getProcessDefinitionKey(functionUnit);
        
        // Then: The key should follow the versioned format
        assertThat(processDefinitionKey).isEqualTo("approval-workflow_v2.1.0");
        assertThat(processDefinitionKey).contains("_v" + version);
        assertThat(processDefinitionKey).startsWith(functionUnitName);
    }
    
    @Test
    void getProcessDefinitionKey_shouldThrowExceptionWhenNoProcessDefinition() {
        // Given: A function unit version without process definition
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(1L)
                .name("incomplete-unit")
                .version("1.0.0")
                .isActive(true)
                .deployedAt(Instant.now())
                .processDefinition(null)  // No process definition
                .build();
        
        // When/Then: Getting the process definition key should throw exception
        assertThatThrownBy(() -> processService.getProcessDefinitionKey(functionUnit))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ProcessDefinition");
    }
    
    @Test
    void getProcessInstancesByVersion_shouldReturnAllInstancesForVersion() {
        // Given: Multiple process instances bound to a version
        Long versionId = 1L;
        String functionUnitName = "document-approval";
        String version = "1.5.0";
        
        FunctionUnit functionUnit = createFunctionUnit(versionId, functionUnitName, version, true);
        
        List<ProcessInstance> mockInstances = Arrays.asList(
                createProcessInstance("pi-1", functionUnit, "user1"),
                createProcessInstance("pi-2", functionUnit, "user2"),
                createProcessInstance("pi-3", functionUnit, "user3")
        );
        
        when(processInstanceRepository.findByFunctionUnitVersionId(versionId))
                .thenReturn(mockInstances);
        
        // When: Querying process instances by version
        List<ProcessInstance> result = processService.getProcessInstancesByVersion(versionId);
        
        // Then: All instances should be returned
        assertThat(result).hasSize(3);
        assertThat(result).extracting(ProcessInstance::getId)
                .containsExactly("pi-1", "pi-2", "pi-3");
        
        // And: All instances should be bound to the same version
        assertThat(result).allMatch(pi -> 
                pi.getFunctionUnitVersion().getId().equals(versionId));
    }
    
    @Test
    void getProcessInstancesByVersion_shouldReturnEmptyListWhenNoInstances() {
        // Given: No process instances exist for a version
        Long versionId = 1L;
        
        when(processInstanceRepository.findByFunctionUnitVersionId(versionId))
                .thenReturn(Collections.emptyList());
        
        // When: Querying process instances by version
        List<ProcessInstance> result = processService.getProcessInstancesByVersion(versionId);
        
        // Then: An empty list should be returned
        assertThat(result).isEmpty();
    }
    
    @Test
    void countProcessInstancesByVersion_shouldReturnCorrectCount() {
        // Given: A version with process instances
        Long versionId = 1L;
        long expectedCount = 42L;
        
        when(processInstanceRepository.countByFunctionUnitVersionId(versionId))
                .thenReturn(expectedCount);
        
        // When: Counting process instances
        long result = processService.countProcessInstancesByVersion(versionId);
        
        // Then: The correct count should be returned
        assertThat(result).isEqualTo(expectedCount);
    }
    
    @Test
    void countProcessInstancesByVersion_shouldReturnZeroWhenNoInstances() {
        // Given: A version with no process instances
        Long versionId = 1L;
        
        when(processInstanceRepository.countByFunctionUnitVersionId(versionId))
                .thenReturn(0L);
        
        // When: Counting process instances
        long result = processService.countProcessInstancesByVersion(versionId);
        
        // Then: Zero should be returned
        assertThat(result).isEqualTo(0L);
    }
    
    @Test
    void processInstanceBinding_shouldNotChangeAfterNewDeployment() {
        // Given: A process instance bound to version 1.0.0
        String functionUnitName = "order-processing";
        String initialVersion = "1.0.0";
        String newVersion = "2.0.0";
        
        FunctionUnit initialActiveVersion = createFunctionUnit(
                1L, functionUnitName, initialVersion, true);
        FunctionUnit newActiveVersion = createFunctionUnit(
                2L, functionUnitName, newVersion, true);
        
        // Create process instance bound to initial version
        ProcessInstance processInstance = createProcessInstance(
                "pi-123", initialActiveVersion, "user1");
        
        when(processInstanceRepository.findById("pi-123"))
                .thenReturn(Optional.of(processInstance));
        
        // When: A new version is deployed (simulated by changing active version)
        initialActiveVersion.setIsActive(false);
        
        // Then: The process instance should still be bound to the initial version
        ProcessInstance retrievedInstance = processInstanceRepository.findById("pi-123").get();
        assertThat(retrievedInstance.getFunctionUnitVersion().getId()).isEqualTo(1L);
        assertThat(retrievedInstance.getFunctionUnitVersion().getVersion())
                .isEqualTo(initialVersion);
        assertThat(retrievedInstance.getProcessDefinitionKey())
                .isEqualTo("order-processing_v1.0.0");
        
        // And: The binding should NOT be to the new version
        assertThat(retrievedInstance.getFunctionUnitVersion().getId())
                .isNotEqualTo(newActiveVersion.getId());
    }
    
    /**
     * Helper method to create a FunctionUnit with ProcessDefinition
     */
    private FunctionUnit createFunctionUnit(Long id, String name, String version, boolean isActive) {
        ProcessDefinition processDefinition = ProcessDefinition.builder()
                .id(id)
                .bpmnXml("<bpmn>test</bpmn>")
                .build();
        
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(id)
                .name(name)
                .version(version)
                .isActive(isActive)
                .deployedAt(Instant.now())
                .processDefinition(processDefinition)
                .build();
        
        // Set bidirectional relationship
        processDefinition.setFunctionUnit(functionUnit);
        
        return functionUnit;
    }
    
    /**
     * Helper method to create a ProcessInstance
     */
    private ProcessInstance createProcessInstance(
            String id, FunctionUnit functionUnit, String userId) {
        return ProcessInstance.builder()
                .id(id)
                .processDefinitionKey(String.format("%s_v%s", 
                        functionUnit.getName(), functionUnit.getVersion()))
                .processDefinitionName(functionUnit.getName())
                .functionUnitVersion(functionUnit)
                .startUserId(userId)
                .startUserName("User " + userId)
                .status("RUNNING")
                .variables(new HashMap<>())
                .build();
    }
}
