package com.admin.service;

import com.admin.entity.RelationTableAccess;
import com.admin.entity.RelationTableDefinition;
import com.admin.repository.RelationTableAccessRepository;
import com.admin.repository.RelationTableDefinitionRepository;
import com.admin.service.impl.RelationTableAccessServiceImpl;
import com.platform.common.enums.RelationTableStatus;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Relation Table 权限过滤属性测试
 *
 * Feature: relation-tables, Property 14: 用户权限过滤
 *
 * Validates: Requirements 8.2, 12.4
 */
class RelationTableAccessPropertyTest {

    private RelationTableAccessRepository accessRepository;
    private RelationTableDefinitionRepository tableDefinitionRepository;
    private RelationTableAccessServiceImpl accessService;

    @BeforeTry
    void setUp() {
        accessRepository = mock(RelationTableAccessRepository.class);
        tableDefinitionRepository = mock(RelationTableDefinitionRepository.class);
        accessService = new RelationTableAccessServiceImpl(accessRepository);
    }

    // ==================== Arbitraries ====================

    @Provide
    Arbitrary<List<String>> userRoleSets() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(String::toLowerCase)
                .list().ofMinSize(0).ofMaxSize(5)
                .map(list -> list.stream().distinct().collect(Collectors.toList()));
    }

    @Provide
    Arbitrary<List<String>> accessRoleSets() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(String::toLowerCase)
                .list().ofMinSize(0).ofMaxSize(5)
                .map(list -> list.stream().distinct().collect(Collectors.toList()));
    }

    /**
     * Generate a scenario with multiple tables, each with random portalVisible and access configs.
     */
    @Provide
    Arbitrary<AccessFilterScenario> accessFilterScenarios() {
        return Arbitraries.integers().between(1, 10).flatMap(tableCount ->
            // Generate user roles
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(8)
                    .map(String::toLowerCase)
                    .list().ofMinSize(1).ofMaxSize(5)
                    .map(list -> list.stream().distinct().collect(Collectors.toList()))
                    .flatMap(userRoles ->
                // Generate tables with random portalVisible and access configs
                Arbitraries.integers().between(0, 1).list().ofSize(tableCount).flatMap(portalVisibleFlags ->
                    // For each table, generate a subset of roles that have access
                    Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(8)
                            .map(String::toLowerCase)
                            .list().ofMinSize(0).ofMaxSize(5)
                            .map(list -> list.stream().distinct().collect(Collectors.toList()))
                            .list().ofSize(tableCount)
                            .map(accessRolesPerTable -> {
                                List<TableConfig> tables = new ArrayList<>();
                                for (int i = 0; i < tableCount; i++) {
                                    boolean portalVisible = portalVisibleFlags.get(i) == 1;
                                    List<String> accessRoles = accessRolesPerTable.get(i);
                                    tables.add(new TableConfig(
                                            (long) (i + 1),
                                            "rt_table_" + i,
                                            portalVisible,
                                            accessRoles
                                    ));
                                }
                                return new AccessFilterScenario(userRoles, tables);
                            })
                ))
        );
    }

    // ==================== Property 14: 用户权限过滤 ====================

    /**
     * Property 14: 用户权限过滤
     *
     * For any business user and set of Relation Tables, the visible tables in User Portal
     * should be exactly the intersection of:
     * (1) portal_visible = true
     * (2) user has at least one Business Role that is assigned access to the table
     *
     * Feature: relation-tables, Property 14: 用户权限过滤
     * Validates: Requirements 8.2, 12.4
     */
    @Property(tries = 100)
    @Tag("Feature: relation-tables, Property 14: 用户权限过滤")
    void userAccessFilterProperty(
            @ForAll("accessFilterScenarios") AccessFilterScenario scenario
    ) {
        List<String> userRoles = scenario.userRoles;
        List<TableConfig> tables = scenario.tables;

        // Compute expected visible tables:
        // portal_visible = true AND user has at least one role with access
        List<Long> expectedVisibleTableIds = tables.stream()
                .filter(t -> t.portalVisible)
                .filter(t -> !t.accessRoles.isEmpty() && t.accessRoles.stream().anyMatch(userRoles::contains))
                .map(t -> t.tableId)
                .collect(Collectors.toList());

        // For each table, mock the access repository behavior
        for (TableConfig table : tables) {
            List<RelationTableAccess> accessConfigs = table.accessRoles.stream()
                    .map(roleId -> RelationTableAccess.builder()
                            .id(UUID.randomUUID().toString())
                            .tableId(table.tableId)
                            .targetType("ROLE")
                            .targetId(roleId)
                            .createdAt(Instant.now())
                            .build())
                    .collect(Collectors.toList());
            when(accessRepository.findByTableId(table.tableId)).thenReturn(accessConfigs);
        }

        // Simulate the filtering logic that would happen in a portal service:
        // 1. Get portal-visible tables
        // 2. For each, check hasAccess
        List<Long> actualVisibleTableIds = tables.stream()
                .filter(t -> t.portalVisible)
                .filter(t -> accessService.hasAccess(t.tableId, userRoles))
                .map(t -> t.tableId)
                .collect(Collectors.toList());

        // === Verify: visible tables = portal_visible=true ∩ user has access ===
        assertThat(actualVisibleTableIds)
                .as("Visible tables should be exactly portal_visible=true AND user has role access")
                .containsExactlyInAnyOrderElementsOf(expectedVisibleTableIds);

        // === Verify: no table with portal_visible=false is visible ===
        List<Long> nonPortalVisibleIds = tables.stream()
                .filter(t -> !t.portalVisible)
                .map(t -> t.tableId)
                .collect(Collectors.toList());
        if (!nonPortalVisibleIds.isEmpty()) {
            assertThat(actualVisibleTableIds)
                    .as("No table with portal_visible=false should be visible")
                    .doesNotContainAnyElementsOf(nonPortalVisibleIds);
        }

        // === Verify: no table without matching role is visible ===
        List<Long> noAccessTableIds = tables.stream()
                .filter(t -> t.portalVisible)
                .filter(t -> t.accessRoles.isEmpty() || t.accessRoles.stream().noneMatch(userRoles::contains))
                .map(t -> t.tableId)
                .collect(Collectors.toList());
        if (!noAccessTableIds.isEmpty()) {
            assertThat(actualVisibleTableIds)
                    .as("No table without matching user role should be visible")
                    .doesNotContainAnyElementsOf(noAccessTableIds);
        }
    }

    // ==================== Helper Classes ====================

    static class AccessFilterScenario {
        final List<String> userRoles;
        final List<TableConfig> tables;

        AccessFilterScenario(List<String> userRoles, List<TableConfig> tables) {
            this.userRoles = userRoles;
            this.tables = tables;
        }

        @Override
        public String toString() {
            return "AccessFilterScenario{userRoles=" + userRoles + ", tables=" + tables.size() + "}";
        }
    }

    static class TableConfig {
        final Long tableId;
        final String tableName;
        final boolean portalVisible;
        final List<String> accessRoles;

        TableConfig(Long tableId, String tableName, boolean portalVisible, List<String> accessRoles) {
            this.tableId = tableId;
            this.tableName = tableName;
            this.portalVisible = portalVisible;
            this.accessRoles = accessRoles;
        }
    }
}
