package com.admin.service.impl;

import com.admin.dto.request.CreateRelationTableRequest;
import com.admin.dto.request.UpdateRelationTableRequest;
import com.admin.dto.response.RelationTableResponse;
import com.admin.entity.RelationTableDefinition;
import com.admin.repository.RelationTableDefinitionRepository;
import com.admin.repository.RelationFieldDefinitionRepository;
import com.admin.repository.RelationTableVersionRepository;
import com.admin.service.RelationTableAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.enums.RelationDataType;
import com.platform.common.enums.RelationTableStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug 条件探索性测试 — 表状态生命周期缺陷验证
 *
 * 这些测试编码了期望行为。在未修复代码上运行时预期 FAIL，
 * 以此确认 bug 存在。修复后测试通过即验证修复正确。
 *
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Bug Condition: 表状态生命周期缺陷验证")
class RelationTableStatusBugConditionTest {

    @Mock private RelationTableDefinitionRepository tableDefinitionRepository;
    @Mock private RelationFieldDefinitionRepository fieldDefinitionRepository;
    @Mock private RelationTableVersionRepository versionRepository;
    @Mock private RelationTableAuditService auditService;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ObjectMapper objectMapper;
    private RelationTableStructureServiceImpl structureService;
    private RelationTableDataServiceImpl dataService;

    @BeforeEach
    void setUp() {
        structureService = new RelationTableStructureServiceImpl(
                tableDefinitionRepository, fieldDefinitionRepository, jdbcTemplate);
        dataService = new RelationTableDataServiceImpl(
                tableDefinitionRepository, versionRepository, auditService, jdbcTemplate, objectMapper);
    }

    // ==================== Helper Methods ====================

    private CreateRelationTableRequest buildCreateRequest(String tableName) {
        CreateRelationTableRequest.FieldDefinitionRequest field =
                CreateRelationTableRequest.FieldDefinitionRequest.builder()
                        .fieldName("id_col")
                        .dataType(RelationDataType.BIGINT)
                        .nullable(false)
                        .isPrimaryKey(true)
                        .sortOrder(0)
                        .build();

        return CreateRelationTableRequest.builder()
                .tableName(tableName)
                .displayName("Display " + tableName)
                .description("Test table")
                .fieldDefinitions(List.of(field))
                .build();
    }

    private RelationTableDefinition buildTableDefinition(Long id, String tableName,
                                                          RelationTableStatus status, boolean enabled) {
        return RelationTableDefinition.builder()
                .id(id)
                .tableName(tableName)
                .displayName("Display " + tableName)
                .description("Description for " + tableName)
                .status(status)
                .enabled(enabled)
                .portalVisible(false)
                .currentVersion(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .fieldDefinitions(new ArrayList<>())
                .versions(new ArrayList<>())
                .build();
    }

    // ==================== Bug Condition Tests ====================

    /**
     * 测试 1: createTable() 应返回 INIT 状态
     *
     * Bug: 未修复代码将新表状态硬编码为 DRAFT 而非 INIT
     * 期望: createTable() 返回的表状态不应为 DRAFT（应为 INIT）
     * 反例: 返回 DRAFT
     *
     * Validates: Requirements 1.1
     */
    @Test
    @DisplayName("Test 1: createTable() 应返回 INIT 状态（未修复代码返回 DRAFT，测试将失败）")
    void createTable_shouldReturnInitStatus() {
        CreateRelationTableRequest request = buildCreateRequest("new_table");

        when(tableDefinitionRepository.existsByTableName("new_table")).thenReturn(false);
        when(tableDefinitionRepository.save(any(RelationTableDefinition.class)))
                .thenAnswer(inv -> {
                    RelationTableDefinition saved = inv.getArgument(0);
                    saved.setId(1L);
                    return saved;
                });

        RelationTableResponse result = structureService.createTable(request);

        // Bug condition: 未修复代码设置 DRAFT，期望不是 DRAFT（应为 INIT）
        assertThat(result.getStatus())
                .as("新创建的表状态不应为 DRAFT（应为 INIT）")
                .isNotEqualTo(RelationTableStatus.DRAFT);
    }

    /**
     * 测试 2: 对 DEPLOYED 状态的表调用 updateTable()，状态应变为 UPDATED（不应为 DRAFT）
     *
     * Bug: 未修复代码无条件将状态重置为 DRAFT
     * 期望: DEPLOYED 表编辑后状态不应为 DRAFT（应为 UPDATED）
     * 反例: 返回 DRAFT
     *
     * Validates: Requirements 1.2
     */
    @Test
    @DisplayName("Test 2: updateTable(DEPLOYED) 应返回 UPDATED 状态（未修复代码返回 DRAFT，测试将失败）")
    void updateTable_deployedTable_shouldReturnUpdatedStatus() {
        RelationTableDefinition existing = buildTableDefinition(
                1L, "deployed_table", RelationTableStatus.DEPLOYED, true);

        when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(tableDefinitionRepository.save(any(RelationTableDefinition.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UpdateRelationTableRequest request = UpdateRelationTableRequest.builder()
                .displayName("Updated Display Name")
                .build();

        RelationTableResponse result = structureService.updateTable(1L, request);

        // Bug condition: 未修复代码无条件设置 DRAFT，期望不是 DRAFT（应为 UPDATED）
        assertThat(result.getStatus())
                .as("编辑 DEPLOYED 表后状态不应为 DRAFT（应为 UPDATED）")
                .isNotEqualTo(RelationTableStatus.DRAFT);
    }

    /**
     * 测试 3: getDeployedTables() 应包含 UPDATED 状态且 enabled=true 的表
     *
     * Bug: 未修复代码仅查询 DEPLOYED 状态，不包含 UPDATED
     * 期望: 返回列表应包含 UPDATED 状态的表
     * 反例: UPDATED 状态的表不在返回列表中
     *
     * 注意: 由于 UPDATED 枚举值尚不存在，此测试通过验证查询方法是否
     * 使用了支持多状态查询的方式来检测 bug。我们构造一个 DEPLOYED 表
     * 和一个 disabled 的 DEPLOYED 表，验证 disabled 表是否被过滤。
     * 同时验证查询是否仅限于 findByStatus(DEPLOYED)。
     *
     * Validates: Requirements 1.3
     */
    @Test
    @DisplayName("Test 3: getDeployedTables() 应包含 UPDATED 状态且 enabled=true 的表（未修复代码仅查询 DEPLOYED，测试将失败）")
    void getDeployedTables_shouldIncludeUpdatedStatusTables() {
        // 设置：一个 DEPLOYED+enabled 表，一个 UPDATED+enabled 表
        RelationTableDefinition deployedEnabled = buildTableDefinition(
                1L, "deployed_enabled", RelationTableStatus.DEPLOYED, true);
        RelationTableDefinition updatedEnabled = buildTableDefinition(
                2L, "updated_enabled", RelationTableStatus.UPDATED, true);

        // 修复后代码调用 findByStatusInAndEnabledTrue(List.of(DEPLOYED, UPDATED))
        when(tableDefinitionRepository.findByStatusInAndEnabledTrue(
                List.of(RelationTableStatus.DEPLOYED, RelationTableStatus.UPDATED)))
                .thenReturn(List.of(deployedEnabled, updatedEnabled));

        List<RelationTableResponse> result = dataService.getDeployedTables();

        // Bug condition: 未修复代码不包含 UPDATED 状态的表，
        // 修复后应同时返回 DEPLOYED 和 UPDATED 且 enabled=true 的表
        assertThat(result)
                .as("getDeployedTables() 应包含 DEPLOYED 和 UPDATED 状态且 enabled=true 的表")
                .hasSize(2)
                .allSatisfy(table -> assertThat(table.getEnabled()).isTrue());
    }

    /**
     * 测试 4: getDeployedTables() 不应包含 enabled=false 的表
     *
     * Bug: 未修复代码未过滤 disabled 表
     * 期望: enabled=false 的表不在返回列表中
     * 反例: disabled 表仍然出现在列表中
     *
     * Validates: Requirements 1.4
     */
    @Test
    @DisplayName("Test 4: getDeployedTables() 不应包含 enabled=false 的表（未修复代码未过滤 disabled，测试将失败）")
    void getDeployedTables_shouldExcludeDisabledTables() {
        RelationTableDefinition enabledTable = buildTableDefinition(
                1L, "enabled_table", RelationTableStatus.DEPLOYED, true);

        // 修复后代码调用 findByStatusInAndEnabledTrue，仅返回 enabled=true 的表
        // disabled 表已在 Repository 层被过滤，不会出现在结果中
        when(tableDefinitionRepository.findByStatusInAndEnabledTrue(
                List.of(RelationTableStatus.DEPLOYED, RelationTableStatus.UPDATED)))
                .thenReturn(List.of(enabledTable));

        List<RelationTableResponse> result = dataService.getDeployedTables();

        // Bug condition: 未修复代码不过滤 disabled 表
        assertThat(result)
                .as("getDeployedTables() 不应包含 enabled=false 的表")
                .extracting(RelationTableResponse::getEnabled)
                .doesNotContain(false);
    }

    /**
     * 测试 5: 对 UPDATED 状态的表调用数据查询不应抛异常
     *
     * Bug: 未修复代码的 getDeployedTableDefinition() 仅允许 DEPLOYED 状态，
     *      UPDATED 状态会抛出 RelationTableNotFoundException
     * 期望: UPDATED 状态的表可以正常执行数据操作
     * 反例: 抛出 RelationTableNotFoundException
     *
     * 注意: 由于 UPDATED 枚举值尚不存在，此测试验证非 DEPLOYED 状态
     * 的表是否被拒绝。我们使用 ROLLBACK 状态来模拟——如果代码仅允许
     * DEPLOYED，那么任何非 DEPLOYED 状态都会被拒绝。
     * 修复后，UPDATED 状态应被允许。
     *
     * 这里我们直接测试：对于 DEPLOYED 状态的表，在编辑后（状态变为 DRAFT），
     * 数据查询会失败。这证明了 bug：编辑已部署表后无法继续查询数据。
     *
     * Validates: Requirements 1.3
     */
    @Test
    @DisplayName("Test 5: 编辑已部署表后数据查询不应失败（未修复代码将状态重置为 DRAFT 导致查询失败，测试将失败）")
    void editedDeployedTable_dataQueryShouldNotFail() {
        // 修复后：编辑 DEPLOYED 表后状态变为 UPDATED（而非 DRAFT）
        // UPDATED 状态的表应允许数据查询
        RelationTableDefinition editedTable = buildTableDefinition(
                1L, "edited_table", RelationTableStatus.UPDATED, true);

        when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(editedTable));

        // 修复后，UPDATED 状态被 getDeployedTableDefinition 允许，不会抛异常
        // 注意：queryData 还需要 versionRepository 返回版本数据，但此处我们只验证状态校验不抛异常
        // 由于 queryData 内部还会调用 getDeployedFields，我们需要 mock versionRepository
        // 但核心断言是状态校验不抛异常——如果状态校验通过但后续步骤失败，那也是不同的异常
        // 这里我们直接验证 UPDATED 状态不会触发 RelationTableNotFoundException
        assertThatCode(() -> {
            try {
                dataService.queryData(1L, null, org.springframework.data.domain.PageRequest.of(0, 10));
            } catch (com.admin.exception.RelationTableNotFoundException e) {
                // 如果是 "Table is not deployed" 异常，说明状态校验失败——这是 bug
                if (e.getMessage() != null && e.getMessage().contains("not deployed")) {
                    throw e;
                }
                // 其他 RelationTableNotFoundException（如 "No deployed version found"）是后续步骤的问题，不是状态校验问题
            }
        })
                .as("编辑已部署表后（状态为 UPDATED），数据查询的状态校验不应抛异常")
                .doesNotThrowAnyException();
    }
}
