package com.admin.bi.service;

import com.admin.bi.component.SupersetRoleSyncComponent;
import com.admin.bi.dto.response.RbacMappingResponse;
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
 * Bug Condition 探索测试 - listMappings 返回所有活跃角色而非仅已映射角色
 *
 * Bug Condition C(X): 存在活跃系统角色 R，使得 bi_rbac_mapping 表中不存在 sys_role_id = R.id 的记录，
 * 但 listMappings() 仍然返回了该角色。
 *
 * 此测试在未修复代码上运行时预期 **失败**，以证明 Bug 存在。
 *
 * Validates: Requirements 1.1, 1.4, 2.1, 2.4
 */
class BiRbacMappingListBugConditionPropertyTest {

    private BiRbacMappingRepository mappingRepository;
    private BiSupersetRoleRepository supersetRoleRepository;
    private RoleRepository roleRepository;
    private SupersetRoleSyncComponent syncComponent;
    private BiRbacMappingServiceImpl service;

    @BeforeTry
    void setUp() {
        mappingRepository = mock(BiRbacMappingRepository.class);
        supersetRoleRepository = mock(BiSupersetRoleRepository.class);
        roleRepository = mock(RoleRepository.class);
        syncComponent = mock(SupersetRoleSyncComponent.class);
        service = new BiRbacMappingServiceImpl(
                mappingRepository, supersetRoleRepository, roleRepository, syncComponent);
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<List<Role>> activeRolesWithMixedMapping() {
        // Generate 2-6 active system roles with unique IDs
        return Arbitraries.integers().between(2, 6).flatMap(count ->
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .list().ofSize(count)
                .filter(names -> names.stream().distinct().count() == count)
                .map(names -> {
                    List<Role> roles = new ArrayList<>();
                    for (int i = 0; i < names.size(); i++) {
                        roles.add(Role.builder()
                                .id("role-" + UUID.randomUUID().toString().substring(0, 8))
                                .name(names.get(i))
                                .code("CODE_" + names.get(i).toUpperCase())
                                .type("BU_UNBOUNDED")
                                .status("ACTIVE")
                                .build());
                    }
                    return roles;
                })
        );
    }

    // ========== Property 1: Bug Condition ==========

    /**
     * Property 1: Bug Condition - listMappings 返回所有活跃角色而非仅已映射角色
     *
     * 场景：存在 N 个活跃系统角色，但仅部分角色在 bi_rbac_mapping 表中有映射记录。
     * 期望行为：listMappings() 仅返回已映射的角色。
     * Bug 行为：listMappings() 返回所有活跃角色（包括未映射的）。
     *
     * 此测试在未修复代码上运行时预期失败，证明 Bug 存在。
     *
     * Validates: Requirements 1.1, 1.4, 2.1, 2.4
     */
    @Property(tries = 50)
    @Tag("Feature: rbac-mapping-manual-creation, Property 1: Bug Condition")
    void listMappingsShouldOnlyReturnMappedRoles(
            @ForAll("activeRolesWithMixedMapping") List<Role> allActiveRoles
    ) {
        // Ensure we have at least 2 roles so we can have mapped and unmapped
        Assume.that(allActiveRoles.size() >= 2);

        // Deterministically split: first role is mapped, rest are unmapped
        // This guarantees the bug condition: unmapped roles exist
        List<Role> mappedRoles = allActiveRoles.subList(0, 1);
        List<Role> unmappedRoles = allActiveRoles.subList(1, allActiveRoles.size());

        // Create mapping records only for mapped roles
        BiSupersetRole supersetRole = BiSupersetRole.builder()
                .id(1)
                .supersetRoleId(100)
                .name("TestSupersetRole")
                .status(SupersetRoleStatus.ACTIVE)
                .lastSyncedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        List<BiRbacMapping> mappings = mappedRoles.stream()
                .map(role -> BiRbacMapping.builder()
                        .id(UUID.randomUUID().toString())
                        .sysRoleId(role.getId())
                        .supersetRoleId(supersetRole.getSupersetRoleId())
                        .createdAt(LocalDateTime.now())
                        .build())
                .collect(Collectors.toList());

        Set<String> mappedRoleIds = mappedRoles.stream()
                .map(Role::getId)
                .collect(Collectors.toSet());

        // Mock: roleRepository.findAllActive() returns ALL active roles
        when(roleRepository.findAllActive()).thenReturn(allActiveRoles);

        // Mock: mappingRepository.findAll() returns only mappings for mapped roles
        when(mappingRepository.findAll()).thenReturn(mappings);

        // Mock: supersetRoleRepository.findAll() returns the superset role
        when(supersetRoleRepository.findAll()).thenReturn(List.of(supersetRole));

        // Execute: call listMappings with no filters
        List<RbacMappingResponse> result = service.listMappings(null, null);

        // Assert: result should ONLY contain mapped roles
        Set<String> returnedRoleIds = result.stream()
                .map(RbacMappingResponse::getSysRoleId)
                .collect(Collectors.toSet());

        // Every returned role must have a mapping record
        assertThat(returnedRoleIds)
                .as("listMappings() should only return roles that have mapping records in bi_rbac_mapping. " +
                    "Found %d roles returned but only %d have mappings. Unmapped roles returned: %s",
                    returnedRoleIds.size(), mappedRoleIds.size(),
                    unmappedRoles.stream().map(r -> r.getName() + "(" + r.getId() + ")").collect(Collectors.joining(", ")))
                .isEqualTo(mappedRoleIds);
    }
}
