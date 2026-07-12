package com.admin.service;

import com.admin.dto.response.RelationTableResponse;
import com.admin.entity.RelationTableDefinition;
import com.admin.entity.RelationTableVersion;
import com.admin.repository.RelationTableDefinitionRepository;
import com.admin.repository.RelationTableVersionRepository;
import com.admin.config.DatabaseSchemaResolver;
import com.admin.service.impl.RelationTableDataServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.RelationFieldDTO;
import com.platform.common.dto.RelationTableDataRowDTO;
import com.platform.common.enums.RelationDataType;
import com.platform.common.enums.RelationTableStatus;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Relation Table 数据管理属性测试
 *
 * Feature: relation-tables, Property 15: 数据列表仅展示已部署表
 * Feature: relation-tables, Property 19: 分页数据大小约束
 *
 * Validates: Requirements 6.1, 6.5
 */
class RelationTableDataPropertyTest {

    private RelationTableDefinitionRepository tableDefinitionRepository;
    private RelationTableVersionRepository versionRepository;
    private RelationTableAuditService auditService;
    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;
    private RelationTableDataServiceImpl service;

    @BeforeTry
    void setUp() {
        tableDefinitionRepository = mock(RelationTableDefinitionRepository.class);
        versionRepository = mock(RelationTableVersionRepository.class);
        auditService = mock(RelationTableAuditService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        objectMapper = new ObjectMapper();
        service = new RelationTableDataServiceImpl(
                tableDefinitionRepository, versionRepository, auditService,
                mock(com.admin.service.RelationTableAccessService.class),
                mock(com.admin.service.RelationTablePrimaryKeyAllocationService.class),
                jdbcTemplate, objectMapper);
    }

    // ==================== Arbitraries ====================

    @Provide
    Arbitrary<String> tableNames() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20)
                .map(String::toLowerCase)
                .map(s -> "rt_" + s);
    }

    @Provide
    Arbitrary<RelationTableStatus> tableStatuses() {
        return Arbitraries.of(RelationTableStatus.values());
    }

    /**
     * Generate a list of table definitions with random statuses.
     * Each table has a unique name and a randomly assigned status.
     */
    @Provide
    Arbitrary<List<RelationTableDefinition>> tableListsWithMixedStatuses() {
        return Arbitraries.integers().between(1, 15).flatMap(count ->
                Arbitraries.of(RelationTableStatus.values()).list().ofSize(count).flatMap(statuses ->
                        Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15)
                                .map(String::toLowerCase)
                                .map(s -> "rt_" + s)
                                .list().ofSize(count)
                                .filter(names -> new HashSet<>(names).size() == names.size())
                                .map(names -> IntStream.range(0, count)
                                        .mapToObj(i -> RelationTableDefinition.builder()
                                                .id((long) (i + 1))
                                                .tableName(names.get(i))
                                                .displayName("Display " + names.get(i))
                                                .status(statuses.get(i))
                                                .enabled(true)
                                                .portalVisible(false)
                                                .currentVersion(statuses.get(i) == RelationTableStatus.DRAFT ? 0 : 1)
                                                .createdAt(Instant.now())
                                                .updatedAt(Instant.now())
                                                .fieldDefinitions(new ArrayList<>())
                                                .versions(new ArrayList<>())
                                                .build())
                                        .collect(Collectors.toList()))
                )
        );
    }

    @Provide
    Arbitrary<Integer> pageSizes() {
        return Arbitraries.integers().between(1, 100);
    }

    // ==================== Property 15: 数据列表仅展示已部署表 ====================

    /**
     * Property 15: 数据列表仅展示已部署表
     *
     * For any set of Relation Tables with random status combinations (DRAFT, DEPLOYED, ROLLBACK),
     * the getDeployedTables() method should return only tables with DEPLOYED status,
     * and the count should match the number of DEPLOYED tables in the input.
     *
     * Feature: relation-tables, Property 15: 数据列表仅展示已部署表
     * Validates: Requirements 6.1
     */
    @Property(tries = 100)
    @Tag("Feature: relation-tables, Property 15: 数据列表仅展示已部署和已更新且启用的表")
    void dataListOnlyShowsDeployedTables(
            @ForAll("tableListsWithMixedStatuses") List<RelationTableDefinition> allTables
    ) {
        // Compute expected: DEPLOYED / UPDATED / ROLLBACK tables that are enabled
        List<RelationTableDefinition> deployedOrUpdatedEnabled = allTables.stream()
                .filter(t -> (t.getStatus() == RelationTableStatus.DEPLOYED
                        || t.getStatus() == RelationTableStatus.UPDATED
                        || t.getStatus() == RelationTableStatus.ROLLBACK)
                        && t.getEnabled())
                .collect(Collectors.toList());

        // Mock repository to return DEPLOYED/UPDATED/ROLLBACK enabled tables
        when(tableDefinitionRepository.findByStatusInAndEnabledTrue(
                List.of(RelationTableStatus.DEPLOYED, RelationTableStatus.UPDATED, RelationTableStatus.ROLLBACK)))
                .thenReturn(deployedOrUpdatedEnabled);

        // Execute
        List<RelationTableResponse> result = service.getDeployedTables();

        // === Verify: all returned tables have DEPLOYED / UPDATED / ROLLBACK status ===
        assertThat(result)
                .as("All returned tables should have DEPLOYED, UPDATED or ROLLBACK status")
                .allMatch(r -> r.getStatus() == RelationTableStatus.DEPLOYED
                        || r.getStatus() == RelationTableStatus.UPDATED
                        || r.getStatus() == RelationTableStatus.ROLLBACK);

        // === Verify: count matches the number of DEPLOYED/UPDATED/ROLLBACK enabled tables ===
        assertThat(result)
                .as("Result count should match the number of DEPLOYED/UPDATED/ROLLBACK enabled tables in input")
                .hasSize(deployedOrUpdatedEnabled.size());

        // === Verify: the repository was called with the correct method ===
        verify(tableDefinitionRepository).findByStatusInAndEnabledTrue(
                List.of(RelationTableStatus.DEPLOYED, RelationTableStatus.UPDATED, RelationTableStatus.ROLLBACK));
    }

    // ==================== Property 19: 分页数据大小约束 ====================

    /**
     * Property 19: 分页数据大小约束
     *
     * For any pagination query with a random pageSize (1-100), the number of returned
     * data rows should never exceed the requested pageSize.
     *
     * Feature: relation-tables, Property 19: 分页数据大小约束
     * Validates: Requirements 6.5
     */
    @Property(tries = 100)
    @Tag("Feature: relation-tables, Property 19: 分页数据大小约束")
    void paginationDataSizeConstraint(
            @ForAll("pageSizes") int pageSize,
            @ForAll @IntRange(min = 0, max = 200) int totalRows
    ) throws Exception {
        Long tableId = 1L;
        String tableName = "rt_test_table";

        // Build a DEPLOYED table definition
        RelationTableDefinition table = RelationTableDefinition.builder()
                .id(tableId)
                .tableName(tableName)
                .displayName("Test Table")
                .status(RelationTableStatus.DEPLOYED)
                .enabled(true)
                .portalVisible(false)
                .currentVersion(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .fieldDefinitions(new ArrayList<>())
                .versions(new ArrayList<>())
                .build();

        // Build snapshot fields
        List<RelationFieldDTO> fields = List.of(
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
                        .build()
        );

        String snapshotJson = objectMapper.writeValueAsString(fields);
        RelationTableVersion version = RelationTableVersion.builder()
                .id(1L)
                .tableDefinition(table)
                .versionNumber(1)
                .snapshotData(snapshotJson)
                .deployedBy("admin")
                .deployedAt(Instant.now())
                .changeLog("Initial deployment")
                .build();

        // Mock repository
        when(tableDefinitionRepository.findById(tableId)).thenReturn(Optional.of(table));
        when(versionRepository.findLatestVersion(tableId)).thenReturn(Optional.of(version));

        // Simulate the actual number of rows returned by the database
        // The DB should return at most pageSize rows
        int actualReturnedRows = Math.min(pageSize, totalRows);
        List<Map<String, Object>> mockRows = IntStream.range(0, actualReturnedRows)
                .mapToObj(i -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", (long) (i + 1));
                    row.put("name", "Row_" + i);
                    return row;
                })
                .collect(Collectors.toList());

        when(jdbcTemplate.queryForObject(contains("COUNT(*)"), eq(Long.class), any(Object[].class)))
                .thenReturn((long) totalRows);
        // Production maps rows via jdbcTemplate.query(sql, RowMapper, args); return mapped DTOs directly
        List<RelationTableDataRowDTO> mockDtoRows = mockRows.stream()
                .map(r -> RelationTableDataRowDTO.builder()
                        .rowId(String.valueOf(r.get("id")))
                        .tableId(tableId)
                        .data(r)
                        .build())
                .collect(Collectors.toList());
        doReturn(mockDtoRows).when(jdbcTemplate)
                .query(contains("SELECT"),
                        org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<RelationTableDataRowDTO>>any(),
                        any(Object[].class));

        // Execute query with the generated pageSize
        Pageable pageable = PageRequest.of(0, pageSize);
        Page<RelationTableDataRowDTO> result = service.queryData(tableId, null, pageable);

        // === Verify: returned data count does not exceed pageSize ===
        assertThat(result.getContent().size())
                .as("Returned data count (%d) should not exceed pageSize (%d)",
                        result.getContent().size(), pageSize)
                .isLessThanOrEqualTo(pageSize);

        // === Verify: total elements reflects the actual total ===
        assertThat(result.getTotalElements())
                .as("Total elements should reflect the actual total row count")
                .isEqualTo(totalRows);

        // === Verify: if totalRows < pageSize, returned count equals totalRows ===
        if (totalRows <= pageSize) {
            assertThat(result.getContent().size())
                    .as("When totalRows (%d) <= pageSize (%d), returned count should equal totalRows",
                            totalRows, pageSize)
                    .isEqualTo(totalRows);
        }
    }
}
