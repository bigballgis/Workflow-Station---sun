package com.admin.bi.service;

import com.admin.bi.component.SupersetRoleSyncComponent;
import com.admin.bi.dto.request.RbacMappingUpdateRequest;
import com.admin.bi.entity.BiRbacMapping;
import com.admin.bi.entity.BiSupersetRole;
import com.admin.bi.enums.SupersetRoleStatus;
import com.admin.bi.repository.BiRbacMappingRepository;
import com.admin.bi.repository.BiSupersetRoleRepository;
import com.admin.bi.service.impl.BiRbacMappingServiceImpl;
import com.admin.repository.RoleRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BiRbacMappingService 属性测试
 *
 * Feature: bi-management
 * Property 15: RBAC 映射全量替换
 * Property 16: RBAC 映射 ACTIVE 约束
 *
 * Validates: Requirements 7.9, 7.10, 7.12, 7.13
 */
class BiRbacMappingServicePropertyTest {

    private BiRbacMappingRepository mappingRepository;
    private BiSupersetRoleRepository supersetRoleRepository;
    private RoleRepository roleRepository;
    private SupersetRoleSyncComponent syncComponent;
    private BiRbacMappingServiceImpl service;

    /** Tracks mappings saved during updateMapping */
    private List<BiRbacMapping> savedMappings;
    /** Tracks sysRoleIds whose mappings were deleted */
    private List<String> deletedSysRoleIds;

    @BeforeTry
    void setUp() {
        mappingRepository = mock(BiRbacMappingRepository.class);
        supersetRoleRepository = mock(BiSupersetRoleRepository.class);
        roleRepository = mock(RoleRepository.class);
        syncComponent = mock(SupersetRoleSyncComponent.class);
        service = new BiRbacMappingServiceImpl(
                mappingRepository, supersetRoleRepository, roleRepository, syncComponent);
        savedMappings = new ArrayList<>();
        deletedSysRoleIds = new ArrayList<>();

        // Capture save calls
        when(mappingRepository.save(any(BiRbacMapping.class))).thenAnswer(inv -> {
            BiRbacMapping m = inv.getArgument(0);
            savedMappings.add(deepCopyMapping(m));
            return m;
        });

        // Capture delete calls
        doAnswer(inv -> {
            String sysRoleId = inv.getArgument(0);
            deletedSysRoleIds.add(sysRoleId);
            return null;
        }).when(mappingRepository).deleteBySysRoleId(anyString());
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<List<BiSupersetRole>> activeSupersetRoles() {
        return supersetRoleArbitrary(SupersetRoleStatus.ACTIVE)
                .list().ofMinSize(1).ofMaxSize(8)
                .filter(list -> {
                    Set<Integer> ids = list.stream()
                            .map(BiSupersetRole::getSupersetRoleId)
                            .collect(Collectors.toSet());
                    return ids.size() == list.size();
                });
    }

    @Provide
    Arbitrary<List<BiSupersetRole>> mixedSupersetRoles() {
        return supersetRoleArbitrary(null)
                .list().ofMinSize(1).ofMaxSize(10)
                .filter(list -> {
                    Set<Integer> ids = list.stream()
                            .map(BiSupersetRole::getSupersetRoleId)
                            .collect(Collectors.toSet());
                    return ids.size() == list.size();
                })
                .filter(list -> {
                    // Ensure at least one ACTIVE and one INACTIVE
                    boolean hasActive = list.stream().anyMatch(r -> r.getStatus() == SupersetRoleStatus.ACTIVE);
                    boolean hasInactive = list.stream().anyMatch(r -> r.getStatus() == SupersetRoleStatus.INACTIVE);
                    return hasActive && hasInactive;
                });
    }

    private Arbitrary<BiSupersetRole> supersetRoleArbitrary(SupersetRoleStatus fixedStatus) {
        Arbitrary<SupersetRoleStatus> statusArb = fixedStatus != null
                ? Arbitraries.just(fixedStatus)
                : Arbitraries.of(SupersetRoleStatus.ACTIVE, SupersetRoleStatus.INACTIVE);

        return Combinators.combine(
                Arbitraries.integers().between(1, 500),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
                statusArb
        ).as((supersetRoleId, name, status) ->
                BiSupersetRole.builder()
                        .id(new Random().nextInt(10000) + 1)
                        .supersetRoleId(supersetRoleId)
                        .name(name)
                        .status(status)
                        .lastSyncedAt(LocalDateTime.now().minusDays(1))
                        .createdAt(LocalDateTime.now().minusDays(2))
                        .updatedAt(LocalDateTime.now().minusDays(1))
                        .build()
        );
    }

    // ========== Property 15: RBAC 映射全量替换 ==========

    /**
     * Property 15: RBAC 映射全量替换
     *
     * For any Sys_Role and any set of ACTIVE Superset_Roles, after executing
     * updateMapping, the effective mappings for that Sys_Role should exactly
     * equal the submitted Superset_Role set (old mappings deleted, new ones created).
     *
     * Feature: bi-management, Property 15: RBAC mapping full replacement
     * Validates: Requirements 7.9, 7.10
     */
    @Property(tries = 100)
    @Tag("Feature: bi-management, Property 15: RBAC mapping full replacement")
    void rbacMappingFullReplacement(
            @ForAll("activeSupersetRoles") List<BiSupersetRole> allActiveRoles
    ) {
        String sysRoleId = UUID.randomUUID().toString();

        // Pick a random subset of active roles to map
        Random rng = new Random();
        List<BiSupersetRole> selectedRoles = allActiveRoles.stream()
                .filter(r -> rng.nextBoolean())
                .collect(Collectors.toList());

        List<Integer> selectedIds = selectedRoles.stream()
                .map(BiSupersetRole::getSupersetRoleId)
                .collect(Collectors.toList());

        // Mock: supersetRoleRepository returns the requested roles
        when(supersetRoleRepository.findBySupersetRoleIdIn(anyList()))
                .thenAnswer(inv -> {
                    List<Integer> requestedIds = inv.getArgument(0);
                    return allActiveRoles.stream()
                            .filter(r -> requestedIds.contains(r.getSupersetRoleId()))
                            .collect(Collectors.toList());
                });

        // Execute
        savedMappings.clear();
        deletedSysRoleIds.clear();

        RbacMappingUpdateRequest request = new RbacMappingUpdateRequest();
        request.setSupersetRoleIds(selectedIds);
        service.updateMapping(sysRoleId, request);

        // Verify: old mappings were deleted for this sysRoleId
        assertThat(deletedSysRoleIds).contains(sysRoleId);

        // Verify: new mappings exactly match the submitted set
        Set<Integer> savedSupersetRoleIds = savedMappings.stream()
                .map(BiRbacMapping::getSupersetRoleId)
                .collect(Collectors.toSet());
        Set<Integer> expectedIds = new HashSet<>(selectedIds);
        assertThat(savedSupersetRoleIds).isEqualTo(expectedIds);

        // Verify: all saved mappings belong to the correct sysRoleId
        for (BiRbacMapping saved : savedMappings) {
            assertThat(saved.getSysRoleId()).isEqualTo(sysRoleId);
        }

        // Verify: number of saved mappings equals number of submitted IDs
        assertThat(savedMappings).hasSize(selectedIds.size());
    }

    /**
     * Property 15 (empty case): When submitting an empty list, all existing
     * mappings should be deleted and no new ones created.
     *
     * Feature: bi-management, Property 15: RBAC mapping full replacement - empty
     * Validates: Requirements 7.9, 7.10
     */
    @Property(tries = 100)
    @Tag("Feature: bi-management, Property 15: RBAC mapping full replacement - empty")
    void rbacMappingFullReplacementWithEmptyList() {
        String sysRoleId = UUID.randomUUID().toString();

        savedMappings.clear();
        deletedSysRoleIds.clear();

        RbacMappingUpdateRequest request = new RbacMappingUpdateRequest();
        request.setSupersetRoleIds(Collections.emptyList());
        service.updateMapping(sysRoleId, request);

        // Verify: old mappings were deleted
        assertThat(deletedSysRoleIds).contains(sysRoleId);

        // Verify: no new mappings created
        assertThat(savedMappings).isEmpty();
    }

    // ========== Property 16: RBAC 映射 ACTIVE 约束 ==========

    /**
     * Property 16: RBAC 映射 ACTIVE 约束 - 仅 ACTIVE 可映射
     *
     * Only ACTIVE Superset_Roles can be mapped. Attempting to map an INACTIVE
     * Superset_Role should throw an exception.
     *
     * Feature: bi-management, Property 16: RBAC mapping ACTIVE constraint - mapping
     * Validates: Requirements 7.12
     */
    @Property(tries = 100)
    @Tag("Feature: bi-management, Property 16: RBAC mapping ACTIVE constraint - mapping")
    void onlyActiveSupersetRolesCanBeMapped(
            @ForAll("mixedSupersetRoles") List<BiSupersetRole> allRoles
    ) {
        String sysRoleId = UUID.randomUUID().toString();

        // Select at least one INACTIVE role to include in the request
        List<BiSupersetRole> inactiveRoles = allRoles.stream()
                .filter(r -> r.getStatus() == SupersetRoleStatus.INACTIVE)
                .collect(Collectors.toList());
        BiSupersetRole inactiveRole = inactiveRoles.get(0);

        // Also include some ACTIVE roles
        List<BiSupersetRole> activeRoles = allRoles.stream()
                .filter(r -> r.getStatus() == SupersetRoleStatus.ACTIVE)
                .collect(Collectors.toList());

        List<Integer> requestedIds = new ArrayList<>();
        if (!activeRoles.isEmpty()) {
            requestedIds.add(activeRoles.get(0).getSupersetRoleId());
        }
        requestedIds.add(inactiveRole.getSupersetRoleId());

        // Mock: return the requested roles with their actual statuses
        when(supersetRoleRepository.findBySupersetRoleIdIn(anyList()))
                .thenAnswer(inv -> {
                    List<Integer> ids = inv.getArgument(0);
                    return allRoles.stream()
                            .filter(r -> ids.contains(r.getSupersetRoleId()))
                            .collect(Collectors.toList());
                });

        RbacMappingUpdateRequest request = new RbacMappingUpdateRequest();
        request.setSupersetRoleIds(requestedIds);

        // Should throw because INACTIVE role is included
        assertThatThrownBy(() -> service.updateMapping(sysRoleId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not ACTIVE");

        // Verify: no mappings were saved (validation fails before delete/save)
        verify(mappingRepository, never()).deleteBySysRoleId(anyString());
        verify(mappingRepository, never()).save(any(BiRbacMapping.class));
    }

    /**
     * Property 16: RBAC 映射 ACTIVE 约束 - 有效映射查询排除 INACTIVE
     *
     * When querying effective Superset role IDs, INACTIVE Superset_Roles
     * should be excluded even if mapping records exist.
     *
     * Feature: bi-management, Property 16: RBAC mapping ACTIVE constraint - effective query
     * Validates: Requirements 7.13
     */
    @Property(tries = 100)
    @Tag("Feature: bi-management, Property 16: RBAC mapping ACTIVE constraint - effective query")
    void effectiveMappingQueryExcludesInactive(
            @ForAll("mixedSupersetRoles") List<BiSupersetRole> allRoles
    ) {
        String sysRoleId = UUID.randomUUID().toString();
        List<String> sysRoleIds = List.of(sysRoleId);

        // Create mappings for ALL roles (both ACTIVE and INACTIVE)
        List<BiRbacMapping> existingMappings = allRoles.stream()
                .map(r -> BiRbacMapping.builder()
                        .id(UUID.randomUUID().toString())
                        .sysRoleId(sysRoleId)
                        .supersetRoleId(r.getSupersetRoleId())
                        .createdAt(LocalDateTime.now())
                        .build())
                .collect(Collectors.toList());

        // Mock: return all mappings for the sysRoleId
        when(mappingRepository.findBySysRoleIdIn(sysRoleIds)).thenReturn(existingMappings);

        // Mock: return all superset roles with their actual statuses
        when(supersetRoleRepository.findBySupersetRoleIdIn(anyList()))
                .thenAnswer(inv -> {
                    List<Integer> ids = inv.getArgument(0);
                    return allRoles.stream()
                            .filter(r -> ids.contains(r.getSupersetRoleId()))
                            .collect(Collectors.toList());
                });

        // Execute
        List<Integer> effectiveIds = service.getEffectiveSupersetRoleIds(sysRoleIds);

        // Verify: only ACTIVE roles are returned
        Set<Integer> activeRoleIds = allRoles.stream()
                .filter(r -> r.getStatus() == SupersetRoleStatus.ACTIVE)
                .map(BiSupersetRole::getSupersetRoleId)
                .collect(Collectors.toSet());

        Set<Integer> inactiveRoleIds = allRoles.stream()
                .filter(r -> r.getStatus() == SupersetRoleStatus.INACTIVE)
                .map(BiSupersetRole::getSupersetRoleId)
                .collect(Collectors.toSet());

        // All returned IDs should be ACTIVE
        for (Integer id : effectiveIds) {
            assertThat(activeRoleIds).contains(id);
        }

        // No INACTIVE IDs should be returned
        for (Integer id : effectiveIds) {
            assertThat(inactiveRoleIds).doesNotContain(id);
        }

        // All ACTIVE roles that have mappings should be returned
        assertThat(new HashSet<>(effectiveIds)).isEqualTo(activeRoleIds);

        // No duplicates
        assertThat(effectiveIds).doesNotHaveDuplicates();
    }

    // ========== Helpers ==========

    private BiRbacMapping deepCopyMapping(BiRbacMapping source) {
        return BiRbacMapping.builder()
                .id(source.getId())
                .sysRoleId(source.getSysRoleId())
                .supersetRoleId(source.getSupersetRoleId())
                .createdAt(source.getCreatedAt())
                .createdBy(source.getCreatedBy())
                .build();
    }
}
