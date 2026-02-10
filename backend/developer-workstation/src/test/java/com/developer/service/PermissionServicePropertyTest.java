package com.developer.service;

import com.developer.entity.FunctionUnit;
import com.developer.entity.FunctionUnitAccess;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitAccessRepository;
import com.developer.repository.FunctionUnitRepository;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * Property-based tests for PermissionService.
 * Tests universal properties that should hold for all valid permission operations.
 */
class PermissionServicePropertyTest {
    
    @Mock
    private FunctionUnitAccessRepository accessRepository;
    
    @Mock
    private FunctionUnitRepository functionUnitRepository;
    
    private PermissionService permissionService;
    
    @BeforeEach
    void setUp() {
        openMocks(this);
        permissionService = new PermissionService(
                accessRepository,
                functionUnitRepository
        );
    }
    
    /**
     * Property 12: Permission Inheritance
     * 
     * For any function unit deployment where a previous version exists, the new version 
     * should have permissions that match the previous active version's permissions.
     * 
     * **Validates: Requirements 4.1**
     */
    @Property(tries = 100)
    void permissionInheritance(
            @ForAll("functionUnitNames") String functionUnitName,
            @ForAll("versions") String sourceVersion,
            @ForAll("versions") String targetVersion,
            @ForAll("permissionCounts") int permissionCount) {
        
        // Initialize mocks for each property test iteration
        openMocks(this);
        permissionService = new PermissionService(
                accessRepository,
                functionUnitRepository
        );
        
        // Given: A source version with permissions and a target version
        FunctionUnit sourceFunctionUnit = createFunctionUnit(1L, functionUnitName, sourceVersion, true);
        FunctionUnit targetFunctionUnit = createFunctionUnit(2L, functionUnitName, targetVersion, false);
        
        List<FunctionUnitAccess> sourcePermissions = createPermissions(sourceFunctionUnit, permissionCount);
        
        // Mock repository calls
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(sourceFunctionUnit));
        when(functionUnitRepository.findById(2L)).thenReturn(Optional.of(targetFunctionUnit));
        when(accessRepository.findByFunctionUnitId(1L)).thenReturn(sourcePermissions);
        
        // Mock saveAll to return the permissions with the target function unit
        when(accessRepository.saveAll(anyList())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Iterable<FunctionUnitAccess> permissions = invocation.getArgument(0);
            List<FunctionUnitAccess> permissionList = new ArrayList<>();
            permissions.forEach(permissionList::add);
            return permissionList.stream()
                    .map(p -> FunctionUnitAccess.builder()
                            .id((long) (Math.random() * 1000))
                            .functionUnit(targetFunctionUnit)
                            .accessType(p.getAccessType())
                            .targetType(p.getTargetType())
                            .targetId(p.getTargetId())
                            .build())
                    .collect(Collectors.toList());
        });
        
        // When: Copying permissions from source to target
        int copiedCount = permissionService.copyPermissions(1L, 2L);
        
        // Then: The number of copied permissions should match the source
        assertThat(copiedCount).isEqualTo(permissionCount);
        
        // And: saveAll should be called with permissions for the target version
        verify(accessRepository).saveAll(argThat(permissions -> {
            if (permissions == null) {
                return false;
            }
            List<FunctionUnitAccess> permissionList = new ArrayList<>();
            permissions.forEach(permissionList::add);
            if (permissionList.size() != permissionCount) {
                return false;
            }
            // Verify all permissions reference the target function unit
            return permissionList.stream()
                    .allMatch(p -> p.getFunctionUnit().equals(targetFunctionUnit));
        }));
    }
    
    /**
     * Property 13: Permission Isolation
     * 
     * For any function unit with multiple versions, modifying permissions should only affect 
     * the active version, leaving inactive versions' permissions unchanged.
     * 
     * **Validates: Requirements 4.3**
     */
    @Property(tries = 100)
    void permissionIsolation(
            @ForAll("functionUnitNames") String functionUnitName,
            @ForAll("versions") String activeVersion,
            @ForAll("versions") String inactiveVersion,
            @ForAll("permissionCounts") int initialPermissionCount,
            @ForAll("permissionCounts") int newPermissionCount) {
        
        // Initialize mocks for each property test iteration
        openMocks(this);
        permissionService = new PermissionService(
                accessRepository,
                functionUnitRepository
        );
        
        // Given: An active version and an inactive version with permissions
        FunctionUnit activeFunctionUnit = createFunctionUnit(1L, functionUnitName, activeVersion, true);
        FunctionUnit inactiveFunctionUnit = createFunctionUnit(2L, functionUnitName, inactiveVersion, false);
        
        List<FunctionUnitAccess> newPermissions = createPermissions(activeFunctionUnit, newPermissionCount);
        
        // Mock repository calls
        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(activeFunctionUnit));
        when(functionUnitRepository.findById(2L)).thenReturn(Optional.of(inactiveFunctionUnit));
        doNothing().when(accessRepository).deleteByFunctionUnitId(1L);
        when(accessRepository.saveAll(anyList())).thenReturn(newPermissions);
        
        // When: Updating permissions for the active version
        List<FunctionUnitAccess> updatedPermissions = permissionService.updatePermissions(1L, newPermissions);
        
        // Then: The update should succeed
        assertThat(updatedPermissions).hasSize(newPermissionCount);
        
        // And: Only the active version's permissions should be modified
        verify(accessRepository).deleteByFunctionUnitId(1L);
        verify(accessRepository, never()).deleteByFunctionUnitId(2L);
        
        // And: The inactive version's permissions should not be touched
        verify(accessRepository, never()).findByFunctionUnitId(2L);
        
        // When: Attempting to update permissions for the inactive version
        // Then: It should throw IllegalStateException
        assertThatThrownBy(() -> permissionService.updatePermissions(2L, newPermissions))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot update permissions for inactive version");
    }
    
    /**
     * Property 14: Permission Version Separation
     * 
     * For any function unit with multiple versions, each version should have its own 
     * distinct permission records (not shared).
     * 
     * **Validates: Requirements 4.4**
     */
    @Property(tries = 100)
    void permissionVersionSeparation(
            @ForAll("functionUnitNames") String functionUnitName,
            @ForAll("versionLists") List<String> versions,
            @ForAll("permissionCounts") int permissionCount) {
        
        // Skip if we don't have at least 2 versions
        Assume.that(versions.size() >= 2);
        
        // Initialize mocks for each property test iteration
        openMocks(this);
        permissionService = new PermissionService(
                accessRepository,
                functionUnitRepository
        );
        
        // Given: Multiple versions of a function unit
        List<FunctionUnit> functionUnits = new ArrayList<>();
        for (int i = 0; i < versions.size(); i++) {
            FunctionUnit fu = createFunctionUnit((long) (i + 1), functionUnitName, versions.get(i), i == 0);
            functionUnits.add(fu);
            
            // Mock repository to return the function unit
            when(functionUnitRepository.findById((long) (i + 1))).thenReturn(Optional.of(fu));
            
            // Mock permissions for each version
            List<FunctionUnitAccess> permissions = createPermissions(fu, permissionCount);
            when(accessRepository.findByFunctionUnitId((long) (i + 1))).thenReturn(permissions);
        }
        
        // When: Retrieving permissions for each version
        List<List<FunctionUnitAccess>> allPermissions = new ArrayList<>();
        for (int i = 0; i < versions.size(); i++) {
            List<FunctionUnitAccess> permissions = permissionService.getPermissions((long) (i + 1));
            allPermissions.add(permissions);
        }
        
        // Then: Each version should have its own permission records
        assertThat(allPermissions).hasSize(versions.size());
        
        // And: Each version should have the expected number of permissions
        for (List<FunctionUnitAccess> permissions : allPermissions) {
            assertThat(permissions).hasSize(permissionCount);
        }
        
        // And: Permissions should be retrieved separately for each version
        for (int i = 0; i < versions.size(); i++) {
            verify(accessRepository).findByFunctionUnitId((long) (i + 1));
        }
        
        // And: Each permission should reference its own version
        for (int i = 0; i < versions.size(); i++) {
            final int versionIndex = i;
            List<FunctionUnitAccess> permissions = allPermissions.get(i);
            assertThat(permissions).allMatch(p -> 
                    p.getFunctionUnit().getId().equals((long) (versionIndex + 1)));
        }
    }
    
    /**
     * Helper method to create a FunctionUnit entity
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
    
    /**
     * Helper method to create a list of FunctionUnitAccess entities
     */
    private List<FunctionUnitAccess> createPermissions(FunctionUnit functionUnit, int count) {
        List<FunctionUnitAccess> permissions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            permissions.add(FunctionUnitAccess.builder()
                    .id((long) (i + 1))
                    .functionUnit(functionUnit)
                    .accessType("READ")
                    .targetType("ROLE")
                    .targetId("role-" + i)
                    .build());
        }
        return permissions;
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
     * Arbitrary for generating lists of semantic versions
     */
    @Provide
    Arbitrary<List<String>> versionLists() {
        return Arbitraries.integers().between(2, 5)
                .flatMap(size -> {
                    List<Arbitrary<String>> versionArbitraries = new ArrayList<>();
                    for (int i = 0; i < size; i++) {
                        versionArbitraries.add(versions());
                    }
                    return Combinators.combine(versionArbitraries).as(versions -> versions);
                });
    }
    
    /**
     * Arbitrary for generating permission counts
     */
    @Provide
    Arbitrary<Integer> permissionCounts() {
        return Arbitraries.integers().between(0, 10);
    }
}
