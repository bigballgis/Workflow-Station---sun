package com.developer.service;

import com.developer.dto.RollbackImpact;
import com.developer.dto.RollbackResult;
import com.developer.entity.FunctionUnit;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.ProcessInstanceRepository;
import com.platform.common.exception.TransactionError;
import com.platform.common.exception.VersionValidationError;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * Property-based tests for RollbackService.
 * Tests universal properties that should hold for all valid rollback operations.
 */
class RollbackServicePropertyTest {
    
    @Mock
    private FunctionUnitRepository functionUnitRepository;
    
    @Mock
    private ProcessInstanceRepository processInstanceRepository;
    
    @Mock
    private VersionService versionService;
    
    private RollbackService rollbackService;
    
    @BeforeEach
    void setUp() {
        openMocks(this);
        rollbackService = new RollbackService(
                functionUnitRepository,
                processInstanceRepository,
                versionService
        );
    }
    
    /**
     * Property 20: Rollback Version Activation
     * 
     * For any rollback operation to a target version, that version should be marked as active 
     * after the rollback completes.
     * 
     * **Validates: Requirements 6.2**
     */
    @Property(tries = 100)
    void rollbackActivatesTargetVersion(
            @ForAll("functionUnitNames") String functionUnitName,
            @ForAll("versionLists") List<String> versions) {
        
        // Skip if we don't have at least 2 versions
        Assume.that(versions.size() >= 2);
        
        // Initialize mocks for each property test iteration
        openMocks(this);
        rollbackService = new RollbackService(
                functionUnitRepository,
                processInstanceRepository,
                versionService
        );
        
        // Given: Multiple versions exist, with the latest being active
        List<FunctionUnit> functionUnits = createFunctionUnits(functionUnitName, versions);
        FunctionUnit latestVersion = functionUnits.get(0);
        FunctionUnit targetVersion = functionUnits.get(1); // Rollback to second-latest version
        
        latestVersion.setIsActive(true);
        targetVersion.setIsActive(false);
        
        // Mock repository calls
        when(functionUnitRepository.findById(targetVersion.getId()))
                .thenReturn(Optional.of(targetVersion));
        when(versionService.getVersionHistory(functionUnitName))
                .thenReturn(functionUnits);
        when(processInstanceRepository.countByFunctionUnitVersionId(anyLong()))
                .thenReturn(0L);
        doNothing().when(processInstanceRepository).deleteByFunctionUnitVersionId(anyLong());
        doNothing().when(functionUnitRepository).deleteById(anyLong());
        doNothing().when(versionService).activateVersion(targetVersion.getId());
        
        // When: Rolling back to the target version
        RollbackResult result = rollbackService.rollbackToVersion(targetVersion.getId());
        
        // Then: The rollback should succeed
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRolledBackToVersion()).isEqualTo(targetVersion.getVersion());
        assertThat(result.getRolledBackToVersionId()).isEqualTo(targetVersion.getId());
        
        // And: The target version should be activated
        verify(versionService).activateVersion(targetVersion.getId());
    }
    
    /**
     * Property 21: Rollback Version Deletion
     * 
     * For any rollback operation to a target version, all versions with version numbers 
     * greater than the target should be deleted from the database.
     * 
     * **Validates: Requirements 6.3**
     */
    @Property(tries = 100)
    void rollbackDeletesNewerVersions(
            @ForAll("functionUnitNames") String functionUnitName,
            @ForAll("versionLists") List<String> versions) {
        
        // Skip if we don't have at least 2 versions
        Assume.that(versions.size() >= 2);
        
        // Initialize mocks for each property test iteration
        openMocks(this);
        rollbackService = new RollbackService(
                functionUnitRepository,
                processInstanceRepository,
                versionService
        );
        
        // Given: Multiple versions exist
        List<FunctionUnit> functionUnits = createFunctionUnits(functionUnitName, versions);
        // Rollback to the second version (index 1)
        // Versions are ordered newest to oldest, so index 0 is the newest
        // Rolling back to index 1 means we delete index 0 (1 version)
        FunctionUnit targetVersion = functionUnits.get(1);
        targetVersion.setIsActive(false);
        
        // Mock repository calls
        when(functionUnitRepository.findById(targetVersion.getId()))
                .thenReturn(Optional.of(targetVersion));
        when(versionService.getVersionHistory(functionUnitName))
                .thenReturn(functionUnits);
        when(processInstanceRepository.countByFunctionUnitVersionId(anyLong()))
                .thenReturn(0L);
        doNothing().when(processInstanceRepository).deleteByFunctionUnitVersionId(anyLong());
        doNothing().when(functionUnitRepository).deleteById(anyLong());
        doNothing().when(versionService).activateVersion(targetVersion.getId());
        
        // When: Rolling back to the target version
        RollbackResult result = rollbackService.rollbackToVersion(targetVersion.getId());
        
        // Then: The rollback should succeed
        assertThat(result.isSuccess()).isTrue();
        
        // And: All newer versions should be deleted
        // Versions are ordered from newest to oldest in the list
        // Target is at index 1, so we delete all versions before it (index 0)
        int targetIndex = functionUnits.indexOf(targetVersion);
        int expectedDeletions = targetIndex; // All versions before target in the list (newer versions)
        assertThat(result.getDeletedVersions()).hasSize(expectedDeletions);
        
        // Verify deleteById was called for each newer version
        verify(functionUnitRepository, times(expectedDeletions)).deleteById(anyLong());
    }
    
    /**
     * Property 22: Rollback Cascading Process Deletion
     * 
     * For any rollback operation, all process instances bound to versions being deleted 
     * should also be deleted.
     * 
     * **Validates: Requirements 6.4**
     */
    @Property(tries = 100)
    void rollbackDeletesProcessInstances(
            @ForAll("functionUnitNames") String functionUnitName,
            @ForAll("versionLists") List<String> versions,
            @ForAll("processInstanceCounts") List<Integer> processInstanceCounts) {
        
        // Skip if we don't have at least 2 versions
        Assume.that(versions.size() >= 2);
        Assume.that(processInstanceCounts.size() >= versions.size());
        
        // Initialize mocks for each property test iteration
        openMocks(this);
        rollbackService = new RollbackService(
                functionUnitRepository,
                processInstanceRepository,
                versionService
        );
        
        // Given: Multiple versions exist with process instances
        List<FunctionUnit> functionUnits = createFunctionUnits(functionUnitName, versions);
        // Rollback to the second version (index 1)
        FunctionUnit targetVersion = functionUnits.get(1);
        targetVersion.setIsActive(false);
        
        // Mock repository calls
        when(functionUnitRepository.findById(targetVersion.getId()))
                .thenReturn(Optional.of(targetVersion));
        when(versionService.getVersionHistory(functionUnitName))
                .thenReturn(functionUnits);
        
        // Mock process instance counts for each version
        // countByFunctionUnitVersionId is called twice per version:
        // 1. Once in calculateRollbackImpact()
        // 2. Once in rollbackToVersion() before deletion
        for (int i = 0; i < functionUnits.size(); i++) {
            int count = processInstanceCounts.get(i);
            when(processInstanceRepository.countByFunctionUnitVersionId(functionUnits.get(i).getId()))
                    .thenReturn((long) count);
        }
        
        doNothing().when(processInstanceRepository).deleteByFunctionUnitVersionId(anyLong());
        doNothing().when(functionUnitRepository).deleteById(anyLong());
        doNothing().when(versionService).activateVersion(targetVersion.getId());
        
        // When: Rolling back to the target version
        RollbackResult result = rollbackService.rollbackToVersion(targetVersion.getId());
        
        // Then: The rollback should succeed
        assertThat(result.isSuccess()).isTrue();
        
        // And: Process instances should be deleted for all versions being deleted
        // Target is at index 1, so we delete version at index 0 (1 version)
        int targetIndex = functionUnits.indexOf(targetVersion);
        int versionsToDelete = targetIndex; // All versions before target (newer versions)
        verify(processInstanceRepository, times(versionsToDelete))
                .deleteByFunctionUnitVersionId(anyLong());
        
        // And: The deleted process count should match the sum of process instances in deleted versions
        long expectedProcessDeletions = 0;
        for (int i = 0; i < versionsToDelete; i++) {
            expectedProcessDeletions += processInstanceCounts.get(i);
        }
        assertThat(result.getDeletedProcessCount()).isEqualTo(expectedProcessDeletions);
    }
    
    /**
     * Property 23: Rollback Atomicity
     * 
     * For any rollback operation, either all changes (version activation, version deletions, 
     * process deletions) should complete successfully, or none should occur (transaction rollback).
     * 
     * **Validates: Requirements 6.6, 11.2**
     */
    @Property(tries = 100)
    void rollbackIsAtomic(
            @ForAll("functionUnitNames") String functionUnitName,
            @ForAll("versionLists") List<String> versions) {
        
        // Skip if we don't have at least 2 versions
        Assume.that(versions.size() >= 2);
        
        // Initialize mocks for each property test iteration
        openMocks(this);
        rollbackService = new RollbackService(
                functionUnitRepository,
                processInstanceRepository,
                versionService
        );
        
        // Given: Multiple versions exist
        List<FunctionUnit> functionUnits = createFunctionUnits(functionUnitName, versions);
        FunctionUnit targetVersion = functionUnits.get(1);
        targetVersion.setIsActive(false);
        
        // Mock repository calls
        when(functionUnitRepository.findById(targetVersion.getId()))
                .thenReturn(Optional.of(targetVersion));
        when(versionService.getVersionHistory(functionUnitName))
                .thenReturn(functionUnits);
        when(processInstanceRepository.countByFunctionUnitVersionId(anyLong()))
                .thenReturn(0L);
        doNothing().when(processInstanceRepository).deleteByFunctionUnitVersionId(anyLong());
        doNothing().when(functionUnitRepository).deleteById(anyLong());
        
        // Simulate failure during version activation
        doThrow(new RuntimeException("Activation failed"))
                .when(versionService).activateVersion(targetVersion.getId());
        
        // When/Then: Rolling back should throw TransactionError
        assertThatThrownBy(() -> rollbackService.rollbackToVersion(targetVersion.getId()))
                .isInstanceOf(TransactionError.class);
        
        // Note: In a real transaction, all changes would be rolled back automatically
        // This test verifies that failures are properly wrapped in TransactionError
    }
    
    /**
     * Property 24: Rollback Transaction Failure Recovery
     * 
     * For any rollback operation that encounters a failure, the system state should be 
     * completely restored to the pre-rollback state.
     * 
     * **Validates: Requirements 6.7, 11.4**
     */
    @Property(tries = 100)
    void rollbackFailureRecovery(
            @ForAll("functionUnitNames") String functionUnitName,
            @ForAll("versionLists") List<String> versions) {
        
        // Skip if we don't have at least 2 versions
        Assume.that(versions.size() >= 2);
        
        // Initialize mocks for each property test iteration
        openMocks(this);
        rollbackService = new RollbackService(
                functionUnitRepository,
                processInstanceRepository,
                versionService
        );
        
        // Given: Multiple versions exist
        List<FunctionUnit> functionUnits = createFunctionUnits(functionUnitName, versions);
        FunctionUnit targetVersion = functionUnits.get(1);
        targetVersion.setIsActive(false);
        
        // Mock repository calls
        when(functionUnitRepository.findById(targetVersion.getId()))
                .thenReturn(Optional.of(targetVersion));
        when(versionService.getVersionHistory(functionUnitName))
                .thenReturn(functionUnits);
        // Return 0 for count check (so rollback can proceed)
        when(processInstanceRepository.countByFunctionUnitVersionId(anyLong()))
                .thenReturn(0L);
        
        // Simulate failure during process deletion (even though count is 0, deletion fails)
        doThrow(new RuntimeException("Process deletion failed"))
                .when(processInstanceRepository).deleteByFunctionUnitVersionId(anyLong());
        
        // When/Then: Rolling back should throw TransactionError
        assertThatThrownBy(() -> rollbackService.rollbackToVersion(targetVersion.getId()))
                .isInstanceOf(TransactionError.class)
                .hasMessageContaining("Rollback operation");
        
        // Note: In a real transaction with @Transactional, the database would automatically
        // rollback all changes. This test verifies that failures are properly caught and wrapped.
    }
    
    /**
     * Property 35: Post-Rollback Process Creation
     * 
     * For any process instance created after a rollback, it should use the process definition 
     * key of the rolled-back (now active) version.
     * 
     * **Validates: Requirements 12.4**
     */
    @Property(tries = 100)
    void postRollbackProcessCreationUsesRolledBackVersion(
            @ForAll("functionUnitNames") String functionUnitName,
            @ForAll("versionLists") List<String> versions) {
        
        // Skip if we don't have at least 2 versions
        Assume.that(versions.size() >= 2);
        
        // Initialize mocks for each property test iteration
        openMocks(this);
        rollbackService = new RollbackService(
                functionUnitRepository,
                processInstanceRepository,
                versionService
        );
        
        // Given: Multiple versions exist
        List<FunctionUnit> functionUnits = createFunctionUnits(functionUnitName, versions);
        FunctionUnit targetVersion = functionUnits.get(1);
        targetVersion.setIsActive(false);
        
        // Mock repository calls for rollback
        when(functionUnitRepository.findById(targetVersion.getId()))
                .thenReturn(Optional.of(targetVersion));
        when(versionService.getVersionHistory(functionUnitName))
                .thenReturn(functionUnits);
        when(processInstanceRepository.countByFunctionUnitVersionId(anyLong()))
                .thenReturn(0L);
        doNothing().when(processInstanceRepository).deleteByFunctionUnitVersionId(anyLong());
        doNothing().when(functionUnitRepository).deleteById(anyLong());
        doNothing().when(versionService).activateVersion(targetVersion.getId());
        
        // When: Rolling back to the target version
        RollbackResult result = rollbackService.rollbackToVersion(targetVersion.getId());
        
        // Then: The rollback should succeed
        assertThat(result.isSuccess()).isTrue();
        
        // And: After rollback, the target version should be active
        // (verified by the activateVersion call)
        verify(versionService).activateVersion(targetVersion.getId());
        
        // And: The rolled back version should be the target version
        assertThat(result.getRolledBackToVersion()).isEqualTo(targetVersion.getVersion());
        
        // Note: The actual process creation would be tested in ProcessService tests
        // This test verifies that the rollback correctly activates the target version
    }
    
    /**
     * Helper method to create a list of FunctionUnit entities with sequential versions
     */
    private List<FunctionUnit> createFunctionUnits(String functionUnitName, List<String> versions) {
        List<FunctionUnit> functionUnits = new ArrayList<>();
        for (int i = 0; i < versions.size(); i++) {
            FunctionUnit fu = FunctionUnit.builder()
                    .id((long) (i + 1))
                    .name(functionUnitName)
                    .version(versions.get(i))
                    .isActive(i == 0) // First (latest) version is active
                    .deployedAt(Instant.now().minusSeconds(versions.size() - i))
                    .build();
            functionUnits.add(fu);
        }
        return functionUnits;
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
     * Arbitrary for generating lists of semantic versions (sorted descending, strictly decreasing)
     */
    @Provide
    Arbitrary<List<String>> versionLists() {
        return Arbitraries.integers().between(2, 5)
                .flatMap(size -> {
                    // Generate a base major version
                    return Arbitraries.integers().between(1, 10)
                            .flatMap(baseMajor -> {
                                List<Arbitrary<String>> versionArbitraries = new ArrayList<>();
                                // Generate versions in strictly descending order
                                for (int i = 0; i < size; i++) {
                                    final int index = i;
                                    versionArbitraries.add(
                                            Combinators.combine(
                                                    Arbitraries.integers().between(0, 5),
                                                    Arbitraries.integers().between(0, 10)
                                            ).as((minor, patch) -> 
                                                    String.format("%d.%d.%d", 
                                                            baseMajor + size - index - 1, // Strictly decreasing major versions
                                                            minor, 
                                                            patch))
                                    );
                                }
                                return Combinators.combine(versionArbitraries).as(versions -> versions);
                            });
                });
    }
    
    /**
     * Arbitrary for generating lists of process instance counts
     */
    @Provide
    Arbitrary<List<Integer>> processInstanceCounts() {
        return Arbitraries.integers().between(0, 10)
                .list().ofMinSize(2).ofMaxSize(10);
    }
}
