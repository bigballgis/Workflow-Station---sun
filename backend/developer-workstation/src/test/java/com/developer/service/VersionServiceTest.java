package com.developer.service;

import com.developer.entity.FunctionUnit;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for VersionService.
 * Tests version queries, history retrieval, and version existence checks.
 * 
 * Requirements: 2.5, 3.3, 1.4
 */
@DisplayName("VersionService Unit Tests")
public class VersionServiceTest {
    
    @Mock
    private FunctionUnitRepository functionUnitRepository;
    
    private VersionService versionService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        versionService = new VersionService(functionUnitRepository);
    }
    
    @Test
    @DisplayName("Should return active version when it exists")
    void shouldReturnActiveVersionWhenItExists() {
        // Setup
        String functionUnitName = "TestFunctionUnit";
        FunctionUnit activeVersion = FunctionUnit.builder()
                .id(1L)
                .name(functionUnitName)
                .version("1.2.3")
                .isActive(true)
                .deployedAt(Instant.now())
                .build();
        
        when(functionUnitRepository.findByNameAndIsActive(functionUnitName, true))
                .thenReturn(Optional.of(activeVersion));
        
        // Execute
        FunctionUnit result = versionService.getActiveVersion(functionUnitName);
        
        // Verify
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(functionUnitName);
        assertThat(result.getVersion()).isEqualTo("1.2.3");
        assertThat(result.getIsActive()).isTrue();
        verify(functionUnitRepository).findByNameAndIsActive(functionUnitName, true);
    }
    
    @Test
    @DisplayName("Should throw ResourceNotFoundException when no active version exists")
    void shouldThrowResourceNotFoundExceptionWhenNoActiveVersionExists() {
        // Setup
        String functionUnitName = "NonExistentFunctionUnit";
        when(functionUnitRepository.findByNameAndIsActive(functionUnitName, true))
                .thenReturn(Optional.empty());
        
        // Execute & Verify
        assertThatThrownBy(() -> versionService.getActiveVersion(functionUnitName))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("FunctionUnit")
                .hasMessageContaining(functionUnitName);
        
        verify(functionUnitRepository).findByNameAndIsActive(functionUnitName, true);
    }
    
    @Test
    @DisplayName("Should return version history ordered by version descending")
    void shouldReturnVersionHistoryOrderedByVersionDescending() {
        // Setup
        String functionUnitName = "TestFunctionUnit";
        FunctionUnit version1 = FunctionUnit.builder()
                .id(1L)
                .name(functionUnitName)
                .version("1.0.0")
                .isActive(false)
                .deployedAt(Instant.now().minusSeconds(3600))
                .build();
        
        FunctionUnit version2 = FunctionUnit.builder()
                .id(2L)
                .name(functionUnitName)
                .version("1.1.0")
                .isActive(false)
                .deployedAt(Instant.now().minusSeconds(1800))
                .build();
        
        FunctionUnit version3 = FunctionUnit.builder()
                .id(3L)
                .name(functionUnitName)
                .version("1.2.0")
                .isActive(true)
                .deployedAt(Instant.now())
                .build();
        
        List<FunctionUnit> versions = Arrays.asList(version3, version2, version1);
        
        when(functionUnitRepository.findByNameOrderByVersionDesc(functionUnitName))
                .thenReturn(versions);
        
        // Execute
        List<FunctionUnit> result = versionService.getVersionHistory(functionUnitName);
        
        // Verify
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getVersion()).isEqualTo("1.2.0");
        assertThat(result.get(1).getVersion()).isEqualTo("1.1.0");
        assertThat(result.get(2).getVersion()).isEqualTo("1.0.0");
        verify(functionUnitRepository).findByNameOrderByVersionDesc(functionUnitName);
    }
    
    @Test
    @DisplayName("Should return empty list when no versions exist")
    void shouldReturnEmptyListWhenNoVersionsExist() {
        // Setup
        String functionUnitName = "NonExistentFunctionUnit";
        when(functionUnitRepository.findByNameOrderByVersionDesc(functionUnitName))
                .thenReturn(List.of());
        
        // Execute
        List<FunctionUnit> result = versionService.getVersionHistory(functionUnitName);
        
        // Verify
        assertThat(result).isEmpty();
        verify(functionUnitRepository).findByNameOrderByVersionDesc(functionUnitName);
    }
    
    @Test
    @DisplayName("Should return true when version exists")
    void shouldReturnTrueWhenVersionExists() {
        // Setup
        String functionUnitName = "TestFunctionUnit";
        String version = "1.2.3";
        when(functionUnitRepository.existsByNameAndVersion(functionUnitName, version))
                .thenReturn(true);
        
        // Execute
        boolean result = versionService.versionExists(functionUnitName, version);
        
        // Verify
        assertThat(result).isTrue();
        verify(functionUnitRepository).existsByNameAndVersion(functionUnitName, version);
    }
    
    @Test
    @DisplayName("Should return false when version does not exist")
    void shouldReturnFalseWhenVersionDoesNotExist() {
        // Setup
        String functionUnitName = "TestFunctionUnit";
        String version = "2.0.0";
        when(functionUnitRepository.existsByNameAndVersion(functionUnitName, version))
                .thenReturn(false);
        
        // Execute
        boolean result = versionService.versionExists(functionUnitName, version);
        
        // Verify
        assertThat(result).isFalse();
        verify(functionUnitRepository).existsByNameAndVersion(functionUnitName, version);
    }
    
    @Test
    @DisplayName("Should handle repository exception in getActiveVersion")
    void shouldHandleRepositoryExceptionInGetActiveVersion() {
        // Setup
        String functionUnitName = "TestFunctionUnit";
        when(functionUnitRepository.findByNameAndIsActive(functionUnitName, true))
                .thenThrow(new RuntimeException("Database error"));
        
        // Execute & Verify
        assertThatThrownBy(() -> versionService.getActiveVersion(functionUnitName))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to retrieve active version");
        
        verify(functionUnitRepository).findByNameAndIsActive(functionUnitName, true);
    }
    
    @Test
    @DisplayName("Should handle repository exception in getVersionHistory")
    void shouldHandleRepositoryExceptionInGetVersionHistory() {
        // Setup
        String functionUnitName = "TestFunctionUnit";
        when(functionUnitRepository.findByNameOrderByVersionDesc(functionUnitName))
                .thenThrow(new RuntimeException("Database error"));
        
        // Execute & Verify
        assertThatThrownBy(() -> versionService.getVersionHistory(functionUnitName))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to retrieve version history");
        
        verify(functionUnitRepository).findByNameOrderByVersionDesc(functionUnitName);
    }
    
    @Test
    @DisplayName("Should handle repository exception in versionExists")
    void shouldHandleRepositoryExceptionInVersionExists() {
        // Setup
        String functionUnitName = "TestFunctionUnit";
        String version = "1.2.3";
        when(functionUnitRepository.existsByNameAndVersion(functionUnitName, version))
                .thenThrow(new RuntimeException("Database error"));
        
        // Execute & Verify
        assertThatThrownBy(() -> versionService.versionExists(functionUnitName, version))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to check version existence");
        
        verify(functionUnitRepository).existsByNameAndVersion(functionUnitName, version);
    }
    
    @Test
    @DisplayName("Should return version history with single version")
    void shouldReturnVersionHistoryWithSingleVersion() {
        // Setup
        String functionUnitName = "TestFunctionUnit";
        FunctionUnit version = FunctionUnit.builder()
                .id(1L)
                .name(functionUnitName)
                .version("1.0.0")
                .isActive(true)
                .deployedAt(Instant.now())
                .build();
        
        when(functionUnitRepository.findByNameOrderByVersionDesc(functionUnitName))
                .thenReturn(List.of(version));
        
        // Execute
        List<FunctionUnit> result = versionService.getVersionHistory(functionUnitName);
        
        // Verify
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getVersion()).isEqualTo("1.0.0");
        assertThat(result.get(0).getIsActive()).isTrue();
        verify(functionUnitRepository).findByNameOrderByVersionDesc(functionUnitName);
    }
    
    @Test
    @DisplayName("Should check version existence for first version")
    void shouldCheckVersionExistenceForFirstVersion() {
        // Setup
        String functionUnitName = "TestFunctionUnit";
        String version = "1.0.0";
        when(functionUnitRepository.existsByNameAndVersion(functionUnitName, version))
                .thenReturn(false);
        
        // Execute
        boolean result = versionService.versionExists(functionUnitName, version);
        
        // Verify
        assertThat(result).isFalse();
        verify(functionUnitRepository).existsByNameAndVersion(functionUnitName, version);
    }
    
    // Tests for generateNextVersion() method
    
    @Test
    @DisplayName("Should return 1.0.0 for first deployment")
    void shouldReturnInitialVersionForFirstDeployment() {
        // Setup
        String functionUnitName = "NewFunctionUnit";
        when(functionUnitRepository.findByNameOrderByVersionDesc(functionUnitName))
                .thenReturn(List.of());
        
        // Execute
        String result = versionService.generateNextVersion(functionUnitName, "patch");
        
        // Verify
        assertThat(result).isEqualTo("1.0.0");
        verify(functionUnitRepository).findByNameOrderByVersionDesc(functionUnitName);
    }
    
    @Test
    @DisplayName("Should increment patch version correctly")
    void shouldIncrementPatchVersionCorrectly() {
        // Setup
        String functionUnitName = "TestFunctionUnit";
        FunctionUnit currentVersion = FunctionUnit.builder()
                .id(1L)
                .name(functionUnitName)
                .version("1.2.3")
                .isActive(true)
                .deployedAt(Instant.now())
                .build();
        
        when(functionUnitRepository.findByNameOrderByVersionDesc(functionUnitName))
                .thenReturn(List.of(currentVersion));
        
        // Execute
        String result = versionService.generateNextVersion(functionUnitName, "patch");
        
        // Verify
        assertThat(result).isEqualTo("1.2.4");
        verify(functionUnitRepository).findByNameOrderByVersionDesc(functionUnitName);
    }
    
    @Test
    @DisplayName("Should increment minor version and reset patch")
    void shouldIncrementMinorVersionAndResetPatch() {
        // Setup
        String functionUnitName = "TestFunctionUnit";
        FunctionUnit currentVersion = FunctionUnit.builder()
                .id(1L)
                .name(functionUnitName)
                .version("1.2.3")
                .isActive(true)
                .deployedAt(Instant.now())
                .build();
        
        when(functionUnitRepository.findByNameOrderByVersionDesc(functionUnitName))
                .thenReturn(List.of(currentVersion));
        
        // Execute
        String result = versionService.generateNextVersion(functionUnitName, "minor");
        
        // Verify
        assertThat(result).isEqualTo("1.3.0");
        verify(functionUnitRepository).findByNameOrderByVersionDesc(functionUnitName);
    }
    
    @Test
    @DisplayName("Should increment major version and reset minor and patch")
    void shouldIncrementMajorVersionAndResetMinorAndPatch() {
        // Setup
        String functionUnitName = "TestFunctionUnit";
        FunctionUnit currentVersion = FunctionUnit.builder()
                .id(1L)
                .name(functionUnitName)
                .version("1.2.3")
                .isActive(true)
                .deployedAt(Instant.now())
                .build();
        
        when(functionUnitRepository.findByNameOrderByVersionDesc(functionUnitName))
                .thenReturn(List.of(currentVersion));
        
        // Execute
        String result = versionService.generateNextVersion(functionUnitName, "major");
        
        // Verify
        assertThat(result).isEqualTo("2.0.0");
        verify(functionUnitRepository).findByNameOrderByVersionDesc(functionUnitName);
    }
    
    @Test
    @DisplayName("Should throw IllegalArgumentException for invalid change type")
    void shouldThrowIllegalArgumentExceptionForInvalidChangeType() {
        // Setup
        String functionUnitName = "TestFunctionUnit";
        
        // Execute & Verify
        assertThatThrownBy(() -> versionService.generateNextVersion(functionUnitName, "invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid change type")
                .hasMessageContaining("invalid");
        
        verify(functionUnitRepository, never()).findByNameOrderByVersionDesc(any());
    }
    
    @Test
    @DisplayName("Should throw IllegalArgumentException for null change type")
    void shouldThrowIllegalArgumentExceptionForNullChangeType() {
        // Setup
        String functionUnitName = "TestFunctionUnit";
        
        // Execute & Verify
        assertThatThrownBy(() -> versionService.generateNextVersion(functionUnitName, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Change type cannot be null or empty");
        
        verify(functionUnitRepository, never()).findByNameOrderByVersionDesc(any());
    }
    
    @Test
    @DisplayName("Should use highest version when multiple versions exist")
    void shouldUseHighestVersionWhenMultipleVersionsExist() {
        // Setup
        String functionUnitName = "TestFunctionUnit";
        FunctionUnit version1 = FunctionUnit.builder()
                .id(1L)
                .name(functionUnitName)
                .version("1.0.0")
                .isActive(false)
                .deployedAt(Instant.now().minusSeconds(3600))
                .build();
        
        FunctionUnit version2 = FunctionUnit.builder()
                .id(2L)
                .name(functionUnitName)
                .version("1.5.2")
                .isActive(true)
                .deployedAt(Instant.now())
                .build();
        
        FunctionUnit version3 = FunctionUnit.builder()
                .id(3L)
                .name(functionUnitName)
                .version("1.2.0")
                .isActive(false)
                .deployedAt(Instant.now().minusSeconds(1800))
                .build();
        
        // Repository returns versions ordered by version descending
        when(functionUnitRepository.findByNameOrderByVersionDesc(functionUnitName))
                .thenReturn(Arrays.asList(version2, version3, version1));
        
        // Execute
        String result = versionService.generateNextVersion(functionUnitName, "patch");
        
        // Verify - should increment from 1.5.2 (highest version)
        assertThat(result).isEqualTo("1.5.3");
        verify(functionUnitRepository).findByNameOrderByVersionDesc(functionUnitName);
    }
    
    @Test
    @DisplayName("Should handle version with zeros correctly")
    void shouldHandleVersionWithZerosCorrectly() {
        // Setup
        String functionUnitName = "TestFunctionUnit";
        FunctionUnit currentVersion = FunctionUnit.builder()
                .id(1L)
                .name(functionUnitName)
                .version("2.0.0")
                .isActive(true)
                .deployedAt(Instant.now())
                .build();
        
        when(functionUnitRepository.findByNameOrderByVersionDesc(functionUnitName))
                .thenReturn(List.of(currentVersion));
        
        // Execute
        String result = versionService.generateNextVersion(functionUnitName, "patch");
        
        // Verify
        assertThat(result).isEqualTo("2.0.1");
        verify(functionUnitRepository).findByNameOrderByVersionDesc(functionUnitName);
    }
    
    @Test
    @DisplayName("Should handle repository exception in generateNextVersion")
    void shouldHandleRepositoryExceptionInGenerateNextVersion() {
        // Setup
        String functionUnitName = "TestFunctionUnit";
        when(functionUnitRepository.findByNameOrderByVersionDesc(functionUnitName))
                .thenThrow(new RuntimeException("Database error"));
        
        // Execute & Verify
        assertThatThrownBy(() -> versionService.generateNextVersion(functionUnitName, "patch"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to generate next version");
        
        verify(functionUnitRepository).findByNameOrderByVersionDesc(functionUnitName);
    }
    
    // Tests for activateVersion() method
    
    @Test
    @DisplayName("Should activate target version and deactivate all others")
    void shouldActivateTargetVersionAndDeactivateAllOthers() {
        // Setup
        String functionUnitName = "TestFunctionUnit";
        Long targetVersionId = 2L;
        
        FunctionUnit version1 = FunctionUnit.builder()
                .id(1L)
                .name(functionUnitName)
                .version("1.0.0")
                .isActive(true)
                .deployedAt(Instant.now().minusSeconds(3600))
                .build();
        
        FunctionUnit version2 = FunctionUnit.builder()
                .id(2L)
                .name(functionUnitName)
                .version("1.1.0")
                .isActive(false)
                .deployedAt(Instant.now().minusSeconds(1800))
                .build();
        
        FunctionUnit version3 = FunctionUnit.builder()
                .id(3L)
                .name(functionUnitName)
                .version("1.2.0")
                .isActive(false)
                .deployedAt(Instant.now())
                .build();
        
        when(functionUnitRepository.findById(targetVersionId))
                .thenReturn(Optional.of(version2));
        when(functionUnitRepository.findByNameOrderByVersionDesc(functionUnitName))
                .thenReturn(Arrays.asList(version3, version2, version1));
        when(functionUnitRepository.save(any(FunctionUnit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Execute
        versionService.activateVersion(targetVersionId);
        
        // Verify
        verify(functionUnitRepository).findById(targetVersionId);
        verify(functionUnitRepository).findByNameOrderByVersionDesc(functionUnitName);
        // Should save version1 (to deactivate), version2 (to activate)
        verify(functionUnitRepository, times(2)).save(any(FunctionUnit.class));
        
        // Verify version2 is activated
        assertThat(version2.getIsActive()).isTrue();
        // Verify version1 is deactivated
        assertThat(version1.getIsActive()).isFalse();
    }
    
    @Test
    @DisplayName("Should throw ResourceNotFoundException when version does not exist")
    void shouldThrowResourceNotFoundExceptionWhenVersionDoesNotExist() {
        // Setup
        Long nonExistentVersionId = 999L;
        when(functionUnitRepository.findById(nonExistentVersionId))
                .thenReturn(Optional.empty());
        
        // Execute & Verify
        assertThatThrownBy(() -> versionService.activateVersion(nonExistentVersionId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("FunctionUnit")
                .hasMessageContaining(nonExistentVersionId.toString());
        
        verify(functionUnitRepository).findById(nonExistentVersionId);
        verify(functionUnitRepository, never()).findByNameOrderByVersionDesc(any());
        verify(functionUnitRepository, never()).save(any());
    }
    
    @Test
    @DisplayName("Should activate version when it is the only version")
    void shouldActivateVersionWhenItIsTheOnlyVersion() {
        // Setup
        String functionUnitName = "TestFunctionUnit";
        Long versionId = 1L;
        
        FunctionUnit version = FunctionUnit.builder()
                .id(versionId)
                .name(functionUnitName)
                .version("1.0.0")
                .isActive(false)
                .deployedAt(Instant.now())
                .build();
        
        when(functionUnitRepository.findById(versionId))
                .thenReturn(Optional.of(version));
        when(functionUnitRepository.findByNameOrderByVersionDesc(functionUnitName))
                .thenReturn(List.of(version));
        when(functionUnitRepository.save(any(FunctionUnit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Execute
        versionService.activateVersion(versionId);
        
        // Verify
        verify(functionUnitRepository).findById(versionId);
        verify(functionUnitRepository).findByNameOrderByVersionDesc(functionUnitName);
        verify(functionUnitRepository, times(1)).save(version);
        assertThat(version.getIsActive()).isTrue();
    }
    
    @Test
    @DisplayName("Should activate version when target is already active")
    void shouldActivateVersionWhenTargetIsAlreadyActive() {
        // Setup
        String functionUnitName = "TestFunctionUnit";
        Long versionId = 1L;
        
        FunctionUnit version = FunctionUnit.builder()
                .id(versionId)
                .name(functionUnitName)
                .version("1.0.0")
                .isActive(true)
                .deployedAt(Instant.now())
                .build();
        
        when(functionUnitRepository.findById(versionId))
                .thenReturn(Optional.of(version));
        when(functionUnitRepository.findByNameOrderByVersionDesc(functionUnitName))
                .thenReturn(List.of(version));
        when(functionUnitRepository.save(any(FunctionUnit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Execute
        versionService.activateVersion(versionId);
        
        // Verify
        verify(functionUnitRepository).findById(versionId);
        verify(functionUnitRepository).findByNameOrderByVersionDesc(functionUnitName);
        // Should deactivate and then reactivate the same version
        verify(functionUnitRepository, times(2)).save(version);
        assertThat(version.getIsActive()).isTrue();
    }
    
    @Test
    @DisplayName("Should deactivate multiple active versions before activating target")
    void shouldDeactivateMultipleActiveVersionsBeforeActivatingTarget() {
        // Setup - This tests recovery from an invalid state where multiple versions are active
        String functionUnitName = "TestFunctionUnit";
        Long targetVersionId = 3L;
        
        FunctionUnit version1 = FunctionUnit.builder()
                .id(1L)
                .name(functionUnitName)
                .version("1.0.0")
                .isActive(true)
                .deployedAt(Instant.now().minusSeconds(3600))
                .build();
        
        FunctionUnit version2 = FunctionUnit.builder()
                .id(2L)
                .name(functionUnitName)
                .version("1.1.0")
                .isActive(true)
                .deployedAt(Instant.now().minusSeconds(1800))
                .build();
        
        FunctionUnit version3 = FunctionUnit.builder()
                .id(3L)
                .name(functionUnitName)
                .version("1.2.0")
                .isActive(false)
                .deployedAt(Instant.now())
                .build();
        
        when(functionUnitRepository.findById(targetVersionId))
                .thenReturn(Optional.of(version3));
        when(functionUnitRepository.findByNameOrderByVersionDesc(functionUnitName))
                .thenReturn(Arrays.asList(version3, version2, version1));
        when(functionUnitRepository.save(any(FunctionUnit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Execute
        versionService.activateVersion(targetVersionId);
        
        // Verify
        verify(functionUnitRepository).findById(targetVersionId);
        verify(functionUnitRepository).findByNameOrderByVersionDesc(functionUnitName);
        // Should save version1 (deactivate), version2 (deactivate), version3 (activate)
        verify(functionUnitRepository, times(3)).save(any(FunctionUnit.class));
        
        assertThat(version1.getIsActive()).isFalse();
        assertThat(version2.getIsActive()).isFalse();
        assertThat(version3.getIsActive()).isTrue();
    }
    
    @Test
    @DisplayName("Should handle repository exception in activateVersion")
    void shouldHandleRepositoryExceptionInActivateVersion() {
        // Setup
        Long versionId = 1L;
        when(functionUnitRepository.findById(versionId))
                .thenThrow(new RuntimeException("Database error"));
        
        // Execute & Verify
        assertThatThrownBy(() -> versionService.activateVersion(versionId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to activate version");
        
        verify(functionUnitRepository).findById(versionId);
        verify(functionUnitRepository, never()).save(any());
    }
    
    @Test
    @DisplayName("Should handle save exception in activateVersion")
    void shouldHandleSaveExceptionInActivateVersion() {
        // Setup
        String functionUnitName = "TestFunctionUnit";
        Long versionId = 1L;
        
        FunctionUnit version = FunctionUnit.builder()
                .id(versionId)
                .name(functionUnitName)
                .version("1.0.0")
                .isActive(false)
                .deployedAt(Instant.now())
                .build();
        
        when(functionUnitRepository.findById(versionId))
                .thenReturn(Optional.of(version));
        when(functionUnitRepository.findByNameOrderByVersionDesc(functionUnitName))
                .thenReturn(List.of(version));
        when(functionUnitRepository.save(any(FunctionUnit.class)))
                .thenThrow(new RuntimeException("Database save error"));
        
        // Execute & Verify
        assertThatThrownBy(() -> versionService.activateVersion(versionId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to activate version");
        
        verify(functionUnitRepository).findById(versionId);
        verify(functionUnitRepository).findByNameOrderByVersionDesc(functionUnitName);
        verify(functionUnitRepository).save(any(FunctionUnit.class));
    }
    
    // Tests for getFunctionUnit() method (backward compatibility)
    
    @Test
    @DisplayName("Should return active version when querying by name only (backward compatible)")
    void shouldReturnActiveVersionWhenQueryingByNameOnly() {
        // Setup
        String functionUnitName = "TestFunctionUnit";
        FunctionUnit activeVersion = FunctionUnit.builder()
                .id(2L)
                .name(functionUnitName)
                .version("1.5.0")
                .isActive(true)
                .deployedAt(Instant.now())
                .build();
        
        when(functionUnitRepository.findByNameAndIsActive(functionUnitName, true))
                .thenReturn(Optional.of(activeVersion));
        
        // Execute - Use backward compatible method (no version specified)
        FunctionUnit result = versionService.getFunctionUnit(functionUnitName);
        
        // Verify - Should return active version
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(functionUnitName);
        assertThat(result.getVersion()).isEqualTo("1.5.0");
        assertThat(result.getIsActive()).isTrue();
        assertThat(result.getId()).isEqualTo(2L);
        verify(functionUnitRepository).findByNameAndIsActive(functionUnitName, true);
    }
    
    @Test
    @DisplayName("Should throw ResourceNotFoundException when no active version exists (backward compatible)")
    void shouldThrowResourceNotFoundExceptionWhenNoActiveVersionExistsBackwardCompatible() {
        // Setup
        String functionUnitName = "NonExistentFunctionUnit";
        when(functionUnitRepository.findByNameAndIsActive(functionUnitName, true))
                .thenReturn(Optional.empty());
        
        // Execute & Verify
        assertThatThrownBy(() -> versionService.getFunctionUnit(functionUnitName))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("FunctionUnit")
                .hasMessageContaining(functionUnitName);
        
        verify(functionUnitRepository).findByNameAndIsActive(functionUnitName, true);
    }
    
    @Test
    @DisplayName("Should return active version even when multiple versions exist (backward compatible)")
    void shouldReturnActiveVersionEvenWhenMultipleVersionsExist() {
        // Setup - Multiple versions exist, but only one is active
        String functionUnitName = "TestFunctionUnit";
        FunctionUnit activeVersion = FunctionUnit.builder()
                .id(3L)
                .name(functionUnitName)
                .version("2.0.0")
                .isActive(true)
                .deployedAt(Instant.now())
                .build();
        
        when(functionUnitRepository.findByNameAndIsActive(functionUnitName, true))
                .thenReturn(Optional.of(activeVersion));
        
        // Execute - Use backward compatible method
        FunctionUnit result = versionService.getFunctionUnit(functionUnitName);
        
        // Verify - Should return the active version (2.0.0), not any other version
        assertThat(result).isNotNull();
        assertThat(result.getVersion()).isEqualTo("2.0.0");
        assertThat(result.getIsActive()).isTrue();
        verify(functionUnitRepository).findByNameAndIsActive(functionUnitName, true);
    }
    
    // ========== Property-Based Tests ==========
    
    /**
     * Feature: function-unit-versioned-deployment, Property 7: Active Version Query
     * 
     * For any function unit, querying for the active version should return exactly one version 
     * record with is_active = true.
     * 
     * Validates: Requirements 2.5
     */
    @net.jqwik.api.Property(tries = 100)
    @DisplayName("Property 7: Active version query returns exactly one active version")
    void propertyActiveVersionQueryReturnsExactlyOneActiveVersion(
            @net.jqwik.api.ForAll("functionUnitName") String functionUnitName) {
        
        // Setup - Create a function unit with active version
        FunctionUnit activeVersion = FunctionUnit.builder()
                .id(1L)
                .name(functionUnitName)
                .version("1.0.0")
                .isActive(true)
                .deployedAt(Instant.now())
                .build();
        
        when(functionUnitRepository.findByNameAndIsActive(functionUnitName, true))
                .thenReturn(Optional.of(activeVersion));
        
        // Execute
        FunctionUnit result = versionService.getActiveVersion(functionUnitName);
        
        // Verify - Should return exactly one version with is_active = true
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(functionUnitName);
        assertThat(result.getIsActive()).isTrue();
        
        // Reset mock for next iteration
        reset(functionUnitRepository);
    }
    
    /**
     * Feature: function-unit-versioned-deployment, Property 10: Version History Ordering
     * 
     * For any function unit with multiple versions, the version history query should return 
     * all versions ordered by semantic version number (descending).
     * 
     * Validates: Requirements 3.3
     */
    @net.jqwik.api.Property(tries = 100)
    @DisplayName("Property 10: Version history is ordered by version descending")
    void propertyVersionHistoryIsOrderedByVersionDescending(
            @net.jqwik.api.ForAll("functionUnitName") String functionUnitName,
            @net.jqwik.api.ForAll("versionList") List<String> versions) {
        
        // Skip if less than 2 versions (ordering doesn't matter)
        net.jqwik.api.Assume.that(versions.size() >= 2);
        
        // Setup - Create function units for each version
        List<FunctionUnit> functionUnits = new java.util.ArrayList<>();
        for (int i = 0; i < versions.size(); i++) {
            FunctionUnit fu = FunctionUnit.builder()
                    .id((long) (i + 1))
                    .name(functionUnitName)
                    .version(versions.get(i))
                    .isActive(i == 0) // First one is active
                    .deployedAt(Instant.now().minusSeconds(i * 100))
                    .build();
            functionUnits.add(fu);
        }
        
        when(functionUnitRepository.findByNameOrderByVersionDesc(functionUnitName))
                .thenReturn(functionUnits);
        
        // Execute
        List<FunctionUnit> result = versionService.getVersionHistory(functionUnitName);
        
        // Verify - Should return all versions in descending order
        assertThat(result).hasSize(versions.size());
        
        // Verify ordering - each version should be >= the next one
        for (int i = 0; i < result.size() - 1; i++) {
            String currentVersion = result.get(i).getVersion();
            String nextVersion = result.get(i + 1).getVersion();
            
            com.platform.common.version.SemanticVersion current = 
                    com.platform.common.version.SemanticVersion.parse(currentVersion);
            com.platform.common.version.SemanticVersion next = 
                    com.platform.common.version.SemanticVersion.parse(nextVersion);
            
            // Version should be greater than or equal to the next one (descending order)
            assertThat(current.compareTo(next))
                    .as("Version %s should be >= %s", currentVersion, nextVersion)
                    .isGreaterThanOrEqualTo(0);
        }
        
        // Reset mock for next iteration
        reset(functionUnitRepository);
    }
    
    /**
     * Feature: function-unit-versioned-deployment, Property 1: Automatic Version Generation
     * 
     * For any function unit deployment with a specified change type (major, minor, patch), 
     * the system should automatically generate a new semantic version number that correctly 
     * increments from the previous highest version.
     * 
     * Validates: Requirements 1.1, 1.2
     */
    @net.jqwik.api.Property(tries = 100)
    @DisplayName("Property 1: Automatic version generation increments correctly")
    void propertyAutomaticVersionGenerationIncrementsCorrectly(
            @net.jqwik.api.ForAll("functionUnitName") String functionUnitName,
            @net.jqwik.api.ForAll("semanticVersion") String currentVersion,
            @net.jqwik.api.ForAll("changeType") String changeType) {
        
        // Setup - Create a function unit with current version
        FunctionUnit existingVersion = FunctionUnit.builder()
                .id(1L)
                .name(functionUnitName)
                .version(currentVersion)
                .isActive(true)
                .deployedAt(Instant.now())
                .build();
        
        when(functionUnitRepository.findByNameOrderByVersionDesc(functionUnitName))
                .thenReturn(List.of(existingVersion));
        
        // Execute
        String nextVersion = versionService.generateNextVersion(functionUnitName, changeType);
        
        // Verify - Next version should be correctly incremented
        com.platform.common.version.SemanticVersion current = 
                com.platform.common.version.SemanticVersion.parse(currentVersion);
        com.platform.common.version.SemanticVersion next = 
                com.platform.common.version.SemanticVersion.parse(nextVersion);
        
        // Verify the increment is correct based on change type
        com.platform.common.version.SemanticVersion expected;
        switch (changeType) {
            case "major":
                expected = current.incrementMajor();
                break;
            case "minor":
                expected = current.incrementMinor();
                break;
            case "patch":
                expected = current.incrementPatch();
                break;
            default:
                throw new IllegalStateException("Invalid change type: " + changeType);
        }
        
        assertThat(next).isEqualTo(expected);
        assertThat(next.greaterThan(current)).isTrue();
        
        // Reset mock for next iteration
        reset(functionUnitRepository);
    }
    
    /**
     * Feature: function-unit-versioned-deployment, Property 5: Single Active Version Invariant
     * 
     * For any function unit at any point in time, exactly one version should be marked as active 
     * (never zero, never multiple).
     * 
     * Validates: Requirements 2.3, 11.6
     */
    @net.jqwik.api.Property(tries = 100)
    @DisplayName("Property 5: Exactly one version is active after activation")
    void propertyExactlyOneVersionIsActiveAfterActivation(
            @net.jqwik.api.ForAll("functionUnitName") String functionUnitName,
            @net.jqwik.api.ForAll("versionList") List<String> versions) {
        
        // Need at least 2 versions to test activation
        net.jqwik.api.Assume.that(versions.size() >= 2);
        
        // Setup - Create multiple versions, all initially inactive
        List<FunctionUnit> functionUnits = new java.util.ArrayList<>();
        for (int i = 0; i < versions.size(); i++) {
            FunctionUnit fu = FunctionUnit.builder()
                    .id((long) (i + 1))
                    .name(functionUnitName)
                    .version(versions.get(i))
                    .isActive(false)
                    .deployedAt(Instant.now().minusSeconds(i * 100))
                    .build();
            functionUnits.add(fu);
        }
        
        // Pick a random version to activate
        int targetIndex = new java.util.Random().nextInt(versions.size());
        Long targetVersionId = (long) (targetIndex + 1);
        FunctionUnit targetVersion = functionUnits.get(targetIndex);
        
        when(functionUnitRepository.findById(targetVersionId))
                .thenReturn(Optional.of(targetVersion));
        when(functionUnitRepository.findByNameOrderByVersionDesc(functionUnitName))
                .thenReturn(functionUnits);
        when(functionUnitRepository.save(any(FunctionUnit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Execute
        versionService.activateVersion(targetVersionId);
        
        // Verify - Exactly one version should be active
        long activeCount = functionUnits.stream()
                .filter(FunctionUnit::getIsActive)
                .count();
        
        assertThat(activeCount)
                .as("Exactly one version should be active")
                .isEqualTo(1);
        
        // Verify the correct version is active
        assertThat(targetVersion.getIsActive()).isTrue();
        
        // Verify all other versions are inactive
        for (int i = 0; i < functionUnits.size(); i++) {
            if (i != targetIndex) {
                assertThat(functionUnits.get(i).getIsActive()).isFalse();
            }
        }
        
        // Reset mock for next iteration
        reset(functionUnitRepository);
    }
    
    /**
     * Feature: function-unit-versioned-deployment, Property 28: Backward Compatible Queries
     * 
     * For any query for a function unit without specifying a version, the system should return 
     * the active version's data.
     * 
     * Validates: Requirements 9.4, 9.5
     */
    @net.jqwik.api.Property(tries = 100)
    @DisplayName("Property 28: Backward compatible queries return active version")
    void propertyBackwardCompatibleQueriesReturnActiveVersion(
            @net.jqwik.api.ForAll("functionUnitName") String functionUnitName,
            @net.jqwik.api.ForAll("versionList") List<String> versions) {
        
        // Need at least 1 version
        net.jqwik.api.Assume.that(!versions.isEmpty());
        
        // Setup - Create multiple versions with one active
        List<FunctionUnit> functionUnits = new java.util.ArrayList<>();
        int activeIndex = new java.util.Random().nextInt(versions.size());
        
        for (int i = 0; i < versions.size(); i++) {
            FunctionUnit fu = FunctionUnit.builder()
                    .id((long) (i + 1))
                    .name(functionUnitName)
                    .version(versions.get(i))
                    .isActive(i == activeIndex) // Only one version is active
                    .deployedAt(Instant.now().minusSeconds(i * 100))
                    .build();
            functionUnits.add(fu);
        }
        
        FunctionUnit activeVersion = functionUnits.get(activeIndex);
        
        // Mock repository to return active version
        when(functionUnitRepository.findByNameAndIsActive(functionUnitName, true))
                .thenReturn(Optional.of(activeVersion));
        
        // Execute - Query without specifying version (backward compatible method)
        FunctionUnit result = versionService.getFunctionUnit(functionUnitName);
        
        // Verify - Should return the active version
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(functionUnitName);
        assertThat(result.getVersion()).isEqualTo(activeVersion.getVersion());
        assertThat(result.getIsActive()).isTrue();
        assertThat(result.getId()).isEqualTo(activeVersion.getId());
        
        // Verify the result matches the active version exactly
        assertThat(result).isEqualTo(activeVersion);
        
        // Reset mock for next iteration
        reset(functionUnitRepository);
    }
    
    // ========== Arbitraries for Property Tests ==========
    
    @net.jqwik.api.Provide
    net.jqwik.api.Arbitrary<String> functionUnitName() {
        return net.jqwik.api.Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(3)
                .ofMaxLength(20);
    }
    
    @net.jqwik.api.Provide
    net.jqwik.api.Arbitrary<String> semanticVersion() {
        return net.jqwik.api.Combinators.combine(
                net.jqwik.api.Arbitraries.integers().between(0, 10),
                net.jqwik.api.Arbitraries.integers().between(0, 20),
                net.jqwik.api.Arbitraries.integers().between(0, 50)
        ).as((major, minor, patch) -> String.format("%d.%d.%d", major, minor, patch));
    }
    
    @net.jqwik.api.Provide
    net.jqwik.api.Arbitrary<String> changeType() {
        return net.jqwik.api.Arbitraries.of("major", "minor", "patch");
    }
    
    @net.jqwik.api.Provide
    net.jqwik.api.Arbitrary<List<String>> versionList() {
        return semanticVersion()
                .list()
                .ofMinSize(1)
                .ofMaxSize(5)
                .map(versions -> {
                    // Sort versions in descending order to simulate repository behavior
                    java.util.List<String> sorted = new java.util.ArrayList<>(versions);
                    sorted.sort((v1, v2) -> {
                        com.platform.common.version.SemanticVersion sv1 = 
                                com.platform.common.version.SemanticVersion.parse(v1);
                        com.platform.common.version.SemanticVersion sv2 = 
                                com.platform.common.version.SemanticVersion.parse(v2);
                        return sv2.compareTo(sv1); // Descending order
                    });
                    return sorted;
                });
    }
}

