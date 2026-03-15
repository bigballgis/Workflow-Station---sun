package com.admin.bi.service;

import com.admin.bi.component.SupersetRoleSyncComponent;
import com.admin.bi.dto.request.RbacMappingUpdateRequest;
import com.admin.bi.dto.response.RbacMappingResponse;
import com.admin.bi.dto.response.SupersetRoleResponse;
import com.admin.bi.entity.BiRbacMapping;
import com.admin.bi.entity.BiSupersetRole;
import com.admin.bi.enums.SupersetRoleStatus;
import com.admin.bi.repository.BiRbacMappingRepository;
import com.admin.bi.repository.BiSupersetRoleRepository;
import com.admin.bi.service.impl.BiRbacMappingServiceImpl;
import com.admin.repository.RoleRepository;
import com.platform.security.entity.Role;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Preservation 属性测试 - 已有功能行为保持不变
 *
 * 在非 Bug 条件输入下验证现有功能行为不受修复影响。
 * 这些测试在未修复代码上运行时预期 **通过**（确认基线行为）。
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5
 */
class BiRbacMappingPreservationPropertyTest {

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
                .list().ofMinSize(1).ofMaxSize(6)
                .filter(list -> list.stream()
                        .map(BiSupersetRole::getSupersetRoleId)
                        .distinct().count() == list.size());
    }

    @Provide
    Arbitrary<List<BiSupersetRole>> mixedSupersetRoles() {
        return supersetRoleArbitrary(null)
                .list().ofMinSize(2).ofMaxSize(8)
                .filter(list -> list.stream()
                        .map(BiSupersetRole::getSupersetRoleId)
                        .distinct().count() == list.size())
                .filter(list -> {
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
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(15),
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


    /**
     * Helper: generate a list of active roles where ALL have mapping records.
     * This is the non-bug-condition scenario: every role in the list is mapped.
     */
    @Provide
    Arbitrary<MappedRolesScenario> allMappedRolesScenario() {
        return Combinators.combine(
                Arbitraries.integers().between(1, 5),
                activeSupersetRoles()
        ).flatAs((roleCount, supersetRoles) -> {
            // Generate unique role names
            return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                    .list().ofSize(roleCount)
                    .filter(names -> names.stream().distinct().count() == roleCount)
                    .map(names -> {
                        List<Role> roles = new ArrayList<>();
                        List<BiRbacMapping> allMappings = new ArrayList<>();
                        Map<String, List<BiRbacMapping>> mappingsByRole = new HashMap<>();

                        for (int i = 0; i < names.size(); i++) {
                            String roleId = "role-" + UUID.randomUUID().toString().substring(0, 8);
                            Role role = Role.builder()
                                    .id(roleId)
                                    .name(names.get(i))
                                    .code("CODE_" + names.get(i).toUpperCase())
                                    .type("BU_UNBOUNDED")
                                    .status("ACTIVE")
                                    .build();
                            roles.add(role);

                            // Each mapped role gets 1-3 superset role mappings
                            int mappingCount = Math.min(1 + (i % 3), supersetRoles.size());
                            List<BiRbacMapping> roleMappings = new ArrayList<>();
                            for (int j = 0; j < mappingCount; j++) {
                                BiRbacMapping mapping = BiRbacMapping.builder()
                                        .id(UUID.randomUUID().toString())
                                        .sysRoleId(roleId)
                                        .supersetRoleId(supersetRoles.get(j % supersetRoles.size()).getSupersetRoleId())
                                        .createdAt(LocalDateTime.now().minusHours(i + 1))
                                        .build();
                                roleMappings.add(mapping);
                                allMappings.add(mapping);
                            }
                            mappingsByRole.put(roleId, roleMappings);
                        }

                        return new MappedRolesScenario(roles, allMappings, mappingsByRole, supersetRoles);
                    });
        });
    }

    // ========== P2a: listMappings returns supersetRoles consistent with bi_rbac_mapping records ==========

    /**
     * P2a: For all mapped system roles, listMappings returns supersetRoles
     * consistent with bi_rbac_mapping table records.
     *
     * Non-bug-condition: ALL roles in the scenario have mapping records.
     * The current listMappings (based on findAllActive) will include these roles
     * and their mappings should be correctly populated.
     *
     * Validates: Requirements 3.2, 3.5
     */
    @Property(tries = 50)
    @Tag("Feature: rbac-mapping-manual-creation, Property 2a: Preservation - listMappings consistency")
    void listMappingsReturnsSupersetRolesConsistentWithMappingTable(
            @ForAll("allMappedRolesScenario") MappedRolesScenario scenario
    ) {
        // Mock: roleRepository.findAllActive() returns all roles (all are mapped in this scenario)
        when(roleRepository.findAllActive()).thenReturn(scenario.roles);

        // Mock: mappingRepository.findAll() returns all mappings
        when(mappingRepository.findAll()).thenReturn(scenario.allMappings);

        // Mock: supersetRoleRepository.findAll() returns all superset roles
        when(supersetRoleRepository.findAll()).thenReturn(scenario.supersetRoles);

        // Execute
        List<RbacMappingResponse> result = service.listMappings(null, null);

        // Verify: each mapped role's supersetRoles matches the mapping table records
        for (RbacMappingResponse response : result) {
            String sysRoleId = response.getSysRoleId();
            List<BiRbacMapping> expectedMappings = scenario.mappingsByRole.getOrDefault(sysRoleId, Collections.emptyList());

            // Collect expected superset role IDs from mapping table
            Set<Integer> expectedSupersetRoleIds = expectedMappings.stream()
                    .map(BiRbacMapping::getSupersetRoleId)
                    .collect(Collectors.toSet());

            // Collect actual superset role IDs from response
            Set<Integer> actualSupersetRoleIds = response.getSupersetRoles().stream()
                    .map(SupersetRoleResponse::getSupersetRoleId)
                    .collect(Collectors.toSet());

            // The response supersetRoles should match the mapping table records
            // (only those that exist in the superset role registry)
            Set<Integer> registeredSupersetRoleIds = scenario.supersetRoles.stream()
                    .map(BiSupersetRole::getSupersetRoleId)
                    .collect(Collectors.toSet());
            Set<Integer> expectedRegistered = expectedSupersetRoleIds.stream()
                    .filter(registeredSupersetRoleIds::contains)
                    .collect(Collectors.toSet());

            assertThat(actualSupersetRoleIds)
                    .as("For role %s, supersetRoles in response should match bi_rbac_mapping records", sysRoleId)
                    .isEqualTo(expectedRegistered);
        }

        // Verify: lastUpdatedAt is set for roles that have mappings
        for (RbacMappingResponse response : result) {
            List<BiRbacMapping> roleMappings = scenario.mappingsByRole.getOrDefault(response.getSysRoleId(), Collections.emptyList());
            if (!roleMappings.isEmpty()) {
                LocalDateTime expectedLastUpdated = roleMappings.stream()
                        .map(BiRbacMapping::getCreatedAt)
                        .filter(Objects::nonNull)
                        .max(LocalDateTime::compareTo)
                        .orElse(null);
                assertThat(response.getLastUpdatedAt())
                        .as("lastUpdatedAt should reflect the latest mapping creation time")
                        .isEqualTo(expectedLastUpdated);
            }
        }
    }

    // ========== P2b: updateMapping full replacement semantics unchanged ==========

    /**
     * P2b: updateMapping full replacement semantics unchanged.
     *
     * For any sysRoleId and any set of ACTIVE Superset_Roles, updateMapping
     * deletes all existing mappings and creates new ones matching the request.
     * This verifies the same behavior as existing Property 15.
     *
     * Validates: Requirements 3.2
     */
    @Property(tries = 50)
    @Tag("Feature: rbac-mapping-manual-creation, Property 2b: Preservation - updateMapping full replacement")
    void updateMappingFullReplacementSemanticsUnchanged(
            @ForAll("activeSupersetRoles") List<BiSupersetRole> allActiveRoles
    ) {
        String sysRoleId = UUID.randomUUID().toString();

        // Pick a subset of active roles to map
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

        savedMappings.clear();
        deletedSysRoleIds.clear();

        RbacMappingUpdateRequest request = new RbacMappingUpdateRequest();
        request.setSupersetRoleIds(selectedIds);
        service.updateMapping(sysRoleId, request);

        // Verify: old mappings were deleted for this sysRoleId
        assertThat(deletedSysRoleIds)
                .as("updateMapping should delete existing mappings before creating new ones")
                .contains(sysRoleId);

        // Verify: new mappings exactly match the submitted set
        Set<Integer> savedSupersetRoleIds = savedMappings.stream()
                .map(BiRbacMapping::getSupersetRoleId)
                .collect(Collectors.toSet());
        assertThat(savedSupersetRoleIds)
                .as("Saved mappings should exactly match the requested superset role IDs")
                .isEqualTo(new HashSet<>(selectedIds));

        // Verify: all saved mappings belong to the correct sysRoleId
        for (BiRbacMapping saved : savedMappings) {
            assertThat(saved.getSysRoleId()).isEqualTo(sysRoleId);
        }
    }

    // ========== P2c: getEffectiveSupersetRoleIds only returns ACTIVE roles ==========

    /**
     * P2c: getEffectiveSupersetRoleIds only returns ACTIVE roles.
     *
     * When querying effective Superset role IDs, INACTIVE roles are excluded
     * even if mapping records exist. This verifies the same behavior as
     * existing Property 16.
     *
     * Validates: Requirements 3.4, 3.5
     */
    @Property(tries = 50)
    @Tag("Feature: rbac-mapping-manual-creation, Property 2c: Preservation - getEffectiveSupersetRoleIds ACTIVE only")
    void getEffectiveSupersetRoleIdsOnlyReturnsActiveRoles(
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

        // Collect expected sets
        Set<Integer> activeRoleIds = allRoles.stream()
                .filter(r -> r.getStatus() == SupersetRoleStatus.ACTIVE)
                .map(BiSupersetRole::getSupersetRoleId)
                .collect(Collectors.toSet());

        Set<Integer> inactiveRoleIds = allRoles.stream()
                .filter(r -> r.getStatus() == SupersetRoleStatus.INACTIVE)
                .map(BiSupersetRole::getSupersetRoleId)
                .collect(Collectors.toSet());

        // All returned IDs should be ACTIVE
        assertThat(effectiveIds)
                .as("All returned IDs should be from ACTIVE superset roles")
                .allMatch(activeRoleIds::contains);

        // No INACTIVE IDs should be returned
        assertThat(effectiveIds)
                .as("No INACTIVE superset role IDs should be returned")
                .noneMatch(inactiveRoleIds::contains);

        // All ACTIVE roles that have mappings should be returned
        assertThat(new HashSet<>(effectiveIds))
                .as("All ACTIVE roles with mappings should be included")
                .isEqualTo(activeRoleIds);

        // No duplicates
        assertThat(effectiveIds)
                .as("Result should not contain duplicates")
                .doesNotHaveDuplicates();
    }

    // ========== Helper classes ==========

    static class MappedRolesScenario {
        final List<Role> roles;
        final List<BiRbacMapping> allMappings;
        final Map<String, List<BiRbacMapping>> mappingsByRole;
        final List<BiSupersetRole> supersetRoles;

        MappedRolesScenario(List<Role> roles, List<BiRbacMapping> allMappings,
                           Map<String, List<BiRbacMapping>> mappingsByRole,
                           List<BiSupersetRole> supersetRoles) {
            this.roles = roles;
            this.allMappings = allMappings;
            this.mappingsByRole = mappingsByRole;
            this.supersetRoles = supersetRoles;
        }

        @Override
        public String toString() {
            return String.format("MappedRolesScenario{roles=%d, mappings=%d, supersetRoles=%d}",
                    roles.size(), allMappings.size(), supersetRoles.size());
        }
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
