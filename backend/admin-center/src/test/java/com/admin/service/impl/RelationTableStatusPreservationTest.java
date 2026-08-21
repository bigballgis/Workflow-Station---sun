package com.admin.service.impl;

import com.admin.dto.request.UpdateRelationTableRequest;
import com.admin.dto.response.RelationTableResponse;
import com.admin.entity.RelationTableDefinition;
import com.admin.entity.RelationTableVersion;
import com.admin.repository.RelationTableDefinitionRepository;
import com.admin.repository.RelationFieldDefinitionRepository;
import com.admin.repository.RelationTableVersionRepository;
import com.admin.repository.FunctionUnitRepository;
import com.admin.service.RelationTableAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.enums.RelationTableStatus;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.mockito.Mockito;
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
 * Preservation 属性测试 — 非 Bug 条件行为保持不变
 *
 * 这些测试在未修复代码上运行时预期 PASS，确认基线行为已捕获。
 * 修复后这些测试仍应 PASS，确认无回归。
 *
 * 观察优先方法论：先在未修复代码上观察行为，然后编写测试捕获该行为。
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6
 */
class RelationTableStatusPreservationTest {

    // ==================== Helper Methods ====================

    private RelationTableDefinitionRepository mockTableRepo() {
        return Mockito.mock(RelationTableDefinitionRepository.class);
    }

    private RelationFieldDefinitionRepository mockFieldRepo() {
        return Mockito.mock(RelationFieldDefinitionRepository.class);
    }

    private RelationTableVersionRepository mockVersionRepo() {
        return Mockito.mock(RelationTableVersionRepository.class);
    }

    private RelationTableAuditService mockAuditService() {
        return Mockito.mock(RelationTableAuditService.class);
    }

    private FunctionUnitRepository mockFunctionUnitRepo() {
        return Mockito.mock(FunctionUnitRepository.class);
    }

    private JdbcTemplate mockJdbcTemplate() {
        return Mockito.mock(JdbcTemplate.class);
    }

    private ObjectMapper realObjectMapper() {
        return new ObjectMapper();
    }

    private com.admin.config.DatabaseSchemaResolver mockSchemaResolver() {
        com.admin.config.DatabaseSchemaResolver resolver = Mockito.mock(com.admin.config.DatabaseSchemaResolver.class);
        Mockito.when(resolver.getSchema()).thenReturn("public");
        return resolver;
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

    // ==================== Arbitrary Providers ====================

    @Provide
    Arbitrary<String> validTableNames() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(3)
                .ofMaxLength(20)
                .map(s -> "tbl_" + s);
    }

    @Provide
    Arbitrary<Long> tableIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }

    // ==================== Property Tests ====================

    /**
     * Property Test 1: 对于任意 DEPLOYED 且 enabled=true 的表，
     * getDeployedTables() 始终包含该表。
     *
     * 观察: 在未修复代码上，getDeployedTables() 调用 findByStatus(DEPLOYED)，
     * 返回所有 DEPLOYED 状态的表（包括 enabled=true 的）。
     * 因此 DEPLOYED+enabled=true 的表一定在结果中。
     *
     * Validates: Requirements 3.2, 3.4
     */
    @Property(tries = 50)
    @Label("Preservation: DEPLOYED+enabled=true 的表始终出现在 getDeployedTables() 结果中")
    void deployedEnabledTable_alwaysInGetDeployedTables(
            @ForAll("tableIds") Long tableId,
            @ForAll("validTableNames") String tableName) {

        RelationTableDefinitionRepository tableRepo = mockTableRepo();
        RelationTableVersionRepository versionRepo = mockVersionRepo();
        RelationTableAuditService auditService = mockAuditService();
        JdbcTemplate jdbcTemplate = mockJdbcTemplate();
        ObjectMapper objectMapper = realObjectMapper();

        RelationTableDataServiceImpl dataService = new RelationTableDataServiceImpl(
                tableRepo, versionRepo, mockFunctionUnitRepo(), auditService,
                org.mockito.Mockito.mock(com.admin.service.RelationTableAccessService.class),
                org.mockito.Mockito.mock(com.admin.service.RelationTablePrimaryKeyAllocationService.class),
                jdbcTemplate, objectMapper);

        // Build a DEPLOYED + enabled=true table
        RelationTableDefinition deployedEnabledTable = buildTableDefinition(
                tableId, tableName, RelationTableStatus.DEPLOYED, true);

        // Production queries DEPLOYED / UPDATED / ROLLBACK enabled tables
        when(tableRepo.findByStatusInAndEnabledTrue(
                List.of(RelationTableStatus.DEPLOYED, RelationTableStatus.UPDATED, RelationTableStatus.ROLLBACK)))
                .thenReturn(List.of(deployedEnabledTable));

        List<RelationTableResponse> result = dataService.getDeployedTables();

        // Preservation: DEPLOYED+enabled=true table is always included
        assertThat(result)
                .as("DEPLOYED+enabled=true 的表应始终出现在 getDeployedTables() 结果中")
                .isNotEmpty()
                .anyMatch(r -> r.getId().equals(tableId) && r.getTableName().equals(tableName));
    }

    /**
     * Property Test 2: 对于任意 DEPLOYED 状态的表，
     * getDeployedTableDefinition() 正常返回（不抛异常）。
     *
     * 观察: 在未修复代码上，getDeployedTableDefinition() 检查 status == DEPLOYED，
     * 对于 DEPLOYED 状态的表不会抛异常。数据查询操作（queryData 等）
     * 内部调用 getDeployedTableDefinition()，对 DEPLOYED 表正常工作。
     *
     * 注意: 我们通过 queryData 间接测试 getDeployedTableDefinition()，
     * 因为它是 private 方法。queryData 会先调用 getDeployedTableDefinition()
     * 再调用 getDeployedFields()，我们只需验证第一步不抛异常。
     * 为此我们 mock versionRepository 返回一个有效的版本快照。
     *
     * Validates: Requirements 3.2, 3.5
     */
    @Property(tries = 50)
    @Label("Preservation: DEPLOYED 状态的表调用 getDeployedTableDefinition() 正常返回")
    void deployedTable_getDeployedTableDefinition_returnsNormally(
            @ForAll("tableIds") Long tableId,
            @ForAll("validTableNames") String tableName) {

        RelationTableDefinitionRepository tableRepo = mockTableRepo();
        RelationTableVersionRepository versionRepo = mockVersionRepo();
        RelationTableAuditService auditService = mockAuditService();
        JdbcTemplate jdbcTemplate = mockJdbcTemplate();
        ObjectMapper objectMapper = realObjectMapper();

        RelationTableDataServiceImpl dataService = new RelationTableDataServiceImpl(
                tableRepo, versionRepo, mockFunctionUnitRepo(), auditService,
                org.mockito.Mockito.mock(com.admin.service.RelationTableAccessService.class),
                org.mockito.Mockito.mock(com.admin.service.RelationTablePrimaryKeyAllocationService.class),
                jdbcTemplate, objectMapper);

        // Build a DEPLOYED table
        RelationTableDefinition deployedTable = buildTableDefinition(
                tableId, tableName, RelationTableStatus.DEPLOYED, true);

        when(tableRepo.findById(tableId)).thenReturn(Optional.of(deployedTable));

        // Provide a valid version snapshot so getDeployedFields() doesn't fail
        String snapshotJson = "[{\"fieldName\":\"id_col\",\"dataType\":\"BIGINT\",\"nullable\":false,\"isPrimaryKey\":true,\"sortOrder\":0}]";
        RelationTableVersion version = RelationTableVersion.builder()
                .id(1L)
                .tableDefinition(deployedTable)
                .versionNumber(1)
                .snapshotData(snapshotJson)
                .deployedBy("test-user")
                .deployedAt(Instant.now())
                .build();
        when(versionRepo.findLatestVersion(tableId)).thenReturn(Optional.of(version));

        // Mock the JDBC count query to return 0 rows (empty table is fine)
        when(jdbcTemplate.queryForObject(contains("COUNT"), eq(Long.class), any()))
                .thenReturn(0L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of());

        // Preservation: DEPLOYED table data query should not throw
        assertThatCode(() -> {
            dataService.queryData(tableId, null,
                    org.springframework.data.domain.PageRequest.of(0, 10));
        })
                .as("DEPLOYED 状态的表调用数据查询不应抛异常")
                .doesNotThrowAnyException();
    }

    /**
     * Property Test 3: 对于任意非 DEPLOYED 状态的表调用 updateTable()，
     * 状态保持为 DRAFT（不应变为 DEPLOYED 或 ROLLBACK）。
     *
     * 观察: 在未修复代码上，updateTable() 无条件将状态设为 DRAFT。
     * 对于 DRAFT 状态（对应未来的 INIT）的表，编辑后状态仍为 DRAFT。
     * 这是 preservation 行为：非 DEPLOYED 表编辑后不会意外变成 DEPLOYED 或 ROLLBACK。
     *
     * 注意: 未修复代码中没有 INIT 枚举，DRAFT 是新表的初始状态。
     * 此测试验证：对 DRAFT 状态的表调用 updateTable()，
     * 状态不会变为 DEPLOYED 或 ROLLBACK（保持为 DRAFT）。
     *
     * Validates: Requirements 3.6
     */
    @Property(tries = 50)
    @Label("Preservation: 非 DEPLOYED 状态的表调用 updateTable() 后状态不会变为 DEPLOYED 或 ROLLBACK")
    void nonDeployedTable_updateTable_statusRemainsNonDeployed(
            @ForAll("tableIds") Long tableId,
            @ForAll("validTableNames") String tableName) {

        RelationTableDefinitionRepository tableRepo = mockTableRepo();
        RelationFieldDefinitionRepository fieldRepo = mockFieldRepo();
        JdbcTemplate jdbcTemplate = mockJdbcTemplate();

        RelationTableStructureServiceImpl structureService = new RelationTableStructureServiceImpl(
                tableRepo, fieldRepo, mockFunctionUnitRepo(),
                new com.admin.service.RelationComputedFieldValidator(), jdbcTemplate);

        // Build a DRAFT table (equivalent to INIT in unfixed code)
        RelationTableDefinition draftTable = buildTableDefinition(
                tableId, tableName, RelationTableStatus.DRAFT, true);

        when(tableRepo.findById(tableId)).thenReturn(Optional.of(draftTable));
        when(tableRepo.save(any(RelationTableDefinition.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UpdateRelationTableRequest request = UpdateRelationTableRequest.builder()
                .displayName("Updated Display Name")
                .build();

        RelationTableResponse result = structureService.updateTable(tableId, request);

        // Preservation: status should NOT become DEPLOYED or ROLLBACK
        assertThat(result.getStatus())
                .as("非 DEPLOYED 状态的表编辑后不应变为 DEPLOYED")
                .isNotEqualTo(RelationTableStatus.DEPLOYED);
        assertThat(result.getStatus())
                .as("非 DEPLOYED 状态的表编辑后不应变为 ROLLBACK")
                .isNotEqualTo(RelationTableStatus.ROLLBACK);
    }
}
