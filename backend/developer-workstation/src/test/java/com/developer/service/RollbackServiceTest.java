package com.developer.service;

import com.developer.dto.RollbackImpact;
import com.developer.dto.RollbackResult;
import com.developer.entity.FunctionUnit;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.ProcessInstanceRepository;
import com.platform.common.exception.TransactionError;
import com.platform.common.exception.VersionValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RollbackService.
 * Tests specific rollback scenarios and edge cases.
 */
@ExtendWith(MockitoExtension.class)
class RollbackServiceTest {
    
    @Mock
    private FunctionUnitRepository functionUnitRepository;
    
    @Mock
    private ProcessInstanceRepository processInstanceRepository;
    
    @Mock
    private VersionService versionService;
    
    private RollbackService rollbackService;
    
    @BeforeEach
    void setUp() {
        rollbackService = new RollbackService(
                functionUnitRepository,
                processInstanceRepository,
                versionService
        );
    }
    
    /**
     * Test rollback to version 1.0.0 (the first version)
     */
    @Test
    void rollbackToVersion_shouldSucceed_whenRollingBackToFirstVersion() {
        // Given: Three versions exist (3.0.0, 2.0.0, 1.0.0)
        String functionUnitName = "test-function";
        
        FunctionUnit version1 = createFunctionUnit(1L, functionUnitName, "1.0.0", false);
        FunctionUnit version2 = createFunctionUnit(2L, functionUnitName, "2.0.0", false);
        FunctionUnit version3 = createFunctionUnit(3L, functionUnitName, "3.0.0", true);
        
        List<FunctionUnit> allVersions = List.of(version3, version2, version1);
        
        // Mock repository calls
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(version1));
        when(versionService.getVersionHistory(functionUnitName)).thenReturn(allVersions);
        when(processInstanceRepository.countByFunctionUnitVersionId(anyLong())).thenReturn(0L);
        doNothing().when(processInstanceRepository).deleteByFunctionUnitVersionId(anyLong());
        doNothing().when(functionUnitRepository).deleteById(anyLong());
        doNothing().when(versionService).activateVersion(1L);
        
        // When: Rolling back to version 1.0.0
        RollbackResult result = rollbackService.rollbackToVersion(1L);
        
        // Then: Rollback should succeed
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRolledBackToVersion()).isEqualTo("1.0.0");
        assertThat(result.getRolledBackToVersionId()).isEqualTo(1L);
        
        // And: Both newer versions should be deleted
        assertThat(result.getDeletedVersions()).containsExactlyInAnyOrder("2.0.0", "3.0.0");
        verify(functionUnitRepository).deleteById(2L);
        verify(functionUnitRepository).deleteById(3L);
        
        // And: Version 1.0.0 should be activated
        verify(versionService).activateVersion(1L);
    }
    
    /**
     * Test rollback with no process instances
     */
    @Test
    void rollbackToVersion_shouldSucceed_whenNoProcessInstancesExist() {
        // Given: Two versions exist with no process instances
        String functionUnitName = "test-function";
        
        FunctionUnit version1 = createFunctionUnit(1L, functionUnitName, "1.0.0", false);
        FunctionUnit version2 = createFunctionUnit(2L, functionUnitName, "2.0.0", true);
        
        List<FunctionUnit> allVersions = List.of(version2, version1);
        
        // Mock repository calls - no process instances
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(version1));
        when(versionService.getVersionHistory(functionUnitName)).thenReturn(allVersions);
        when(processInstanceRepository.countByFunctionUnitVersionId(anyLong())).thenReturn(0L);
        doNothing().when(processInstanceRepository).deleteByFunctionUnitVersionId(anyLong());
        doNothing().when(functionUnitRepository).deleteById(anyLong());
        doNothing().when(versionService).activateVersion(1L);
        
        // When: Rolling back to version 1.0.0
        RollbackResult result = rollbackService.rollbackToVersion(1L);
        
        // Then: Rollback should succeed
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDeletedProcessCount()).isEqualTo(0L);
        
        // And: Process deletion should still be called (even though count is 0)
        verify(processInstanceRepository).deleteByFunctionUnitVersionId(2L);
    }
    
    /**
     * Test rollback validation error when target version doesn't exist
     */
    @Test
    void rollbackToVersion_shouldThrowException_whenTargetVersionNotFound() {
        // Given: Target version doesn't exist
        when(functionUnitRepository.findById(999L)).thenReturn(Optional.empty());
        
        // When/Then: Should throw ResourceNotFoundException
        assertThatThrownBy(() -> rollbackService.rollbackToVersion(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }
    
    /**
     * Test rollback validation error when target version is already active
     */
    @Test
    void rollbackToVersion_shouldThrowException_whenTargetVersionIsAlreadyActive() {
        // Given: Target version is already active
        String functionUnitName = "test-function";
        FunctionUnit activeVersion = createFunctionUnit(1L, functionUnitName, "1.0.0", true);
        
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(activeVersion));
        
        // When/Then: Should throw VersionValidationError
        assertThatThrownBy(() -> rollbackService.rollbackToVersion(1L))
                .isInstanceOf(VersionValidationError.class)
                .hasMessageContaining("already active");
    }
    
    /**
     * Test calculateRollbackImpact returns correct information
     */
    @Test
    void calculateRollbackImpact_shouldReturnCorrectImpact() {
        // Given: Three versions with process instances
        String functionUnitName = "test-function";
        
        FunctionUnit version1 = createFunctionUnit(1L, functionUnitName, "1.0.0", false);
        FunctionUnit version2 = createFunctionUnit(2L, functionUnitName, "2.0.0", false);
        FunctionUnit version3 = createFunctionUnit(3L, functionUnitName, "3.0.0", true);
        
        List<FunctionUnit> allVersions = List.of(version3, version2, version1);
        
        // Mock repository calls
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(version1));
        when(versionService.getVersionHistory(functionUnitName)).thenReturn(allVersions);
        when(processInstanceRepository.countByFunctionUnitVersionId(2L)).thenReturn(5L);
        when(processInstanceRepository.countByFunctionUnitVersionId(3L)).thenReturn(10L);
        
        // When: Calculating rollback impact
        RollbackImpact impact = rollbackService.calculateRollbackImpact(1L);
        
        // Then: Impact should be correct
        assertThat(impact.getTargetVersion()).isEqualTo("1.0.0");
        assertThat(impact.getTargetVersionId()).isEqualTo(1L);
        assertThat(impact.getVersionsToDelete()).containsExactlyInAnyOrder("2.0.0", "3.0.0");
        assertThat(impact.getVersionIdsToDelete()).containsExactlyInAnyOrder(2L, 3L);
        assertThat(impact.getTotalProcessInstancesToDelete()).isEqualTo(15L);
        assertThat(impact.isCanProceed()).isTrue();
        assertThat(impact.getWarningMessage()).contains("WARNING");
        assertThat(impact.getWarningMessage()).contains("2 version(s)");
        assertThat(impact.getWarningMessage()).contains("15 process instance(s)");
    }
    
    /**
     * Test calculateRollbackImpact when target version is already active
     */
    @Test
    void calculateRollbackImpact_shouldReturnCannotProceed_whenTargetVersionIsActive() {
        // Given: Target version is already active
        String functionUnitName = "test-function";
        FunctionUnit activeVersion = createFunctionUnit(1L, functionUnitName, "1.0.0", true);
        
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(activeVersion));
        
        // When: Calculating rollback impact
        // Then: Should throw VersionValidationError because version is already active
        assertThatThrownBy(() -> rollbackService.calculateRollbackImpact(1L))
                .isInstanceOf(VersionValidationError.class)
                .hasMessageContaining("already active");
    }
    
    /**
     * Test rollback with process instances
     */
    @Test
    void rollbackToVersion_shouldDeleteProcessInstances_whenTheyExist() {
        // Given: Two versions with process instances
        String functionUnitName = "test-function";
        
        FunctionUnit version1 = createFunctionUnit(1L, functionUnitName, "1.0.0", false);
        FunctionUnit version2 = createFunctionUnit(2L, functionUnitName, "2.0.0", true);
        
        List<FunctionUnit> allVersions = List.of(version2, version1);
        
        // Mock repository calls - version 2 has 5 process instances
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(version1));
        when(versionService.getVersionHistory(functionUnitName)).thenReturn(allVersions);
        when(processInstanceRepository.countByFunctionUnitVersionId(2L)).thenReturn(5L);
        doNothing().when(processInstanceRepository).deleteByFunctionUnitVersionId(anyLong());
        doNothing().when(functionUnitRepository).deleteById(anyLong());
        doNothing().when(versionService).activateVersion(1L);
        
        // When: Rolling back to version 1.0.0
        RollbackResult result = rollbackService.rollbackToVersion(1L);
        
        // Then: Rollback should succeed
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDeletedProcessCount()).isEqualTo(5L);
        
        // And: Process instances should be deleted before version deletion
        verify(processInstanceRepository).deleteByFunctionUnitVersionId(2L);
        verify(functionUnitRepository).deleteById(2L);
    }
    
    /**
     * Test rollback transaction failure
     */
    @Test
    void rollbackToVersion_shouldThrowTransactionError_whenDeletionFails() {
        // Given: Two versions exist
        String functionUnitName = "test-function";
        
        FunctionUnit version1 = createFunctionUnit(1L, functionUnitName, "1.0.0", false);
        FunctionUnit version2 = createFunctionUnit(2L, functionUnitName, "2.0.0", true);
        
        List<FunctionUnit> allVersions = List.of(version2, version1);
        
        // Mock repository calls - simulate failure during process deletion
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(version1));
        when(versionService.getVersionHistory(functionUnitName)).thenReturn(allVersions);
        when(processInstanceRepository.countByFunctionUnitVersionId(2L)).thenReturn(5L);
        doThrow(new RuntimeException("Database error"))
                .when(processInstanceRepository).deleteByFunctionUnitVersionId(2L);
        
        // When/Then: Should throw TransactionError
        assertThatThrownBy(() -> rollbackService.rollbackToVersion(1L))
                .isInstanceOf(TransactionError.class)
                .hasMessageContaining("Rollback operation");
        
        // And: Version should not be deleted or activated
        verify(functionUnitRepository, never()).deleteById(anyLong());
        verify(versionService, never()).activateVersion(anyLong());
    }
    
    /**
     * Test rollback with multiple versions to delete
     */
    @Test
    void rollbackToVersion_shouldDeleteAllNewerVersions() {
        // Given: Five versions exist
        String functionUnitName = "test-function";
        
        List<FunctionUnit> allVersions = new ArrayList<>();
        for (int i = 5; i >= 1; i--) {
            FunctionUnit version = createFunctionUnit(
                    (long) i, 
                    functionUnitName, 
                    i + ".0.0", 
                    i == 5 // Only version 5 is active
            );
            allVersions.add(version);
        }
        
        FunctionUnit targetVersion = allVersions.get(3); // Version 2.0.0
        
        // Mock repository calls
        when(functionUnitRepository.findById(2L)).thenReturn(Optional.of(targetVersion));
        when(versionService.getVersionHistory(functionUnitName)).thenReturn(allVersions);
        when(processInstanceRepository.countByFunctionUnitVersionId(anyLong())).thenReturn(0L);
        doNothing().when(processInstanceRepository).deleteByFunctionUnitVersionId(anyLong());
        doNothing().when(functionUnitRepository).deleteById(anyLong());
        doNothing().when(versionService).activateVersion(2L);
        
        // When: Rolling back to version 2.0.0
        RollbackResult result = rollbackService.rollbackToVersion(2L);
        
        // Then: Rollback should succeed
        assertThat(result.isSuccess()).isTrue();
        
        // And: Versions 3, 4, and 5 should be deleted
        assertThat(result.getDeletedVersions()).containsExactlyInAnyOrder("3.0.0", "4.0.0", "5.0.0");
        verify(functionUnitRepository).deleteById(3L);
        verify(functionUnitRepository).deleteById(4L);
        verify(functionUnitRepository).deleteById(5L);
        
        // And: Versions 1 and 2 should not be deleted
        verify(functionUnitRepository, never()).deleteById(1L);
        verify(functionUnitRepository, never()).deleteById(2L);
    }
    
    /**
     * Helper method to create a FunctionUnit for testing
     */
    private FunctionUnit createFunctionUnit(Long id, String name, String version, boolean isActive) {
        return FunctionUnit.builder()
                .id(id)
                .name(name)
                .version(version)
                .isActive(isActive)
                .deployedAt(Instant.now())
                .build();
    }
}
