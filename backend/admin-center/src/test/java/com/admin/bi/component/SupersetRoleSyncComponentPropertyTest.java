package com.admin.bi.component;

import com.admin.bi.config.BiProperties;
import com.admin.bi.dto.response.SyncResultResponse;
import com.admin.bi.entity.BiSupersetRole;
import com.admin.bi.enums.SupersetRoleStatus;
import com.admin.bi.repository.BiSupersetRoleRepository;
import com.admin.exception.SupersetSyncException;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Superset 角色同步属性测试
 *
 * Feature: bi-management, Property 13: Superset role sync correctness
 * Feature: bi-management, Property 14: Superset role sync error recovery
 *
 * 使用 jqwik 验证各种 ab_role 数据与本地 bi_superset_role 状态组合下的同步结果。
 *
 * Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5, 7.6
 */
class SupersetRoleSyncComponentPropertyTest {

    private JdbcTemplate jdbcTemplate;
    private BiSupersetRoleRepository roleRepository;
    private BiProperties biProperties;
    private SupersetRoleSyncComponent syncComponent;

    /** Tracks all entities saved during sync */
    private List<BiSupersetRole> savedEntities;

    @BeforeTry
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        roleRepository = mock(BiSupersetRoleRepository.class);
        biProperties = new BiProperties();
        syncComponent = new SupersetRoleSyncComponent(jdbcTemplate, roleRepository, biProperties);
        savedEntities = new ArrayList<>();

        // Capture all save calls
        when(roleRepository.save(any(BiSupersetRole.class))).thenAnswer(invocation -> {
            BiSupersetRole entity = invocation.getArgument(0);
            savedEntities.add(deepCopy(entity));
            return entity;
        });
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<List<Map<String, Object>>> supersetRoleData() {
        return supersetRoleRow().list().ofMinSize(0).ofMaxSize(8)
                .filter(list -> {
                    // Ensure unique id values
                    Set<Integer> ids = list.stream()
                            .map(m -> (Integer) m.get("id"))
                            .collect(Collectors.toSet());
                    return ids.size() == list.size();
                });
    }

    private Arbitrary<Map<String, Object>> supersetRoleRow() {
        return Combinators.combine(
                Arbitraries.integers().between(1, 500),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30)
        ).as((id, name) -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", id);
            row.put("name", name);
            return row;
        });
    }

    @Provide
    Arbitrary<List<BiSupersetRole>> existingRoleRecords() {
        return roleRecord().list().ofMinSize(0).ofMaxSize(8)
                .filter(list -> {
                    Set<Integer> ids = list.stream()
                            .map(BiSupersetRole::getSupersetRoleId)
                            .collect(Collectors.toSet());
                    return ids.size() == list.size();
                });
    }

    private Arbitrary<BiSupersetRole> roleRecord() {
        return Combinators.combine(
                Arbitraries.integers().between(1, 500),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30),
                Arbitraries.of(SupersetRoleStatus.ACTIVE, SupersetRoleStatus.INACTIVE)
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

    // ========== Property 13: Superset 角色同步正确性 ==========

    /**
     * Property 13: Superset 角色同步正确性
     *
     * For any combination of ab_role data and existing local bi_superset_role records,
     * after executing sync:
     * 1. New roles (in ab_role but not in local) are created as ACTIVE
     * 2. Existing roles with changed name get updated
     * 3. Local roles not in ab_role are marked INACTIVE
     * 4. INACTIVE roles that reappear in ab_role are restored to ACTIVE
     *
     * Feature: bi-management, Property 13: Superset role sync correctness
     * Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5
     */
    @Property(tries = 100)
    @Tag("Feature: bi-management, Property 13: Superset role sync correctness")
    void supersetRoleSyncCorrectness(
            @ForAll("supersetRoleData") List<Map<String, Object>> supersetData,
            @ForAll("existingRoleRecords") List<BiSupersetRole> existingRecords
    ) {
        // Snapshot existing records before sync (component mutates in-place)
        List<BiSupersetRole> snapshotBefore = existingRecords.stream()
                .map(this::deepCopy)
                .collect(Collectors.toList());

        // Build lookup maps from snapshot
        Map<Integer, BiSupersetRole> existingBySuperId = snapshotBefore.stream()
                .collect(Collectors.toMap(BiSupersetRole::getSupersetRoleId, r -> r));
        Set<Integer> supersetIds = supersetData.stream()
                .map(m -> (Integer) m.get("id"))
                .collect(Collectors.toSet());

        // Setup mocks
        when(jdbcTemplate.queryForList(anyString())).thenReturn(supersetData);
        when(roleRepository.findAll()).thenReturn(new ArrayList<>(existingRecords));

        // Execute sync
        savedEntities.clear();
        SyncResultResponse result = syncComponent.executeSyncOperation();

        // Build saved entities map by supersetRoleId (last save wins)
        Map<Integer, BiSupersetRole> savedBySuperId = new LinkedHashMap<>();
        for (BiSupersetRole saved : savedEntities) {
            savedBySuperId.put(saved.getSupersetRoleId(), saved);
        }

        // === Verify each Superset role ===
        for (Map<String, Object> row : supersetData) {
            Integer superId = (Integer) row.get("id");
            String expectedName = (String) row.get("name");

            BiSupersetRole saved = savedBySuperId.get(superId);
            assertThat(saved)
                    .as("Superset role %d should have been saved", superId)
                    .isNotNull();

            BiSupersetRole prior = existingBySuperId.get(superId);

            if (prior == null) {
                // Requirement 7.2: New role created as ACTIVE
                assertThat(saved.getStatus()).isEqualTo(SupersetRoleStatus.ACTIVE);
                assertThat(saved.getName()).isEqualTo(expectedName);
                assertThat(saved.getSupersetRoleId()).isEqualTo(superId);
                assertThat(saved.getLastSyncedAt()).isNotNull();
            } else {
                // Requirement 7.3: name field updated
                assertThat(saved.getName()).isEqualTo(expectedName);

                if (prior.getStatus() == SupersetRoleStatus.INACTIVE) {
                    // Requirement 7.5: INACTIVE restored to ACTIVE
                    assertThat(saved.getStatus()).isEqualTo(SupersetRoleStatus.ACTIVE);
                } else {
                    // ACTIVE stays ACTIVE
                    assertThat(saved.getStatus()).isEqualTo(SupersetRoleStatus.ACTIVE);
                }
            }
        }

        // === Verify roles no longer in Superset ===
        for (Map.Entry<Integer, BiSupersetRole> entry : existingBySuperId.entrySet()) {
            Integer superId = entry.getKey();
            BiSupersetRole prior = entry.getValue();

            if (!supersetIds.contains(superId)) {
                if (prior.getStatus() == SupersetRoleStatus.INACTIVE) {
                    // Already INACTIVE, should stay INACTIVE (component skips)
                } else {
                    // Requirement 7.4: ACTIVE -> INACTIVE
                    BiSupersetRole saved = savedBySuperId.get(superId);
                    assertThat(saved)
                            .as("Role %d (was ACTIVE) should be saved as INACTIVE", superId)
                            .isNotNull();
                    assertThat(saved.getStatus()).isEqualTo(SupersetRoleStatus.INACTIVE);
                }
            }
        }

        // === Verify summary counts ===
        int expectedCreated = 0;
        int expectedUpdated = 0;
        int expectedInactivated = 0;

        for (Map<String, Object> row : supersetData) {
            Integer superId = (Integer) row.get("id");
            String newName = (String) row.get("name");
            BiSupersetRole prior = existingBySuperId.get(superId);
            if (prior == null) {
                expectedCreated++;
            } else {
                boolean nameChanged = !Objects.equals(prior.getName(), newName);
                if (prior.getStatus() == SupersetRoleStatus.INACTIVE) {
                    expectedUpdated++;
                } else if (nameChanged) {
                    expectedUpdated++;
                }
            }
        }

        for (BiSupersetRole prior : snapshotBefore) {
            if (!supersetIds.contains(prior.getSupersetRoleId())) {
                if (prior.getStatus() != SupersetRoleStatus.INACTIVE) {
                    expectedInactivated++;
                }
            }
        }

        assertThat(result.getCreated()).isEqualTo(expectedCreated);
        assertThat(result.getUpdated()).isEqualTo(expectedUpdated);
        assertThat(result.getAutoInactivated()).isEqualTo(expectedInactivated);
    }

    // ========== Property 14: Superset 角色同步错误恢复 ==========

    /**
     * Property 14: Superset 角色同步错误恢复
     *
     * For any existing bi_superset_role state, if the Superset_Role_Sync_Operation
     * encounters a database exception during JdbcTemplate query, then no save calls
     * should be made (the exception occurs before any processing), ensuring all
     * records remain unchanged.
     *
     * Feature: bi-management, Property 14: Superset role sync error recovery
     * Validates: Requirements 7.6
     */
    @Property(tries = 100)
    @Tag("Feature: bi-management, Property 14: Superset role sync error recovery")
    void syncErrorRecovery(
            @ForAll("existingRoleRecords") List<BiSupersetRole> existingRecords
    ) {
        // Mock JdbcTemplate to throw RuntimeException (simulating DB connection failure)
        when(jdbcTemplate.queryForList(anyString()))
                .thenThrow(new RuntimeException("Simulated Superset database connection failure"));
        when(roleRepository.findAll()).thenReturn(new ArrayList<>(existingRecords));

        // Execute sync - expect SupersetSyncException
        savedEntities.clear();
        assertThatThrownBy(() -> syncComponent.executeSyncOperation())
                .isInstanceOf(SupersetSyncException.class);

        // Verify no save calls were made (exception happens before any processing)
        verify(roleRepository, never()).save(any(BiSupersetRole.class));
        assertThat(savedEntities).isEmpty();
    }

    // ========== Helpers ==========

    private BiSupersetRole deepCopy(BiSupersetRole source) {
        return BiSupersetRole.builder()
                .id(source.getId())
                .supersetRoleId(source.getSupersetRoleId())
                .name(source.getName())
                .status(source.getStatus())
                .lastSyncedAt(source.getLastSyncedAt())
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .build();
    }
}
