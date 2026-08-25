package com.admin.service;

import com.admin.component.RelationTableFunctionUnitResolver;
import com.admin.dto.response.RelationTableResponse;
import com.admin.entity.RelationFieldDefinition;
import com.admin.entity.RelationTableDefinition;
import com.admin.entity.RelationTableVersion;
import com.admin.exception.RelationTableNotFoundException;
import com.admin.repository.FunctionUnitRepository;
import com.admin.repository.RelationTableDefinitionRepository;
import com.admin.repository.RelationTableFunctionUnitRepository;
import com.admin.repository.RelationTableVersionRepository;
import com.admin.service.impl.RelationTableDataServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.RelationFieldDTO;
import com.platform.common.dto.RelationTableDataRowDTO;
import com.platform.common.enums.RelationDataType;
import com.platform.common.enums.RelationTableStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RelationTableDataServiceImpl 单元测试
 * 测试仅展示已部署表、数据 CRUD、分页、搜索过滤
 * 需求: 6.1-6.5
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RelationTableDataServiceImpl Tests")
class RelationTableDataServiceTest {

    @Mock
    private RelationTableDefinitionRepository tableDefinitionRepository;

    @Mock
    private RelationTableVersionRepository versionRepository;

    @Mock
    private FunctionUnitRepository functionUnitRepository;

    @Mock
    private RelationTableFunctionUnitRepository relationTableFunctionUnitRepository;

    @Mock
    private RelationTableAuditService auditService;

    @Mock
    private RelationTableAccessService accessService;

    @Mock
    private RelationTablePrimaryKeyAllocationService primaryKeyAllocationService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ObjectMapper objectMapper;

    // Real instance: a thin pass-through collaborator over the two repositories above, resolving
    // the Relation Table <-> Function Unit many-to-many link for display.
    private RelationTableFunctionUnitResolver relationTableFunctionUnitResolver;

    private RelationTableDataServiceImpl service;

    @BeforeEach
    void wireService() {
        relationTableFunctionUnitResolver =
                new RelationTableFunctionUnitResolver(relationTableFunctionUnitRepository, functionUnitRepository);
        service = new RelationTableDataServiceImpl(
                tableDefinitionRepository, versionRepository, relationTableFunctionUnitResolver, auditService,
                accessService, primaryKeyAllocationService, jdbcTemplate, objectMapper);
    }

    // ==================== Helper Methods ====================

    private RelationTableDefinition buildDeployedTable(Long id, String tableName) {
        return RelationTableDefinition.builder()
                .id(id)
                .tableName(tableName)
                .displayName("Display " + tableName)
                .description("Description for " + tableName)
                .status(RelationTableStatus.DEPLOYED)
                .enabled(true)
                .portalVisible(false)
                .currentVersion(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .fieldDefinitions(new ArrayList<>())
                .versions(new ArrayList<>())
                .build();
    }

    private RelationTableDefinition buildDraftTable(Long id, String tableName) {
        return RelationTableDefinition.builder()
                .id(id)
                .tableName(tableName)
                .displayName("Display " + tableName)
                .status(RelationTableStatus.DRAFT)
                .enabled(true)
                .portalVisible(false)
                .currentVersion(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .fieldDefinitions(new ArrayList<>())
                .versions(new ArrayList<>())
                .build();
    }

    private List<RelationFieldDTO> buildStandardFields() {
        return List.of(
                RelationFieldDTO.builder()
                        .fieldName("id")
                        .dataType(RelationDataType.BIGINT)
                        .nullable(false)
                        .isPrimaryKey(true)
                        .sortOrder(0)
                        .build(),
                RelationFieldDTO.builder()
                        .fieldName("name")
                        .dataType(RelationDataType.VARCHAR)
                        .length(255)
                        .nullable(true)
                        .isPrimaryKey(false)
                        .sortOrder(1)
                        .build(),
                RelationFieldDTO.builder()
                        .fieldName("status")
                        .dataType(RelationDataType.VARCHAR)
                        .length(50)
                        .nullable(true)
                        .isPrimaryKey(false)
                        .sortOrder(2)
                        .build()
        );
    }

    private RelationTableVersion buildVersion(RelationTableDefinition table, String snapshotData) {
        return RelationTableVersion.builder()
                .id(1L)
                .tableDefinition(table)
                .versionNumber(1)
                .snapshotData(snapshotData)
                .deployedBy("admin")
                .deployedAt(Instant.now())
                .changeLog("Initial deployment")
                .build();
    }

    private void setupDeployedTableWithFields(Long tableId, String tableName) throws JsonProcessingException {
        RelationTableDefinition table = buildDeployedTable(tableId, tableName);
        String snapshotJson = "snapshot_json";
        RelationTableVersion version = buildVersion(table, snapshotJson);
        List<RelationFieldDTO> fields = buildStandardFields();

        when(tableDefinitionRepository.findById(tableId)).thenReturn(Optional.of(table));
        when(versionRepository.findLatestVersion(tableId)).thenReturn(Optional.of(version));
        when(objectMapper.readValue(eq(snapshotJson), any(TypeReference.class))).thenReturn(fields);
    }

    /** Mocks one JDBC ResultSet row for the queryData RowMapper (row_id, data JSON, status). */
    private ResultSet mockDataRowResultSet(String rowId, String dataJson, String status) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("row_id")).thenReturn(rowId);
        when(rs.getString("data")).thenReturn(dataJson);
        when(rs.getString("status")).thenReturn(status);
        return rs;
    }

    /** Stubs the getRowData single-row lookup: SELECT data ... WHERE table_id = ? AND row_id = ?. */
    @SuppressWarnings("unchecked")
    private void stubRowLookup(Long tableId, String rowId, List<Map<String, Object>> rows) {
        doReturn(rows).when(jdbcTemplate)
                .query(contains("SELECT"), ArgumentMatchers.<RowMapper<Map<String, Object>>>any(),
                        eq(tableId), eq(rowId));
    }

    // ==================== getDeployedTables Tests ====================

    @Nested
    @DisplayName("getDeployedTables() - 返回 DEPLOYED 和 UPDATED 状态且 enabled=true 的表")
    class GetDeployedTablesTests {

        @Test
        @DisplayName("Should return DEPLOYED and UPDATED tables that are enabled")
        void shouldReturnOnlyDeployedTables() {
            RelationTableDefinition deployed1 = buildDeployedTable(1L, "deployed_table_1");
            RelationTableDefinition deployed2 = buildDeployedTable(2L, "deployed_table_2");

            when(tableDefinitionRepository.findByStatusInAndEnabledTrue(
                    List.of(RelationTableStatus.DEPLOYED, RelationTableStatus.UPDATED, RelationTableStatus.ROLLBACK)))
                    .thenReturn(List.of(deployed1, deployed2));

            List<RelationTableResponse> result = service.getDeployedTables();

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(r -> r.getStatus() == RelationTableStatus.DEPLOYED);
            verify(tableDefinitionRepository).findByStatusInAndEnabledTrue(
                    List.of(RelationTableStatus.DEPLOYED, RelationTableStatus.UPDATED, RelationTableStatus.ROLLBACK));
        }

        @Test
        @DisplayName("Should return empty list when no deployed tables exist")
        void shouldReturnEmptyWhenNoDeployedTables() {
            when(tableDefinitionRepository.findByStatusInAndEnabledTrue(
                    List.of(RelationTableStatus.DEPLOYED, RelationTableStatus.UPDATED, RelationTableStatus.ROLLBACK)))
                    .thenReturn(Collections.emptyList());

            List<RelationTableResponse> result = service.getDeployedTables();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("UPDATED table should expose deployed display name and snapshot field labels in Table Data")
        void updatedTableShouldUseDeployedDisplayNameAndSnapshotFields() throws JsonProcessingException {
            RelationTableDefinition updatedTable = RelationTableDefinition.builder()
                    .id(3L)
                    .tableName("customer")
                    .displayName("Customer Draft")
                    .deployedDisplayName("Customer")
                    .status(RelationTableStatus.UPDATED)
                    .enabled(true)
                    .portalVisible(false)
                    .currentVersion(2)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .fieldDefinitions(new ArrayList<>())
                    .versions(new ArrayList<>())
                    .build();

            String snapshotJson = "snapshot_json";
            RelationTableVersion version = buildVersion(updatedTable, snapshotJson);
            List<RelationFieldDTO> snapshotFields = List.of(
                    RelationFieldDTO.builder()
                            .fieldName("name")
                            .dataType(RelationDataType.VARCHAR)
                            .displayName("Name Deployed")
                            .sortOrder(0)
                            .build());

            when(tableDefinitionRepository.findByStatusInAndEnabledTrue(
                    List.of(RelationTableStatus.DEPLOYED, RelationTableStatus.UPDATED, RelationTableStatus.ROLLBACK)))
                    .thenReturn(List.of(updatedTable));
            when(versionRepository.findLatestVersion(3L)).thenReturn(Optional.of(version));
            when(objectMapper.readValue(eq(snapshotJson), any(TypeReference.class))).thenReturn(snapshotFields);

            List<RelationTableResponse> result = service.getDeployedTables();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getDisplayName()).isEqualTo("Customer");
            assertThat(result.get(0).getFieldDefinitions()).singleElement()
                    .extracting(RelationTableResponse.FieldDefinitionResponse::getDisplayName)
                    .isEqualTo("Name Deployed");
        }
    }

    // ==================== queryData Tests ====================

    @Nested
    @DisplayName("queryData() - 分页查询与搜索过滤")
    class QueryDataTests {

        @Test
        @DisplayName("Should return paginated data for deployed table")
        void shouldReturnPaginatedData() throws Exception {
            setupDeployedTableWithFields(1L, "test_table");
            Pageable pageable = PageRequest.of(0, 10);

            Map<String, Object> row1 = new LinkedHashMap<>();
            row1.put("id", 1L);
            row1.put("name", "Alice");
            row1.put("status", "Active");

            Map<String, Object> row2 = new LinkedHashMap<>();
            row2.put("id", 2L);
            row2.put("name", "Bob");
            row2.put("status", "Active");

            // No search term → empty params for count, pagination params for data query
            when(jdbcTemplate.queryForObject(contains("COUNT(*)"), eq(Long.class), any(Object[].class)))
                    .thenReturn(2L);
            ResultSet rs1 = mockDataRowResultSet("1", "row1_json", "ACTIVE");
            ResultSet rs2 = mockDataRowResultSet("2", "row2_json", "ACTIVE");
            when(objectMapper.readValue(eq("row1_json"), any(TypeReference.class))).thenReturn(new LinkedHashMap<>(row1));
            when(objectMapper.readValue(eq("row2_json"), any(TypeReference.class))).thenReturn(new LinkedHashMap<>(row2));
            when(jdbcTemplate.query(contains("SELECT"),
                    ArgumentMatchers.<RowMapper<RelationTableDataRowDTO>>any(), any(Object[].class)))
                    .thenAnswer(inv -> {
                        RowMapper<RelationTableDataRowDTO> mapper = inv.getArgument(1);
                        return List.of(mapper.mapRow(rs1, 0), mapper.mapRow(rs2, 1));
                    });

            Page<RelationTableDataRowDTO> result = service.queryData(1L, null, pageable);

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent().get(0).getRowId()).isEqualTo("1");
            assertThat(result.getContent().get(0).getTableId()).isEqualTo(1L);
            assertThat(result.getContent().get(1).getRowId()).isEqualTo("2");
        }

        @Test
        @DisplayName("Should apply search filter on text fields")
        void shouldApplySearchFilter() throws Exception {
            setupDeployedTableWithFields(1L, "test_table");
            Pageable pageable = PageRequest.of(0, 10);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", 1L);
            row.put("name", "Alice");
            row.put("status", "Active");

            when(jdbcTemplate.queryForObject(contains("COUNT(*)"), eq(Long.class), any(Object[].class)))
                    .thenReturn(1L);
            ResultSet rs = mockDataRowResultSet("1", "row_json", "ACTIVE");
            when(objectMapper.readValue(eq("row_json"), any(TypeReference.class))).thenReturn(new LinkedHashMap<>(row));
            when(jdbcTemplate.query(contains("ILIKE"),
                    ArgumentMatchers.<RowMapper<RelationTableDataRowDTO>>any(), any(Object[].class)))
                    .thenAnswer(inv -> {
                        RowMapper<RelationTableDataRowDTO> mapper = inv.getArgument(1);
                        return List.of(mapper.mapRow(rs, 0));
                    });

            Page<RelationTableDataRowDTO> result = service.queryData(1L, "Alice", pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should return empty page when no data matches")
        void shouldReturnEmptyPageWhenNoMatch() throws JsonProcessingException {
            setupDeployedTableWithFields(1L, "test_table");
            Pageable pageable = PageRequest.of(0, 10);

            when(jdbcTemplate.queryForObject(contains("COUNT(*)"), eq(Long.class), any(Object[].class)))
                    .thenReturn(0L);
            when(jdbcTemplate.query(contains("SELECT"),
                    ArgumentMatchers.<RowMapper<RelationTableDataRowDTO>>any(), any(Object[].class)))
                    .thenReturn(Collections.emptyList());

            Page<RelationTableDataRowDTO> result = service.queryData(1L, "nonexistent", pageable);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should throw when table not found")
        void shouldThrowWhenTableNotFound() {
            when(tableDefinitionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.queryData(999L, null, PageRequest.of(0, 10)))
                    .isInstanceOf(RelationTableNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw when table is not deployed")
        void shouldThrowWhenTableNotDeployed() {
            RelationTableDefinition draftTable = buildDraftTable(1L, "draft_table");
            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(draftTable));

            assertThatThrownBy(() -> service.queryData(1L, null, PageRequest.of(0, 10)))
                    .isInstanceOf(RelationTableNotFoundException.class);
        }
    }

    // ==================== addData Tests ====================

    @Nested
    @DisplayName("addData() - 新增数据")
    class AddDataTests {

        @Test
        @DisplayName("Should insert data and call audit log")
        void shouldInsertDataAndAudit() throws JsonProcessingException {
            setupDeployedTableWithFields(1L, "test_table");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", "100");
            data.put("name", "Charlie");
            data.put("status", "Active");

            when(jdbcTemplate.update(contains("INSERT INTO"), any(Object[].class))).thenReturn(1);

            RelationTableDataRowDTO result = service.addData(1L, data);

            assertThat(result.getRowId()).isEqualTo("100");
            assertThat(result.getTableId()).isEqualTo(1L);
            assertThat(result.getData()).containsEntry("name", "Charlie");

            verify(jdbcTemplate).update(contains("INSERT INTO"), any(Object[].class));
            verify(auditService).logAdd(eq(1L), eq("test_table"), eq("100"), anyMap());
        }

        @Test
        @DisplayName("Should filter out invalid field names")
        void shouldFilterInvalidFields() throws JsonProcessingException {
            setupDeployedTableWithFields(1L, "test_table");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", "101");
            data.put("name", "Dave");
            data.put("invalid_field", "should_be_filtered");

            when(jdbcTemplate.update(contains("INSERT INTO"), any(Object[].class))).thenReturn(1);

            RelationTableDataRowDTO result = service.addData(1L, data);

            assertThat(result.getData()).doesNotContainKey("invalid_field");
            verify(auditService).logAdd(eq(1L), eq("test_table"), eq("101"), argThat(map ->
                    !map.containsKey("invalid_field")));
        }

        @Test
        @DisplayName("Should throw when table not deployed for add")
        void shouldThrowWhenTableNotDeployedForAdd() {
            RelationTableDefinition draftTable = buildDraftTable(1L, "draft_table");
            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(draftTable));

            assertThatThrownBy(() -> service.addData(1L, Map.of("name", "test")))
                    .isInstanceOf(RelationTableNotFoundException.class);
        }
    }

    // ==================== updateData Tests ====================

    @Nested
    @DisplayName("updateData() - 修改数据")
    class UpdateDataTests {

        @Test
        @DisplayName("Should update data and call audit log with old/new values")
        void shouldUpdateDataAndAudit() throws JsonProcessingException {
            setupDeployedTableWithFields(1L, "test_table");

            Map<String, Object> oldRow = new LinkedHashMap<>();
            oldRow.put("id", 1L);
            oldRow.put("name", "OldName");
            oldRow.put("status", "Active");

            Map<String, Object> newRow = new LinkedHashMap<>();
            newRow.put("id", 1L);
            newRow.put("name", "NewName");
            newRow.put("status", "Active");

            stubRowLookup(1L, "1", List.of(oldRow));
            when(jdbcTemplate.update(contains("UPDATE"), any(Object[].class))).thenReturn(1);

            Map<String, Object> updateData = Map.of("name", "NewName");
            RelationTableDataRowDTO result = service.updateData(1L, "1", updateData);

            assertThat(result.getRowId()).isEqualTo("1");
            assertThat(result.getTableId()).isEqualTo(1L);

            verify(jdbcTemplate).update(contains("UPDATE"), any(Object[].class));
            verify(auditService).logUpdate(eq(1L), eq("test_table"), eq("1"), eq(oldRow), eq(newRow));
        }

        @Test
        @DisplayName("Should throw when row not found for update")
        void shouldThrowWhenRowNotFoundForUpdate() throws JsonProcessingException {
            setupDeployedTableWithFields(1L, "test_table");

            stubRowLookup(1L, "999", Collections.emptyList());

            assertThatThrownBy(() -> service.updateData(1L, "999", Map.of("name", "test")))
                    .isInstanceOf(RelationTableNotFoundException.class)
                    .hasMessageContaining("Row not found");
        }

        @Test
        @DisplayName("Should return existing data when no valid update fields provided")
        void shouldReturnExistingDataWhenNoValidFields() throws JsonProcessingException {
            setupDeployedTableWithFields(1L, "test_table");

            Map<String, Object> existingRow = new LinkedHashMap<>();
            existingRow.put("id", 1L);
            existingRow.put("name", "Existing");
            existingRow.put("status", "Active");

            stubRowLookup(1L, "1", List.of(existingRow));

            // Only PK field in update data - should be filtered out
            Map<String, Object> updateData = Map.of("id", "1");
            RelationTableDataRowDTO result = service.updateData(1L, "1", updateData);

            assertThat(result.getData()).isEqualTo(existingRow);
            verify(jdbcTemplate, never()).update(contains("UPDATE"), any(Object[].class));
        }
    }

    // ==================== deleteData Tests ====================

    @Nested
    @DisplayName("deleteData() - 删除数据")
    class DeleteDataTests {

        @Test
        @DisplayName("Should delete data and call audit log")
        void shouldDeleteDataAndAudit() throws JsonProcessingException {
            // deleteData only loads the table definition (no field snapshot needed)
            when(tableDefinitionRepository.findById(1L))
                    .thenReturn(Optional.of(buildDeployedTable(1L, "test_table")));

            Map<String, Object> existingRow = new LinkedHashMap<>();
            existingRow.put("id", 1L);
            existingRow.put("name", "ToDelete");
            existingRow.put("status", "Active");

            stubRowLookup(1L, "1", List.of(existingRow));
            when(jdbcTemplate.update(contains("DELETE"), eq(1L), eq("1"))).thenReturn(1);

            service.deleteData(1L, "1");

            verify(jdbcTemplate).update(contains("DELETE"), eq(1L), eq("1"));
            verify(auditService).logDelete(eq(1L), eq("test_table"), eq("1"), eq(existingRow));
        }

        @Test
        @DisplayName("Should throw when row not found for delete")
        void shouldThrowWhenRowNotFoundForDelete() throws JsonProcessingException {
            when(tableDefinitionRepository.findById(1L))
                    .thenReturn(Optional.of(buildDeployedTable(1L, "test_table")));

            stubRowLookup(1L, "999", Collections.emptyList());

            assertThatThrownBy(() -> service.deleteData(1L, "999"))
                    .isInstanceOf(RelationTableNotFoundException.class)
                    .hasMessageContaining("Row not found");
        }

        @Test
        @DisplayName("Should throw when table not found for delete")
        void shouldThrowWhenTableNotFoundForDelete() {
            when(tableDefinitionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteData(999L, "1"))
                    .isInstanceOf(RelationTableNotFoundException.class);
        }
    }

    // ==================== changeStatus Tests ====================

    @Nested
    @DisplayName("changeStatus() - 变更数据状态")
    class ChangeStatusTests {

        @Test
        @DisplayName("Should change status and call audit log")
        void shouldChangeStatusAndAudit() throws JsonProcessingException {
            setupDeployedTableWithFields(1L, "test_table");

            Map<String, Object> existingRow = new LinkedHashMap<>();
            existingRow.put("id", 1L);
            existingRow.put("name", "TestRow");
            existingRow.put("status", "Active");

            stubRowLookup(1L, "1", List.of(existingRow));
            when(jdbcTemplate.update(contains("SET status"), any(Object[].class))).thenReturn(1);

            RelationTableDataRowDTO result = service.changeStatus(1L, "1", "Inactive");

            assertThat(result.getRowId()).isEqualTo("1");
            assertThat(result.getTableId()).isEqualTo(1L);

            verify(auditService).logStatusChange(eq(1L), eq("test_table"), eq("1"), eq("Active"), eq("Inactive"));
        }

        @Test
        @DisplayName("Should throw when row not found for status change")
        void shouldThrowWhenRowNotFoundForStatusChange() throws JsonProcessingException {
            setupDeployedTableWithFields(1L, "test_table");

            stubRowLookup(1L, "999", Collections.emptyList());

            assertThatThrownBy(() -> service.changeStatus(1L, "999", "Inactive"))
                    .isInstanceOf(RelationTableNotFoundException.class)
                    .hasMessageContaining("Row not found");
        }

        @Test
        @DisplayName("Should throw when table not deployed for status change")
        void shouldThrowWhenTableNotDeployedForStatusChange() {
            RelationTableDefinition draftTable = buildDraftTable(1L, "draft_table");
            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(draftTable));

            assertThatThrownBy(() -> service.changeStatus(1L, "1", "Inactive"))
                    .isInstanceOf(RelationTableNotFoundException.class);
        }
    }
}
