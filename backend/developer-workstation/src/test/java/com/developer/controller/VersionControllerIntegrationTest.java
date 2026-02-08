package com.developer.controller;

import com.developer.dto.*;
import com.developer.entity.FunctionUnit;
import com.developer.service.DeploymentService;
import com.developer.service.RollbackService;
import com.developer.service.UIService;
import com.developer.service.VersionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.exception.VersionValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for VersionController API endpoints.
 * Tests the full deployment flow, version management, and rollback operations via REST API.
 * 
 * Requirements: 1.1, 2.5, 3.1, 3.2, 3.3, 6.2, 7.1
 */
@WebMvcTest(VersionController.class)
class VersionControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private DeploymentService deploymentService;
    
    @MockBean
    private VersionService versionService;
    
    @MockBean
    private RollbackService rollbackService;
    
    @MockBean
    private UIService uiService;
    
    private FunctionUnit testFunctionUnit;
    private DeploymentResult testDeploymentResult;
    
    @BeforeEach
    void setUp() {
        testFunctionUnit = FunctionUnit.builder()
                .id(1L)
                .name("TestFunctionUnit")
                .version("1.0.0")
                .isActive(true)
                .deployedAt(Instant.now())
                .build();
        
        testDeploymentResult = DeploymentResult.builder()
                .success(true)
                .versionId(1L)
                .version("1.0.0")
                .processDefinitionKey("TestFunctionUnit_v1.0.0")
                .deployedAt(Instant.now())
                .build();
    }
    
    @Test
    void testDeployFunctionUnit_Success() throws Exception {
        // Arrange
        DeploymentRequest request = DeploymentRequest.builder()
                .bpmnXml("<bpmn>test</bpmn>")
                .changeType("minor")
                .build();
        
        when(deploymentService.deployFunctionUnit(
                eq("TestFunctionUnit"),
                eq(request.getBpmnXml()),
                eq(request.getChangeType()),
                any()
        )).thenReturn(testDeploymentResult);
        
        // Act & Assert
        mockMvc.perform(post("/api/function-units/TestFunctionUnit/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.version").value("1.0.0"))
                .andExpect(jsonPath("$.data.processDefinitionKey").value("TestFunctionUnit_v1.0.0"));
    }
    
    @Test
    void testDeployFunctionUnit_ValidationError() throws Exception {
        // Arrange
        DeploymentRequest request = DeploymentRequest.builder()
                .bpmnXml("<bpmn>test</bpmn>")
                .changeType("minor")
                .build();
        
        when(deploymentService.deployFunctionUnit(
                eq("TestFunctionUnit"),
                eq(request.getBpmnXml()),
                eq(request.getChangeType()),
                any()
        )).thenThrow(VersionValidationError.duplicateVersion("TestFunctionUnit", "1.0.0"));
        
        // Act & Assert
        mockMvc.perform(post("/api/function-units/TestFunctionUnit/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
    
    @Test
    void testDeployFunctionUnit_InvalidChangeType() throws Exception {
        // Arrange
        DeploymentRequest request = DeploymentRequest.builder()
                .bpmnXml("<bpmn>test</bpmn>")
                .changeType("invalid")
                .build();
        
        // Act & Assert
        mockMvc.perform(post("/api/function-units/TestFunctionUnit/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void testGetVersionHistory_Success() throws Exception {
        // Arrange
        List<FunctionUnit> versions = Arrays.asList(
                FunctionUnit.builder().id(2L).name("TestFunctionUnit").version("1.1.0").isActive(true).build(),
                FunctionUnit.builder().id(1L).name("TestFunctionUnit").version("1.0.0").isActive(false).build()
        );
        
        when(versionService.getVersionHistory("TestFunctionUnit")).thenReturn(versions);
        
        // Act & Assert
        mockMvc.perform(get("/api/function-units/TestFunctionUnit/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].version").value("1.1.0"))
                .andExpect(jsonPath("$.data[1].version").value("1.0.0"));
    }
    
    @Test
    void testGetActiveVersion_Success() throws Exception {
        // Arrange
        when(versionService.getActiveVersion("TestFunctionUnit")).thenReturn(testFunctionUnit);
        
        // Act & Assert
        mockMvc.perform(get("/api/function-units/TestFunctionUnit/versions/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.version").value("1.0.0"))
                .andExpect(jsonPath("$.data.isActive").value(true));
    }
    
    @Test
    void testGetActiveVersion_NotFound() throws Exception {
        // Arrange
        when(versionService.getActiveVersion("NonExistent")).thenReturn(null);
        
        // Act & Assert
        mockMvc.perform(get("/api/function-units/NonExistent/versions/active"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }
    
    @Test
    void testRollback_RequiresConfirmation() throws Exception {
        // Arrange
        RollbackRequest request = RollbackRequest.builder()
                .targetVersion("1.0.0")
                .confirmed(false)
                .build();
        
        List<FunctionUnit> versions = Arrays.asList(
                FunctionUnit.builder().id(2L).name("TestFunctionUnit").version("1.1.0").isActive(true).build(),
                FunctionUnit.builder().id(1L).name("TestFunctionUnit").version("1.0.0").isActive(false).build()
        );
        
        RollbackImpact impact = RollbackImpact.builder()
                .targetVersion("1.0.0")
                .targetVersionId(1L)
                .versionsToDelete(Arrays.asList("1.1.0"))
                .totalProcessInstancesToDelete(5L)
                .canProceed(true)
                .build();
        
        when(versionService.getVersionHistory("TestFunctionUnit")).thenReturn(versions);
        when(rollbackService.calculateRollbackImpact(1L)).thenReturn(impact);
        
        // Act & Assert
        mockMvc.perform(post("/api/function-units/TestFunctionUnit/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CONFIRMATION_REQUIRED"));
    }
    
    @Test
    void testRollback_Success() throws Exception {
        // Arrange
        RollbackRequest request = RollbackRequest.builder()
                .targetVersion("1.0.0")
                .confirmed(true)
                .build();
        
        List<FunctionUnit> versions = Arrays.asList(
                FunctionUnit.builder().id(2L).name("TestFunctionUnit").version("1.1.0").isActive(true).build(),
                FunctionUnit.builder().id(1L).name("TestFunctionUnit").version("1.0.0").isActive(false).build()
        );
        
        RollbackImpact impact = RollbackImpact.builder()
                .targetVersion("1.0.0")
                .targetVersionId(1L)
                .versionsToDelete(Arrays.asList("1.1.0"))
                .totalProcessInstancesToDelete(5L)
                .canProceed(true)
                .build();
        
        RollbackResult result = RollbackResult.builder()
                .success(true)
                .rolledBackToVersion("1.0.0")
                .deletedVersions(Arrays.asList("1.1.0"))
                .deletedProcessCount(5)
                .build();
        
        when(versionService.getVersionHistory("TestFunctionUnit")).thenReturn(versions);
        when(rollbackService.calculateRollbackImpact(1L)).thenReturn(impact);
        when(rollbackService.rollbackToVersion(1L)).thenReturn(result);
        
        // Act & Assert
        mockMvc.perform(post("/api/function-units/TestFunctionUnit/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rolledBackToVersion").value("1.0.0"))
                .andExpect(jsonPath("$.data.deletedVersions[0]").value("1.1.0"))
                .andExpect(jsonPath("$.data.deletedProcessCount").value(5));
    }
    
    @Test
    void testRollback_VersionNotFound() throws Exception {
        // Arrange
        RollbackRequest request = RollbackRequest.builder()
                .targetVersion("9.9.9")
                .confirmed(true)
                .build();
        
        List<FunctionUnit> versions = Arrays.asList(
                FunctionUnit.builder().id(1L).name("TestFunctionUnit").version("1.0.0").isActive(true).build()
        );
        
        when(versionService.getVersionHistory("TestFunctionUnit")).thenReturn(versions);
        
        // Act & Assert
        mockMvc.perform(post("/api/function-units/TestFunctionUnit/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
    
    @Test
    void testGetFunctionUnitsForDisplay_Success() throws Exception {
        // Arrange
        List<FunctionUnitDisplay> displays = Arrays.asList(
                FunctionUnitDisplay.builder()
                        .functionUnitName("TestFunctionUnit1")
                        .currentVersion("1.0.0")
                        .versionCount(1)
                        .build(),
                FunctionUnitDisplay.builder()
                        .functionUnitName("TestFunctionUnit2")
                        .currentVersion("2.1.0")
                        .versionCount(3)
                        .build()
        );
        
        when(uiService.getFunctionUnitsForDisplay()).thenReturn(displays);
        
        // Act & Assert
        mockMvc.perform(get("/api/function-units"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].functionUnitName").value("TestFunctionUnit1"))
                .andExpect(jsonPath("$.data[1].versionCount").value(3));
    }
    
    @Test
    void testGetVersionHistoryForUI_Success() throws Exception {
        // Arrange
        VersionHistoryDisplay history = VersionHistoryDisplay.builder()
                .functionUnitName("TestFunctionUnit")
                .versions(Arrays.asList(
                        VersionHistoryEntry.builder()
                                .version("1.1.0")
                                .isActive(true)
                                .processInstanceCount(10L)
                                .canRollback(false)
                                .build(),
                        VersionHistoryEntry.builder()
                                .version("1.0.0")
                                .isActive(false)
                                .processInstanceCount(5L)
                                .canRollback(true)
                                .build()
                ))
                .build();
        
        when(uiService.getVersionHistoryForUI("TestFunctionUnit")).thenReturn(history);
        
        // Act & Assert
        mockMvc.perform(get("/api/function-units/TestFunctionUnit/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.functionUnitName").value("TestFunctionUnit"))
                .andExpect(jsonPath("$.data.versions").isArray())
                .andExpect(jsonPath("$.data.versions.length()").value(2))
                .andExpect(jsonPath("$.data.versions[0].version").value("1.1.0"))
                .andExpect(jsonPath("$.data.versions[0].isActive").value(true))
                .andExpect(jsonPath("$.data.versions[1].canRollback").value(true));
    }
}
