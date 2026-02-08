package com.developer.service;

import com.developer.client.WorkflowEngineClient;
import com.developer.dto.DeploymentResult;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitRepository;
import com.platform.common.exception.TransactionError;
import com.platform.common.exception.VersionValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DeploymentService.
 * Tests specific deployment scenarios and edge cases.
 */
@ExtendWith(MockitoExtension.class)
class DeploymentServiceTest {
    
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
        deploymentService = new DeploymentService(
                functionUnitRepository,
                versionService,
                permissionService,
                workflowEngineClient
        );
    }
    
    @Test
    void deployBPMNToFlowable_shouldGenerateVersionedKey() {
        // Given
        String functionUnitName = "purchase-approval";
        String version = "1.2.3";
        String bpmnXml = "<bpmn>test</bpmn>";
        
        Map<String, Object> flowableResponse = new HashMap<>();
        flowableResponse.put("processDefinitionId", "test-id");
        flowableResponse.put("processDefinitionKey", "purchase-approval_v1.2.3");
        
        when(workflowEngineClient.deployProcess(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(flowableResponse));
        
        // When
        String processDefinitionKey = deploymentService.deployBPMNToFlowable(
                functionUnitName, version, bpmnXml);
        
        // Then
        assertThat(processDefinitionKey).isEqualTo("purchase-approval_v1.2.3");
        
        // Verify the correct parameters were passed to Flowable
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> xmlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        
        verify(workflowEngineClient).deployProcess(
                keyCaptor.capture(), 
                xmlCaptor.capture(), 
                nameCaptor.capture());
        
        assertThat(keyCaptor.getValue()).isEqualTo("purchase-approval_v1.2.3");
        assertThat(xmlCaptor.getValue()).isEqualTo(bpmnXml);
        assertThat(nameCaptor.getValue()).contains("purchase-approval");
        assertThat(nameCaptor.getValue()).contains("1.2.3");
    }
    
    @Test
    void deployBPMNToFlowable_shouldThrowException_whenFlowableUnavailable() {
        // Given
        String functionUnitName = "test-unit";
        String version = "1.0.0";
        String bpmnXml = "<bpmn>test</bpmn>";
        
        when(workflowEngineClient.deployProcess(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> deploymentService.deployBPMNToFlowable(
                functionUnitName, version, bpmnXml))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("workflow engine unavailable");
    }
    
    @Test
    void deployFunctionUnit_shouldCreateFirstVersion() {
        // Given: First deployment (no previous version)
        String functionUnitName = "leave-management";
        String bpmnXml = "<bpmn>test</bpmn>";
        String changeType = "major";
        Map<String, Object> metadata = new HashMap<>();
        
        // Mock version service
        when(versionService.generateNextVersion(functionUnitName, changeType))
                .thenReturn("1.0.0");
        when(versionService.versionExists(functionUnitName, "1.0.0"))
                .thenReturn(false);
        when(versionService.getActiveVersion(functionUnitName))
                .thenThrow(new ResourceNotFoundException("FunctionUnit", functionUnitName));
        
        // Mock function unit repository
        FunctionUnit newVersion = FunctionUnit.builder()
                .id(1L)
                .name(functionUnitName)
                .version("1.0.0")
                .isActive(false)
                .deployedAt(Instant.now())
                .build();
        
        when(functionUnitRepository.save(any(FunctionUnit.class)))
                .thenReturn(newVersion);
        when(functionUnitRepository.findById(1L))
                .thenReturn(Optional.of(newVersion));
        
        // Mock Flowable deployment
        Map<String, Object> flowableResponse = new HashMap<>();
        flowableResponse.put("processDefinitionKey", "leave-management_v1.0.0");
        when(workflowEngineClient.deployProcess(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(flowableResponse));
        
        // When
        DeploymentResult result = deploymentService.deployFunctionUnit(
                functionUnitName, bpmnXml, changeType, metadata);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getVersion()).isEqualTo("1.0.0");
        assertThat(result.getProcessDefinitionKey()).isEqualTo("leave-management_v1.0.0");
        
        // Verify version service was called
        verify(versionService).generateNextVersion(functionUnitName, changeType);
        verify(versionService).versionExists(functionUnitName, "1.0.0");
        verify(versionService).activateVersion(1L);
        
        // Verify permission service was NOT called (no previous version)
        verify(permissionService, never()).copyPermissions(anyLong(), anyLong());
        
        // Verify Flowable deployment
        verify(workflowEngineClient).deployProcess(
                eq("leave-management_v1.0.0"), 
                eq(bpmnXml), 
                anyString());
    }
    
    @Test
    void deployFunctionUnit_shouldCopyPermissionsFromPreviousVersion() {
        // Given: Deployment with existing version
        String functionUnitName = "expense-approval";
        String bpmnXml = "<bpmn>test</bpmn>";
        String changeType = "minor";
        Map<String, Object> metadata = new HashMap<>();
        
        // Mock previous active version
        FunctionUnit previousVersion = FunctionUnit.builder()
                .id(1L)
                .name(functionUnitName)
                .version("1.0.0")
                .isActive(true)
                .deployedAt(Instant.now())
                .build();
        
        // Mock new version
        FunctionUnit newVersion = FunctionUnit.builder()
                .id(2L)
                .name(functionUnitName)
                .version("1.1.0")
                .isActive(false)
                .deployedAt(Instant.now())
                .previousVersion(previousVersion)
                .build();
        
        // Mock version service
        when(versionService.generateNextVersion(functionUnitName, changeType))
                .thenReturn("1.1.0");
        when(versionService.versionExists(functionUnitName, "1.1.0"))
                .thenReturn(false);
        when(versionService.getActiveVersion(functionUnitName))
                .thenReturn(previousVersion);
        
        // Mock function unit repository
        when(functionUnitRepository.save(any(FunctionUnit.class)))
                .thenReturn(newVersion);
        when(functionUnitRepository.findById(2L))
                .thenReturn(Optional.of(newVersion));
        
        // Mock Flowable deployment
        Map<String, Object> flowableResponse = new HashMap<>();
        flowableResponse.put("processDefinitionKey", "expense-approval_v1.1.0");
        when(workflowEngineClient.deployProcess(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(flowableResponse));
        
        // Mock permission service
        when(permissionService.copyPermissions(1L, 2L))
                .thenReturn(5);
        
        // When
        DeploymentResult result = deploymentService.deployFunctionUnit(
                functionUnitName, bpmnXml, changeType, metadata);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getVersion()).isEqualTo("1.1.0");
        
        // Verify permissions were copied
        verify(permissionService).copyPermissions(1L, 2L);
        
        // Verify version was activated
        verify(versionService).activateVersion(2L);
    }
    
    @Test
    void deployFunctionUnit_shouldThrowException_whenVersionAlreadyExists() {
        // Given
        String functionUnitName = "test-unit";
        String bpmnXml = "<bpmn>test</bpmn>";
        String changeType = "patch";
        Map<String, Object> metadata = new HashMap<>();
        
        when(versionService.generateNextVersion(functionUnitName, changeType))
                .thenReturn("1.0.1");
        when(versionService.versionExists(functionUnitName, "1.0.1"))
                .thenReturn(true);
        
        // When/Then
        assertThatThrownBy(() -> deploymentService.deployFunctionUnit(
                functionUnitName, bpmnXml, changeType, metadata))
                .isInstanceOf(VersionValidationError.class)
                .hasMessageContaining("already exists");
        
        // Verify no deployment occurred
        verify(functionUnitRepository, never()).save(any());
        verify(workflowEngineClient, never()).deployProcess(anyString(), anyString(), anyString());
        verify(versionService, never()).activateVersion(anyLong());
    }
    
    @Test
    void deployFunctionUnit_shouldRollback_whenFlowableDeploymentFails() {
        // Given
        String functionUnitName = "test-unit";
        String bpmnXml = "<bpmn>test</bpmn>";
        String changeType = "major";
        Map<String, Object> metadata = new HashMap<>();
        
        FunctionUnit newVersion = FunctionUnit.builder()
                .id(1L)
                .name(functionUnitName)
                .version("1.0.0")
                .isActive(false)
                .deployedAt(Instant.now())
                .build();
        
        when(versionService.generateNextVersion(functionUnitName, changeType))
                .thenReturn("1.0.0");
        when(versionService.versionExists(functionUnitName, "1.0.0"))
                .thenReturn(false);
        when(versionService.getActiveVersion(functionUnitName))
                .thenThrow(new ResourceNotFoundException("FunctionUnit", functionUnitName));
        when(functionUnitRepository.save(any(FunctionUnit.class)))
                .thenReturn(newVersion);
        
        // Mock Flowable deployment failure
        when(workflowEngineClient.deployProcess(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> deploymentService.deployFunctionUnit(
                functionUnitName, bpmnXml, changeType, metadata))
                .isInstanceOf(TransactionError.class)
                .hasMessageContaining("BPMN deployment to Flowable");
        
        // Verify version was not activated
        verify(versionService, never()).activateVersion(anyLong());
    }
    
    @Test
    void deployFunctionUnit_shouldRollback_whenPermissionCopyFails() {
        // Given
        String functionUnitName = "test-unit";
        String bpmnXml = "<bpmn>test</bpmn>";
        String changeType = "minor";
        Map<String, Object> metadata = new HashMap<>();
        
        FunctionUnit previousVersion = FunctionUnit.builder()
                .id(1L)
                .name(functionUnitName)
                .version("1.0.0")
                .isActive(true)
                .build();
        
        FunctionUnit newVersion = FunctionUnit.builder()
                .id(2L)
                .name(functionUnitName)
                .version("1.1.0")
                .isActive(false)
                .build();
        
        when(versionService.generateNextVersion(functionUnitName, changeType))
                .thenReturn("1.1.0");
        when(versionService.versionExists(functionUnitName, "1.1.0"))
                .thenReturn(false);
        when(versionService.getActiveVersion(functionUnitName))
                .thenReturn(previousVersion);
        when(functionUnitRepository.save(any(FunctionUnit.class)))
                .thenReturn(newVersion);
        
        Map<String, Object> flowableResponse = new HashMap<>();
        flowableResponse.put("processDefinitionKey", "test-unit_v1.1.0");
        when(workflowEngineClient.deployProcess(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(flowableResponse));
        
        // Mock permission copy failure
        when(permissionService.copyPermissions(1L, 2L))
                .thenThrow(new RuntimeException("Permission copy failed"));
        
        // When/Then
        assertThatThrownBy(() -> deploymentService.deployFunctionUnit(
                functionUnitName, bpmnXml, changeType, metadata))
                .isInstanceOf(TransactionError.class)
                .hasMessageContaining("Permission copying");
        
        // Verify version was not activated
        verify(versionService, never()).activateVersion(anyLong());
    }
    
    @Test
    void deployFunctionUnit_shouldRollback_whenVersionActivationFails() {
        // Given
        String functionUnitName = "test-unit";
        String bpmnXml = "<bpmn>test</bpmn>";
        String changeType = "patch";
        Map<String, Object> metadata = new HashMap<>();
        
        FunctionUnit newVersion = FunctionUnit.builder()
                .id(1L)
                .name(functionUnitName)
                .version("1.0.0")
                .isActive(false)
                .build();
        
        when(versionService.generateNextVersion(functionUnitName, changeType))
                .thenReturn("1.0.0");
        when(versionService.versionExists(functionUnitName, "1.0.0"))
                .thenReturn(false);
        when(versionService.getActiveVersion(functionUnitName))
                .thenThrow(new ResourceNotFoundException("FunctionUnit", functionUnitName));
        when(functionUnitRepository.save(any(FunctionUnit.class)))
                .thenReturn(newVersion);
        
        Map<String, Object> flowableResponse = new HashMap<>();
        flowableResponse.put("processDefinitionKey", "test-unit_v1.0.0");
        when(workflowEngineClient.deployProcess(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(flowableResponse));
        
        // Mock version activation failure
        doThrow(new RuntimeException("Activation failed"))
                .when(versionService).activateVersion(1L);
        
        // When/Then
        assertThatThrownBy(() -> deploymentService.deployFunctionUnit(
                functionUnitName, bpmnXml, changeType, metadata))
                .isInstanceOf(TransactionError.class)
                .hasMessageContaining("Version activation");
    }
}
