package com.developer.service;

import com.developer.client.WorkflowEngineClient;
import com.developer.entity.FunctionUnit;
import com.developer.repository.FunctionUnitRepository;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * Property-based tests for DeploymentService.
 * Tests universal properties that should hold for all valid deployments.
 */
class DeploymentServicePropertyTest {
    
    @Mock
    private FunctionUnitRepository functionUnitRepository;
    
    @Mock
    private VersionService versionService;
    
    @Mock
    private PermissionService permissionService;
    
    @Mock
    private WorkflowEngineClient workflowEngineClient;
    
    private DeploymentService deploymentService;
    
    @BeforeEach
    void setUp() {
        openMocks(this);
        deploymentService = new DeploymentService(
                functionUnitRepository,
                versionService,
                permissionService,
                workflowEngineClient
        );
    }
    
    /**
     * Property 25: Versioned Process Definition Key
     * 
     * For any function unit deployment, the BPMN process definition key deployed to Flowable 
     * should include the version number in the format {functionUnitName}_v{version}.
     * 
     * **Validates: Requirements 7.4, 12.1, 12.2**
     */
    @Property(tries = 100)
    void versionedProcessDefinitionKeyFormat(
            @ForAll("functionUnitNames") String functionUnitName,
            @ForAll("versions") String version) {
        
        // Initialize mocks for each property test iteration
        openMocks(this);
        deploymentService = new DeploymentService(
                functionUnitRepository,
                versionService,
                permissionService,
                workflowEngineClient
        );
        
        // Given: A function unit name and version
        String bpmnXml = "<bpmn>test</bpmn>";
        
        // Mock workflow engine to capture the process key
        final String[] capturedProcessKey = new String[1];
        when(workflowEngineClient.deployProcess(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    capturedProcessKey[0] = invocation.getArgument(0);
                    Map<String, Object> result = new HashMap<>();
                    result.put("processDefinitionId", "test-id");
                    result.put("processDefinitionKey", capturedProcessKey[0]);
                    return Optional.of(result);
                });
        
        // When: Deploying BPMN to Flowable
        String processDefinitionKey = deploymentService.deployBPMNToFlowable(
                functionUnitName, version, bpmnXml);
        
        // Then: The process definition key should follow the format {functionUnitName}_v{version}
        String expectedKey = String.format("%s_v%s", functionUnitName, version);
        assertThat(processDefinitionKey).isEqualTo(expectedKey);
        assertThat(capturedProcessKey[0]).isEqualTo(expectedKey);
        
        // And: The key should contain the version number
        assertThat(processDefinitionKey).contains("_v" + version);
        
        // And: The key should start with the function unit name
        assertThat(processDefinitionKey).startsWith(functionUnitName);
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
}
