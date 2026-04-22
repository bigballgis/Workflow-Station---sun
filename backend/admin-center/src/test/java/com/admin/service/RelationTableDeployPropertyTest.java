package com.admin.service;

import com.admin.dto.request.RollbackRequest;
import com.admin.dto.response.RelationTableResponse;
import com.admin.entity.RelationFieldDefinition;
import com.admin.entity.RelationTableDefinition;
import com.admin.entity.RelationTableVersion;
import com.admin.exception.RelationTableDeploymentException;
import com.admin.repository.RelationTableDefinitionRepository;
import com.admin.repository.RelationTableVersionRepository;
import com.admin.service.impl.RelationTableDeployServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.RelationFieldDTO;
import com.platform.common.enums.RelationDataType;
import com.platform.common.enums.RelationTableStatus;
import com.platform.security.util.SecurityContextUtils;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Relation Table 部署与回滚属性测试
 *
 * Feature: relation-tables, Property 7: 部署版本递增与快照
 * Feature: relation-tables, Property 8: 回滚恢复表定义
 * Feature: relation-tables, Property 17: 部署失败回滚
 *
 * Validates: Requirements 5.2, 5.4, 5.6
 */
class RelationTableDeployPropertyTest {

    private RelationTableDefinitionRepository tableDefinitionRepository;
    private RelationTableVersionRepository versionRepository;
    private com.admin.repository.RelationFieldDefinitionRepository fieldDefinitionRepository;
    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;
    private com.admin.config.DatabaseSchemaResolver schemaResolver;
    private RelationTableDeployServiceImpl service;

    @BeforeTry
    void setUp() {
        tableDefinitionRepository = mock(RelationTableDefinitionRepository.class);
        versionRepository = mock(RelationTableVersionRepository.class);
        fieldDefinitionRepository = mock(com.admin.repository.RelationFieldDefinitionRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        objectMapper = new ObjectMapper();
        schemaResolver = mock(com.admin.config.DatabaseSchemaResolver.class);
        when(schemaResolver.getSchema()).thenReturn("public");
        service = new RelationTableDeployServiceImpl(
                tableDefinitionRepository, versionRepository, fieldDefinitionRepository, jdbcTemplate, objectMapper, schemaResolver);
    }

    // ==================== Arbitraries ====================

    @Provide
    Arbitrary<RelationDataType> dataTypes() {
        return Arbitraries.of(RelationDataType.values());
    }

    @Provide
    Arbitrary<String> fieldNames() {
        return Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(20)
                .map(String::toLowerCase)
                .map(s -> "f_" + s);
    }

    @Provide
    Arbitrary<List<FieldSpec>> fieldSpecs() {
        return fieldSpec().list().ofMinSize(1).ofMaxSize(6)
                .filter(list -> {
                    Set<String> names = list.stream()
                            .map(FieldSpec::fieldName)
                            .collect(Collectors.toSet());
                    return names.size() == list.size();
                });
    }

    private Arbitrary<FieldSpec> fieldSpec() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(15)
                        .map(String::toLowerCase).map(s -> "f_" + s),
                Arbitraries.of(RelationDataType.values()),
                Arbitraries.integers().between(1, 500),
                Arbitraries.of(true, false),
                Arbitraries.of(true, false)
        ).as(FieldSpec::new);
    }

    @Provide
    Arbitrary<Integer> currentVersions() {
        return Arbitraries.integers().between(0, 50);
    }

    @Provide
    Arbitrary<String> tableNames() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20)
                .map(String::toLowerCase)
                .map(s -> "rt_" + s);
    }

    // ==================== Property 7: 部署版本递增与快照 ====================

    /**
     * Property 7: 部署版本递增与快照
     *
     * For any successful deploy operation, the new version number should equal
     * currentVersion + 1, and the snapshot data should match the field definitions
     * at the time of deployment.
     *
     * Feature: relation-tables, Property 7: 部署版本递增与快照
     * Validates: Requirements 5.2
     */
    @Property(tries = 100)
    @Tag("Feature: relation-tables, Property 7: 部署版本递增与快照")
    void deployVersionIncrementAndSnapshot(
            @ForAll("fieldSpecs") List<FieldSpec> fieldSpecs,
            @ForAll("currentVersions") int currentVersion,
            @ForAll("tableNames") String tableName
    ) {
        try (MockedStatic<SecurityContextUtils> securityMock = mockStatic(SecurityContextUtils.class)) {
            securityMock.when(SecurityContextUtils::getCurrentUsername)
                    .thenReturn(Optional.of("test_admin"));

            // Build table definition with generated fields
            RelationTableDefinition table = RelationTableDefinition.builder()
                    .id(1L)
                    .tableName(tableName)
                    .displayName("Display " + tableName)
                    .status(currentVersion == 0 ? RelationTableStatus.DRAFT : RelationTableStatus.DEPLOYED)
                    .enabled(true)
                    .portalVisible(false)
                    .currentVersion(currentVersion)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .fieldDefinitions(new ArrayList<>())
                    .versions(new ArrayList<>())
                    .build();

            List<RelationFieldDefinition> fields = IntStream.range(0, fieldSpecs.size())
                    .mapToObj(i -> {
                        FieldSpec spec = fieldSpecs.get(i);
                        return RelationFieldDefinition.builder()
                                .id((long) (i + 1))
                                .tableDefinition(table)
                                .fieldName(spec.fieldName())
                                .dataType(spec.dataType())
                                .length(spec.dataType() == RelationDataType.VARCHAR ? spec.length() : null)
                                .precision(spec.dataType() == RelationDataType.DECIMAL ? 10 : null)
                                .scale(spec.dataType() == RelationDataType.DECIMAL ? 2 : null)
                                .nullable(spec.nullable())
                                .isPrimaryKey(spec.isPrimaryKey())
                                .sortOrder(i)
                                .build();
                    })
                    .collect(Collectors.toList());
            table.getFieldDefinitions().addAll(fields);

            // Mock repository
            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(table));

            // For incremental deploy, mock the alter table path
            if (currentVersion > 0) {
                when(tableDefinitionRepository.findByTableName(tableName)).thenReturn(Optional.of(table));
                // Return empty so it falls back to CREATE TABLE DDL list
                when(versionRepository.findLatestVersion(1L)).thenReturn(Optional.empty());
            }

            // Capture saved version
            ArgumentCaptor<RelationTableVersion> versionCaptor =
                    ArgumentCaptor.forClass(RelationTableVersion.class);
            when(versionRepository.save(versionCaptor.capture()))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(tableDefinitionRepository.save(any(RelationTableDefinition.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Execute deploy
            RelationTableResponse result = service.deploy(1L);

            // === Verify version number = currentVersion + 1 ===
            int expectedVersion = currentVersion + 1;
            assertThat(result.getCurrentVersion())
                    .as("Version should be currentVersion + 1 = %d", expectedVersion)
                    .isEqualTo(expectedVersion);

            RelationTableVersion savedVersion = versionCaptor.getValue();
            assertThat(savedVersion.getVersionNumber()).isEqualTo(expectedVersion);

            // === Verify snapshot data matches field definitions ===
            assertThat(savedVersion.getSnapshotData()).isNotNull();
            List<RelationFieldDTO> snapshotFields;
            try {
                snapshotFields = objectMapper.readValue(
                        savedVersion.getSnapshotData(), new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                fail("Snapshot data should be valid JSON: " + e.getMessage());
                return;
            }

            assertThat(snapshotFields).hasSameSizeAs(fieldSpecs);
            for (int i = 0; i < fieldSpecs.size(); i++) {
                FieldSpec spec = fieldSpecs.get(i);
                RelationFieldDTO snapshot = snapshotFields.get(i);
                assertThat(snapshot.getFieldName()).isEqualTo(spec.fieldName());
                assertThat(snapshot.getDataType()).isEqualTo(spec.dataType());
            }

            // === Verify status is DEPLOYED ===
            assertThat(result.getStatus()).isEqualTo(RelationTableStatus.DEPLOYED);
        }
    }

    // ==================== Property 8: 回滚恢复表定义 ====================

    /**
     * Property 8: 回滚恢复表定义
     *
     * For any rollback operation targeting a historical version, the current table
     * definition's field list should match the target version's snapshot, and a new
     * version number should be generated.
     *
     * Feature: relation-tables, Property 8: 回滚恢复表定义
     * Validates: Requirements 5.4
     */
    @Property(tries = 100)
    @Tag("Feature: relation-tables, Property 8: 回滚恢复表定义")
    void rollbackRestoresTableDefinition(
            @ForAll("fieldSpecs") List<FieldSpec> snapshotFieldSpecs,
            @ForAll("fieldSpecs") List<FieldSpec> currentFieldSpecs,
            @ForAll @IntRange(min = 1, max = 50) int currentVersion,
            @ForAll @IntRange(min = 1, max = 50) int targetVersionNumber,
            @ForAll("tableNames") String tableName
    ) {
        try (MockedStatic<SecurityContextUtils> securityMock = mockStatic(SecurityContextUtils.class)) {
            securityMock.when(SecurityContextUtils::getCurrentUsername)
                    .thenReturn(Optional.of("test_admin"));

            // Build current table definition with current fields
            RelationTableDefinition table = RelationTableDefinition.builder()
                    .id(1L)
                    .tableName(tableName)
                    .displayName("Display " + tableName)
                    .status(RelationTableStatus.DEPLOYED)
                    .enabled(true)
                    .portalVisible(false)
                    .currentVersion(currentVersion)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .fieldDefinitions(new ArrayList<>())
                    .versions(new ArrayList<>())
                    .build();

            // Add current fields
            for (int i = 0; i < currentFieldSpecs.size(); i++) {
                FieldSpec spec = currentFieldSpecs.get(i);
                table.getFieldDefinitions().add(RelationFieldDefinition.builder()
                        .id((long) (i + 100))
                        .tableDefinition(table)
                        .fieldName(spec.fieldName())
                        .dataType(spec.dataType())
                        .length(spec.dataType() == RelationDataType.VARCHAR ? spec.length() : null)
                        .nullable(spec.nullable())
                        .isPrimaryKey(spec.isPrimaryKey())
                        .sortOrder(i)
                        .build());
            }

            // Build snapshot data from snapshotFieldSpecs
            List<RelationFieldDTO> snapshotDtos = IntStream.range(0, snapshotFieldSpecs.size())
                    .mapToObj(i -> {
                        FieldSpec spec = snapshotFieldSpecs.get(i);
                        return RelationFieldDTO.builder()
                                .fieldName(spec.fieldName())
                                .dataType(spec.dataType())
                                .length(spec.dataType() == RelationDataType.VARCHAR ? spec.length() : null)
                                .nullable(spec.nullable())
                                .isPrimaryKey(spec.isPrimaryKey())
                                .sortOrder(i)
                                .build();
                    })
                    .collect(Collectors.toList());

            String snapshotJson;
            try {
                snapshotJson = objectMapper.writeValueAsString(snapshotDtos);
            } catch (JsonProcessingException e) {
                fail("Failed to serialize snapshot: " + e.getMessage());
                return;
            }

            RelationTableVersion targetVersion = RelationTableVersion.builder()
                    .id(10L)
                    .tableDefinition(table)
                    .versionNumber(targetVersionNumber)
                    .snapshotData(snapshotJson)
                    .deployedBy("admin")
                    .deployedAt(Instant.now().minusSeconds(3600))
                    .changeLog("Version " + targetVersionNumber)
                    .build();

            // Mock repositories
            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(table));
            when(versionRepository.findById(10L)).thenReturn(Optional.of(targetVersion));

            ArgumentCaptor<RelationTableVersion> versionCaptor =
                    ArgumentCaptor.forClass(RelationTableVersion.class);
            when(versionRepository.save(versionCaptor.capture()))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(tableDefinitionRepository.save(any(RelationTableDefinition.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Execute rollback
            RollbackRequest request = RollbackRequest.builder().targetVersionId(10L).build();
            RelationTableResponse result = service.rollback(1L, request);

            // === Verify field list matches snapshot ===
            assertThat(table.getFieldDefinitions()).hasSameSizeAs(snapshotFieldSpecs);
            for (int i = 0; i < snapshotFieldSpecs.size(); i++) {
                FieldSpec expectedSpec = snapshotFieldSpecs.get(i);
                RelationFieldDefinition actual = table.getFieldDefinitions().get(i);
                assertThat(actual.getFieldName())
                        .as("Field %d name should match snapshot", i)
                        .isEqualTo(expectedSpec.fieldName());
                assertThat(actual.getDataType())
                        .as("Field %d dataType should match snapshot", i)
                        .isEqualTo(expectedSpec.dataType());
            }

            // === Verify new version number = currentVersion + 1 ===
            int expectedNewVersion = currentVersion + 1;
            assertThat(result.getCurrentVersion()).isEqualTo(expectedNewVersion);

            RelationTableVersion savedVersion = versionCaptor.getValue();
            assertThat(savedVersion.getVersionNumber()).isEqualTo(expectedNewVersion);
            assertThat(savedVersion.getSnapshotData()).isEqualTo(snapshotJson);

            // === Verify status is ROLLBACK ===
            assertThat(result.getStatus()).isEqualTo(RelationTableStatus.ROLLBACK);
        }
    }

    // ==================== Property 17: 部署失败回滚 ====================

    /**
     * Property 17: 部署失败回滚
     *
     * For any table definition that causes DDL execution to fail, the deploy operation
     * should fail, and the table's status, version number should remain unchanged.
     *
     * Feature: relation-tables, Property 17: 部署失败回滚
     * Validates: Requirements 5.6
     */
    @Property(tries = 100)
    @Tag("Feature: relation-tables, Property 17: 部署失败回滚")
    void deployFailurePreservesState(
            @ForAll("fieldSpecs") List<FieldSpec> fieldSpecs,
            @ForAll("currentVersions") int currentVersion,
            @ForAll("tableNames") String tableName
    ) {
        // Record original state
        RelationTableStatus originalStatus = currentVersion == 0
                ? RelationTableStatus.DRAFT : RelationTableStatus.DEPLOYED;

        RelationTableDefinition table = RelationTableDefinition.builder()
                .id(1L)
                .tableName(tableName)
                .displayName("Display " + tableName)
                .status(originalStatus)
                .enabled(true)
                .portalVisible(false)
                .currentVersion(currentVersion)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .fieldDefinitions(new ArrayList<>())
                .versions(new ArrayList<>())
                .build();

        List<RelationFieldDefinition> fields = IntStream.range(0, fieldSpecs.size())
                .mapToObj(i -> {
                    FieldSpec spec = fieldSpecs.get(i);
                    return RelationFieldDefinition.builder()
                            .id((long) (i + 1))
                            .tableDefinition(table)
                            .fieldName(spec.fieldName())
                            .dataType(spec.dataType())
                            .length(spec.dataType() == RelationDataType.VARCHAR ? spec.length() : null)
                            .nullable(spec.nullable())
                            .isPrimaryKey(spec.isPrimaryKey())
                            .sortOrder(i)
                            .build();
                })
                .collect(Collectors.toList());
        table.getFieldDefinitions().addAll(fields);

        // Mock repository
        when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(table));

        // For incremental deploy path
        if (currentVersion > 0) {
            when(tableDefinitionRepository.findByTableName(tableName)).thenReturn(Optional.of(table));
            when(versionRepository.findLatestVersion(1L)).thenReturn(Optional.empty());
        }

        // Mock DDL execution to throw exception
        doThrow(new RuntimeException("Simulated DDL failure"))
                .when(jdbcTemplate).execute(anyString());

        // Execute deploy - should throw
        assertThatThrownBy(() -> service.deploy(1L))
                .isInstanceOf(RelationTableDeploymentException.class);

        // === Verify status unchanged ===
        assertThat(table.getStatus())
                .as("Status should remain %s after failed deploy", originalStatus)
                .isEqualTo(originalStatus);

        // === Verify version number unchanged ===
        assertThat(table.getCurrentVersion())
                .as("Version should remain %d after failed deploy", currentVersion)
                .isEqualTo(currentVersion);

        // === Verify no version snapshot was created ===
        verify(versionRepository, never()).save(any());

        // === Verify table definition was not saved ===
        verify(tableDefinitionRepository, never()).save(any());
    }

    // ==================== Helper Record ====================

    record FieldSpec(String fieldName, RelationDataType dataType, int length,
                     boolean nullable, boolean isPrimaryKey) {}
}
