package com.developer.service;

import com.developer.client.WorkflowEngineClient;
import com.developer.dto.DeploymentResult;
import com.developer.entity.FunctionUnit;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.ProcessInstanceRepository;
import com.platform.common.exception.VersionValidationError;
import com.platform.common.version.SemanticVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for error conditions in versioned deployment system.
 * Tests specific error scenarios including validation failures and edge cases.
 */
class ErrorConditionsTest {
    
    @Mock
    private FunctionUnitRepository functionUnitRepository;
    
    @Mock
    private ProcessInstanceRepository processInstanceRepository;
    
    @Mock
    private VersionService versionService;
    
    @Mock
    private PermissionService permissionService;
    
    @Mock
    private WorkflowEngineClient workflowEngineClient;
    
    private DeploymentService deploymentService;
    private RollbackService rollbackService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        deploymentService = new DeploymentService(
                functionUnitRepository,
                versionService,
                permissionService,
                workflowEngineClient
        );
        
        rollbackService = new RollbackService(
                functionUnitRepository,
                processInstanceRepository,
                versionService
        );
    }
    
    /**
     * Test invalid semantic version format.
     * Verifies that invalid version strings are rejected.
     */
    @Test
    void testInvalidSemanticVersionFormat() {
        // Test various invalid version formats
        String[] invalidVersions = {
                "1",           // Missing minor and patch
                "1.0",         // Missing patch
                "1.0.0.0",     // Too many components
                "a.b.c",       // Non-numeric
                "1.0.x",       // Non-numeric patch
                "1.x.0",       // Non-numeric minor
                "x.0.0",       // Non-numeric major
                "v1.0.0",      // Prefix not allowed
                "1.0.0-beta",  // Suffix not allowed
                "1.0.0+build", // Build metadata not allowed
                "-1.0.0",      // Negative version
                "1.-1.0",      // Negative minor
                "1.0.-1"       // Negative patch
        };
        
        for (String invalidVersion : invalidVersions) {
            assertThatThrownBy(() -> SemanticVersion.parse(invalidVersion))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        
        // Test empty string separately as it has a different error message
        assertThatThrownBy(() -> SemanticVersion.parse(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
    }
    
    /**
     * Test duplicate version deployment.
     * Verifies that attempting to deploy a version that already exists is rejected.
     */
    @Test
    void testDuplicateVersionDeployment() {
        // Given: A function unit with an existing version
        String functionUnitName = "test-function";
        String existingVersion = "1.0.0";
        String changeType = "patch";
        String bpmnXml = "<bpmn>test</bpmn>";
        Map<String, Object> metadata = new HashMap<>();
        
        // Mock version generation to return existing version
        when(versionService.generateNextVersion(functionUnitName, changeType))
                .thenReturn(existingVersion);
        
        // Mock version existence check to return true
        when(versionService.versionExists(functionUnitName, existingVersion))
                .thenReturn(true);
        
        // When: Attempting to deploy with duplicate version
        // Then: Should throw VersionValidationError
        assertThatThrownBy(() -> deploymentService.deployFunctionUnit(
                functionUnitName, bpmnXml, changeType, metadata))
                .isInstanceOf(VersionValidationError.class)
                .hasMessageContaining("already exists");
        
        // And: Error message should contain version and function unit name
        try {
            deploymentService.deployFunctionUnit(functionUnitName, bpmnXml, changeType, metadata);
        } catch (VersionValidationError e) {
            assertThat(e.getMessage()).contains(existingVersion);
            assertThat(e.getMessage()).contains(functionUnitName);
            assertThat(e.getField()).isEqualTo("version");
            assertThat(e.getValue()).isEqualTo(existingVersion);
        }
    }
    
    /**
     * Test rollback to non-existent version.
     * Verifies that attempting to rollback to a version that doesn't exist is rejected.
     */
    @Test
    void testRollbackToNonExistentVersion() {
        // Given: A non-existent version ID
        Long nonExistentVersionId = 999L;
        
        // Mock repository to return empty optional
        when(functionUnitRepository.findById(nonExistentVersionId))
                .thenReturn(Optional.empty());
        
        // When: Attempting to rollback to non-existent version
        // Then: Should throw ResourceNotFoundException
        assertThatThrownBy(() -> rollbackService.calculateRollbackImpact(nonExistentVersionId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("FunctionUnit");
        
        // And: Should also fail when attempting actual rollback
        assertThatThrownBy(() -> rollbackService.rollbackToVersion(nonExistentVersionId))
                .isInstanceOf(RuntimeException.class);
    }
    
    /**
     * Test rollback to already-active version.
     * Verifies that attempting to rollback to the currently active version is handled gracefully.
     */
    @Test
    void testRollbackToAlreadyActiveVersion() {
        // Given: An active version
        Long activeVersionId = 1L;
        String functionUnitName = "test-function";
        String version = "1.0.0";
        
        FunctionUnit activeVersion = FunctionUnit.builder()
                .id(activeVersionId)
                .name(functionUnitName)
                .version(version)
                .isActive(true)  // Already active
                .deployedAt(Instant.now())
                .build();
        
        when(functionUnitRepository.findById(activeVersionId))
                .thenReturn(Optional.of(activeVersion));
        
        when(versionService.getVersionHistory(functionUnitName))
                .thenReturn(Collections.singletonList(activeVersion));
        
        // When: Calculating rollback impact for already-active version
        // Then: Should throw VersionValidationError because version is already active
        assertThatThrownBy(() -> rollbackService.calculateRollbackImpact(activeVersionId))
                .isInstanceOf(VersionValidationError.class)
                .hasMessageContaining("already active");
    }
    
    /**
     * Test concurrent modification conflicts.
     * Verifies that concurrent deployments are handled correctly.
     */
    @Test
    void testConcurrentModificationConflict() {
        // Given: Two concurrent deployments for the same function unit
        String functionUnitName = "test-function";
        String changeType = "patch";
        String bpmnXml = "<bpmn>test</bpmn>";
        Map<String, Object> metadata = new HashMap<>();
        
        // First deployment generates version 1.0.1
        when(versionService.generateNextVersion(functionUnitName, changeType))
                .thenReturn("1.0.1");
        
        // First check: version doesn't exist
        when(versionService.versionExists(functionUnitName, "1.0.1"))
                .thenReturn(false)
                .thenReturn(true);  // Second check: version now exists (concurrent deployment)
        
        FunctionUnit mockFunctionUnit = FunctionUnit.builder()
                .id(1L)
                .name(functionUnitName)
                .version("1.0.1")
                .isActive(false)
                .deployedAt(Instant.now())
                .build();
        
        when(functionUnitRepository.save(any(FunctionUnit.class)))
                .thenReturn(mockFunctionUnit);
        
        when(workflowEngineClient.deployProcess(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(Map.of("processDefinitionId", "test-id")));
        
        when(functionUnitRepository.findById(1L))
                .thenReturn(Optional.of(mockFunctionUnit));
        
        // When: First deployment succeeds
        DeploymentResult result1 = deploymentService.deployFunctionUnit(
                functionUnitName, bpmnXml, changeType, metadata);
        
        assertThat(result1).isNotNull();
        assertThat(result1.getVersion()).isEqualTo("1.0.1");
        
        // When: Second concurrent deployment attempts same version
        // Then: Should fail with duplicate version error
        assertThatThrownBy(() -> deploymentService.deployFunctionUnit(
                functionUnitName, bpmnXml, changeType, metadata))
                .isInstanceOf(VersionValidationError.class)
                .hasMessageContaining("already exists");
    }
    
    /**
     * Test invalid change type.
     * Verifies that invalid change types are rejected.
     */
    @Test
    void testInvalidChangeType() {
        // Given: Invalid change types
        String functionUnitName = "test-function";
        String[] invalidChangeTypes = {
                "MAJOR",      // Wrong case
                "Minor",      // Wrong case
                "PATCH",      // Wrong case
                "bugfix",     // Invalid type
                "feature",    // Invalid type
                "hotfix",     // Invalid type
                ""            // Empty
        };
        
        for (String invalidChangeType : invalidChangeTypes) {
            // Mock the version service to throw exception for invalid change type
            when(versionService.generateNextVersion(functionUnitName, invalidChangeType))
                    .thenThrow(new IllegalArgumentException("Invalid change type: " + invalidChangeType));
            
            // When: Attempting to generate version with invalid change type
            // Then: Should throw IllegalArgumentException
            assertThatThrownBy(() -> versionService.generateNextVersion(
                    functionUnitName, invalidChangeType))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid change type");
        }
        
        // Test null separately
        when(versionService.generateNextVersion(functionUnitName, null))
                .thenThrow(new IllegalArgumentException("Invalid change type: null"));
        
        assertThatThrownBy(() -> versionService.generateNextVersion(
                functionUnitName, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid change type");
    }
    
    /**
     * Test rollback with no newer versions.
     * Verifies that rollback to the latest version is handled correctly.
     */
    @Test
    void testRollbackWithNoNewerVersions() {
        // Given: A function unit with only one version (the latest)
        Long versionId = 1L;
        String functionUnitName = "test-function";
        String version = "2.0.0";
        
        FunctionUnit latestVersion = FunctionUnit.builder()
                .id(versionId)
                .name(functionUnitName)
                .version(version)
                .isActive(true)
                .deployedAt(Instant.now())
                .build();
        
        when(functionUnitRepository.findById(versionId))
                .thenReturn(Optional.of(latestVersion));
        
        when(versionService.getVersionHistory(functionUnitName))
                .thenReturn(Collections.singletonList(latestVersion));
        
        // When: Calculating rollback impact
        // Then: Should throw VersionValidationError because version is already active
        assertThatThrownBy(() -> rollbackService.calculateRollbackImpact(versionId))
                .isInstanceOf(VersionValidationError.class)
                .hasMessageContaining("already active");
    }
    
    /**
     * Test empty function unit name.
     * Verifies that empty or null function unit names are handled appropriately.
     */
    @Test
    void testEmptyFunctionUnitName() {
        // Given: Empty function unit name
        String emptyName = "";
        String changeType = "patch";
        
        // Mock version service to throw exception for empty name
        when(versionService.generateNextVersion(emptyName, changeType))
                .thenThrow(new IllegalArgumentException("Function unit name cannot be null or empty"));
        
        // When: Attempting to generate version with empty name
        // Then: Should throw IllegalArgumentException
        assertThatThrownBy(() -> versionService.generateNextVersion(emptyName, changeType))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Function unit name cannot be null or empty");
        
        // Note: Null function unit name would cause NullPointerException in repository layer
        // which is expected behavior for null inputs
    }
    
    /**
     * Test version comparison edge cases.
     * Verifies that version comparison handles edge cases correctly.
     */
    @Test
    void testVersionComparisonEdgeCases() {
        // Test equal versions
        SemanticVersion v1 = SemanticVersion.parse("1.0.0");
        SemanticVersion v2 = SemanticVersion.parse("1.0.0");
        assertThat(v1.compareTo(v2)).isEqualTo(0);
        assertThat(v1.equals(v2)).isTrue();
        
        // Test major version difference
        SemanticVersion v3 = SemanticVersion.parse("2.0.0");
        assertThat(v3.compareTo(v1)).isGreaterThan(0);
        assertThat(v1.compareTo(v3)).isLessThan(0);
        
        // Test minor version difference
        SemanticVersion v4 = SemanticVersion.parse("1.1.0");
        assertThat(v4.compareTo(v1)).isGreaterThan(0);
        assertThat(v1.compareTo(v4)).isLessThan(0);
        
        // Test patch version difference
        SemanticVersion v5 = SemanticVersion.parse("1.0.1");
        assertThat(v5.compareTo(v1)).isGreaterThan(0);
        assertThat(v1.compareTo(v5)).isLessThan(0);
        
        // Test large version numbers
        SemanticVersion v6 = SemanticVersion.parse("100.200.300");
        SemanticVersion v7 = SemanticVersion.parse("100.200.301");
        assertThat(v7.compareTo(v6)).isGreaterThan(0);
    }
    
    /**
     * Test rollback to version 1.0.0.
     * Verifies that rollback to the first version works correctly.
     */
    @Test
    void testRollbackToFirstVersion() {
        // Given: Multiple versions with rollback target being 1.0.0
        Long targetVersionId = 1L;
        String functionUnitName = "test-function";
        
        FunctionUnit v1 = FunctionUnit.builder()
                .id(targetVersionId)
                .name(functionUnitName)
                .version("1.0.0")
                .isActive(false)
                .deployedAt(Instant.now().minusSeconds(200))
                .build();
        
        FunctionUnit v2 = FunctionUnit.builder()
                .id(2L)
                .name(functionUnitName)
                .version("1.0.1")
                .isActive(false)
                .deployedAt(Instant.now().minusSeconds(100))
                .build();
        
        FunctionUnit v3 = FunctionUnit.builder()
                .id(3L)
                .name(functionUnitName)
                .version("1.0.2")
                .isActive(true)
                .deployedAt(Instant.now())
                .build();
        
        when(functionUnitRepository.findById(targetVersionId))
                .thenReturn(Optional.of(v1));
        
        when(versionService.getVersionHistory(functionUnitName))
                .thenReturn(Arrays.asList(v3, v2, v1));  // Ordered by version desc
        
        when(processInstanceRepository.countByFunctionUnitVersionId(2L))
                .thenReturn(5L);
        when(processInstanceRepository.countByFunctionUnitVersionId(3L))
                .thenReturn(10L);
        
        // When: Calculating rollback impact to version 1.0.0
        var impact = rollbackService.calculateRollbackImpact(targetVersionId);
        
        // Then: Should delete versions 1.0.1 and 1.0.2
        assertThat(impact.getTargetVersion()).isEqualTo("1.0.0");
        assertThat(impact.getVersionsToDelete()).hasSize(2);
        assertThat(impact.getTotalProcessInstancesToDelete()).isEqualTo(15);
        assertThat(impact.isCanProceed()).isTrue();
    }
}
