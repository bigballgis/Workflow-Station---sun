package com.developer.service;

import com.developer.client.WorkflowEngineClient;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.entity.ProcessInstance;
import com.developer.repository.ProcessInstanceRepository;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * Property-based tests for ProcessService.
 * Tests universal properties that should hold for all process instance operations.
 */
class ProcessServicePropertyTest {
    
    @Mock
    private ProcessInstanceRepository processInstanceRepository;
    
    @Mock
    private VersionService versionService;
    
    @Mock
    private WorkflowEngineClient workflowEngineClient;
    
    private ProcessService processService;
    
    @BeforeEach
    void setUp() {
        openMocks(this);
        processService = new ProcessService(
                processInstanceRepository,
                versionService,
                workflowEngineClient
        );
    }
    
    /**
     * Property 16: Process Binding at Creation
     * 
     * For any process instance created, it should be bound to the function unit version 
     * that was active at the time of creation.
     * 
     * **Validates: Requirements 5.1**
     */
    @Property(tries = 100)
    void processInstanceBoundToActiveVersionAtCreation(
            @ForAll("functionUnitNames") String functionUnitName,
            @ForAll("versions") String version,
            @ForAll("userIds") String userId) {
        
        // Initialize mocks for each property test iteration
        openMocks(this);
        processService = new ProcessService(
                processInstanceRepository,
                versionService,
                workflowEngineClient
        );
        
        // Given: An active version exists for the function unit
        FunctionUnit activeVersion = createMockFunctionUnit(
                1L, functionUnitName, version, true);
        
        when(versionService.getActiveVersion(functionUnitName))
                .thenReturn(activeVersion);
        
        // Mock process instance save to capture the bound version
        final FunctionUnit[] capturedVersion = new FunctionUnit[1];
        when(processInstanceRepository.save(any(ProcessInstance.class)))
                .thenAnswer(invocation -> {
                    ProcessInstance pi = invocation.getArgument(0);
                    capturedVersion[0] = pi.getFunctionUnitVersion();
                    return pi;
                });
        
        // When: Creating a process instance
        Map<String, Object> variables = new HashMap<>();
        variables.put("testVar", "testValue");
        
        ProcessInstance processInstance = processService.createProcessInstance(
                functionUnitName, variables, userId, "Test User");
        
        // Then: The process instance should be bound to the active version
        assertThat(capturedVersion[0]).isNotNull();
        assertThat(capturedVersion[0].getId()).isEqualTo(activeVersion.getId());
        assertThat(capturedVersion[0].getVersion()).isEqualTo(version);
        assertThat(capturedVersion[0].getIsActive()).isTrue();
        
        // And: The binding should reference the exact active version at creation time
        assertThat(capturedVersion[0]).isSameAs(activeVersion);
    }
    
    /**
     * Property 34: Process Instance Uses Active Version Key
     * 
     * For any process instance created, it should use the process definition key 
     * corresponding to the active version at creation time.
     * 
     * **Validates: Requirements 12.3**
     */
    @Property(tries = 100)
    void processInstanceUsesActiveVersionProcessDefinitionKey(
            @ForAll("functionUnitNames") String functionUnitName,
            @ForAll("versions") String version,
            @ForAll("userIds") String userId) {
        
        // Initialize mocks for each property test iteration
        openMocks(this);
        processService = new ProcessService(
                processInstanceRepository,
                versionService,
                workflowEngineClient
        );
        
        // Given: An active version exists with a process definition
        FunctionUnit activeVersion = createMockFunctionUnit(
                1L, functionUnitName, version, true);
        
        when(versionService.getActiveVersion(functionUnitName))
                .thenReturn(activeVersion);
        
        // Mock process instance save to capture the process definition key
        final String[] capturedProcessKey = new String[1];
        when(processInstanceRepository.save(any(ProcessInstance.class)))
                .thenAnswer(invocation -> {
                    ProcessInstance pi = invocation.getArgument(0);
                    capturedProcessKey[0] = pi.getProcessDefinitionKey();
                    return pi;
                });
        
        // When: Creating a process instance
        Map<String, Object> variables = new HashMap<>();
        ProcessInstance processInstance = processService.createProcessInstance(
                functionUnitName, variables, userId, "Test User");
        
        // Then: The process definition key should match the active version's key format
        String expectedKey = String.format("%s_v%s", functionUnitName, version);
        assertThat(capturedProcessKey[0]).isEqualTo(expectedKey);
        
        // And: The key should contain the version number
        assertThat(capturedProcessKey[0]).contains("_v" + version);
        
        // And: The key should start with the function unit name
        assertThat(capturedProcessKey[0]).startsWith(functionUnitName);
    }
    
    /**
     * Property 17: Process Binding Immutability
     * 
     * For any process instance, its function unit version binding should never change 
     * throughout its entire lifecycle, regardless of new deployments.
     * 
     * **Validates: Requirements 5.2, 5.4**
     */
    @Property(tries = 100)
    void processBindingRemainsImmutableAfterDeployment(
            @ForAll("functionUnitNames") String functionUnitName,
            @ForAll("versions") String initialVersion,
            @ForAll("versions") String newVersion,
            @ForAll("userIds") String userId) {
        
        // Skip if versions are the same
        Assume.that(!initialVersion.equals(newVersion));
        
        // Initialize mocks for each property test iteration
        openMocks(this);
        processService = new ProcessService(
                processInstanceRepository,
                versionService,
                workflowEngineClient
        );
        
        // Given: A process instance bound to an initial version
        FunctionUnit initialActiveVersion = createMockFunctionUnit(
                1L, functionUnitName, initialVersion, true);
        
        when(versionService.getActiveVersion(functionUnitName))
                .thenReturn(initialActiveVersion);
        
        // Capture the initial binding
        final FunctionUnit[] initialBinding = new FunctionUnit[1];
        when(processInstanceRepository.save(any(ProcessInstance.class)))
                .thenAnswer(invocation -> {
                    ProcessInstance pi = invocation.getArgument(0);
                    if (initialBinding[0] == null) {
                        initialBinding[0] = pi.getFunctionUnitVersion();
                    }
                    return pi;
                });
        
        // Create the process instance
        Map<String, Object> variables = new HashMap<>();
        ProcessInstance processInstance = processService.createProcessInstance(
                functionUnitName, variables, userId, "Test User");
        
        // When: A new version is deployed (simulated by changing active version)
        FunctionUnit newActiveVersion = createMockFunctionUnit(
                2L, functionUnitName, newVersion, true);
        
        // Simulate that the old version is now inactive
        initialActiveVersion.setIsActive(false);
        
        // Mock repository to return the process instance with its original binding
        String processInstanceId = UUID.randomUUID().toString();
        ProcessInstance storedInstance = ProcessInstance.builder()
                .id(processInstanceId)
                .processDefinitionKey(String.format("%s_v%s", functionUnitName, initialVersion))
                .functionUnitVersion(initialActiveVersion)  // Still bound to initial version
                .startUserId(userId)
                .status("RUNNING")
                .build();
        
        when(processInstanceRepository.findById(processInstanceId))
                .thenReturn(Optional.of(storedInstance));
        
        // Then: The process instance should still be bound to the initial version
        ProcessInstance retrievedInstance = processInstanceRepository.findById(processInstanceId).get();
        assertThat(retrievedInstance.getFunctionUnitVersion()).isNotNull();
        assertThat(retrievedInstance.getFunctionUnitVersion().getId())
                .isEqualTo(initialActiveVersion.getId());
        assertThat(retrievedInstance.getFunctionUnitVersion().getVersion())
                .isEqualTo(initialVersion);
        
        // And: The binding should NOT be to the new active version
        assertThat(retrievedInstance.getFunctionUnitVersion().getId())
                .isNotEqualTo(newActiveVersion.getId());
        assertThat(retrievedInstance.getFunctionUnitVersion().getVersion())
                .isNotEqualTo(newVersion);
        
        // And: The process definition key should still reference the initial version
        assertThat(retrievedInstance.getProcessDefinitionKey())
                .contains("_v" + initialVersion);
        assertThat(retrievedInstance.getProcessDefinitionKey())
                .doesNotContain("_v" + newVersion);
    }
    
    /**
     * Property 18: Process Execution Version Consistency
     * 
     * For any process instance execution, it should use the function unit version 
     * it was bound to at creation time, not the current active version.
     * 
     * **Validates: Requirements 5.3**
     */
    @Property(tries = 100)
    void processExecutionUsesOriginalBoundVersion(
            @ForAll("functionUnitNames") String functionUnitName,
            @ForAll("versions") String boundVersion,
            @ForAll("versions") String currentActiveVersion) {
        
        // Skip if versions are the same
        Assume.that(!boundVersion.equals(currentActiveVersion));
        
        // Initialize mocks for each property test iteration
        openMocks(this);
        processService = new ProcessService(
                processInstanceRepository,
                versionService,
                workflowEngineClient
        );
        
        // Given: A process instance bound to a specific version
        FunctionUnit boundFunctionUnit = createMockFunctionUnit(
                1L, functionUnitName, boundVersion, false);
        
        // And: A different version is currently active
        FunctionUnit activeFunctionUnit = createMockFunctionUnit(
                2L, functionUnitName, currentActiveVersion, true);
        
        when(versionService.getActiveVersion(functionUnitName))
                .thenReturn(activeFunctionUnit);
        
        // When: Getting the process definition key for the bound version
        String processDefinitionKey = processService.getProcessDefinitionKey(boundFunctionUnit);
        
        // Then: The process definition key should match the bound version, not the active version
        String expectedKey = String.format("%s_v%s", functionUnitName, boundVersion);
        assertThat(processDefinitionKey).isEqualTo(expectedKey);
        
        // And: The key should NOT contain the current active version
        assertThat(processDefinitionKey).doesNotContain("_v" + currentActiveVersion);
        
        // And: The key should contain the bound version
        assertThat(processDefinitionKey).contains("_v" + boundVersion);
    }
    
    /**
     * Property 19: Process Query Version Information
     * 
     * For any process instance query, the result should include the bound function unit 
     * version information.
     * 
     * **Validates: Requirements 5.5**
     */
    @Property(tries = 100)
    void processQueryIncludesVersionInformation(
            @ForAll("functionUnitNames") String functionUnitName,
            @ForAll("versions") String version,
            @ForAll("positiveIntegers") int processCount) {
        
        // Limit process count to reasonable range
        Assume.that(processCount > 0 && processCount <= 100);
        
        // Initialize mocks for each property test iteration
        openMocks(this);
        processService = new ProcessService(
                processInstanceRepository,
                versionService,
                workflowEngineClient
        );
        
        // Given: A function unit version with process instances
        FunctionUnit functionUnit = createMockFunctionUnit(
                1L, functionUnitName, version, true);
        
        // Create mock process instances bound to this version
        List<ProcessInstance> mockProcessInstances = new ArrayList<>();
        for (int i = 0; i < processCount; i++) {
            ProcessInstance pi = ProcessInstance.builder()
                    .id(UUID.randomUUID().toString())
                    .processDefinitionKey(String.format("%s_v%s", functionUnitName, version))
                    .functionUnitVersion(functionUnit)
                    .startUserId("user-" + i)
                    .status("RUNNING")
                    .build();
            mockProcessInstances.add(pi);
        }
        
        when(processInstanceRepository.findByFunctionUnitVersionId(functionUnit.getId()))
                .thenReturn(mockProcessInstances);
        
        // When: Querying process instances by version
        List<ProcessInstance> result = processService.getProcessInstancesByVersion(functionUnit.getId());
        
        // Then: All returned process instances should include version information
        assertThat(result).hasSize(processCount);
        
        for (ProcessInstance pi : result) {
            // Each process instance should have version binding
            assertThat(pi.getFunctionUnitVersion()).isNotNull();
            assertThat(pi.getFunctionUnitVersion().getId()).isEqualTo(functionUnit.getId());
            assertThat(pi.getFunctionUnitVersion().getVersion()).isEqualTo(version);
            
            // Process definition key should include version
            assertThat(pi.getProcessDefinitionKey()).contains("_v" + version);
        }
    }
    
    /**
     * Helper method to create a mock FunctionUnit
     */
    private FunctionUnit createMockFunctionUnit(Long id, String name, String version, boolean isActive) {
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
     * Arbitrary for generating valid function unit names
     */
    @Provide
    Arbitrary<String> functionUnitNames() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars('-', '_')
                .ofMinLength(3)
                .ofMaxLength(50)
                .filter(s -> !s.isEmpty() && Character.isLetter(s.charAt(0)));
    }
    
    /**
     * Arbitrary for generating valid semantic versions
     */
    @Provide
    Arbitrary<String> versions() {
        return Combinators.combine(
                Arbitraries.integers().between(0, 10),
                Arbitraries.integers().between(0, 20),
                Arbitraries.integers().between(0, 50)
        ).as((major, minor, patch) -> String.format("%d.%d.%d", major, minor, patch));
    }
    
    /**
     * Arbitrary for generating valid user IDs
     */
    @Provide
    Arbitrary<String> userIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars('-', '_')
                .ofMinLength(3)
                .ofMaxLength(20);
    }
    
    /**
     * Arbitrary for generating positive integers
     */
    @Provide
    Arbitrary<Integer> positiveIntegers() {
        return Arbitraries.integers().between(1, 100);
    }
}
