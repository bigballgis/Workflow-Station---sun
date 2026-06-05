package com.admin.service;

import com.admin.dto.request.RollbackRequest;
import com.admin.dto.response.RelationTableResponse;
import com.admin.dto.response.RelationTableVersionResponse;
import com.admin.entity.RelationFieldDefinition;
import com.admin.entity.RelationTableDefinition;
import com.admin.entity.RelationTableVersion;
import com.admin.exception.RelationTableDeploymentException;
import com.admin.exception.RelationTableNotFoundException;
import com.admin.repository.RelationTableDefinitionRepository;
import com.admin.repository.RelationTableVersionRepository;
import com.admin.config.DatabaseSchemaResolver;
import com.admin.service.impl.RelationTableDeployServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.RelationFieldDTO;
import com.platform.common.enums.RelationDataType;
import com.platform.common.enums.RelationTableStatus;
import com.platform.security.util.SecurityContextUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * RelationTableDeployServiceImpl 单元测试
 * 测试首次部署（CREATE TABLE）、增量部署（ALTER TABLE ADD/DROP/MODIFY COLUMN）、部署失败回滚、回滚到历史版本
 * 需求: 5.1-5.6
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RelationTableDeployServiceImpl Tests")
class RelationTableDeployServiceTest {

    @Mock
    private RelationTableDefinitionRepository tableDefinitionRepository;

    @Mock
    private RelationTableVersionRepository versionRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private DatabaseSchemaResolver schemaResolver;

    @InjectMocks
    private RelationTableDeployServiceImpl service;

    // ==================== Helper Methods ====================

    private RelationTableDefinition buildTableDefinition(Long id, String tableName, int currentVersion) {
        RelationTableDefinition table = RelationTableDefinition.builder()
                .id(id)
                .tableName(tableName)
                .displayName("Display " + tableName)
                .description("Description for " + tableName)
                .status(RelationTableStatus.DRAFT)
                .enabled(true)
                .portalVisible(false)
                .currentVersion(currentVersion)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .fieldDefinitions(new ArrayList<>())
                .versions(new ArrayList<>())
                .build();
        return table;
    }

    private RelationFieldDefinition buildField(RelationTableDefinition table, String fieldName,
                                                RelationDataType dataType, int sortOrder) {
        return RelationFieldDefinition.builder()
                .id((long) sortOrder + 1)
                .tableDefinition(table)
                .fieldName(fieldName)
                .dataType(dataType)
                .length(dataType == RelationDataType.VARCHAR ? 255 : null)
                .nullable(true)
                .isPrimaryKey(false)
                .sortOrder(sortOrder)
                .build();
    }

    private RelationFieldDefinition buildPrimaryKeyField(RelationTableDefinition table, String fieldName, int sortOrder) {
        return RelationFieldDefinition.builder()
                .id((long) sortOrder + 1)
                .tableDefinition(table)
                .fieldName(fieldName)
                .dataType(RelationDataType.BIGINT)
                .nullable(false)
                .isPrimaryKey(true)
                .sortOrder(sortOrder)
                .build();
    }

    private RelationTableVersion buildVersion(RelationTableDefinition table, int versionNumber, String snapshotData) {
        return RelationTableVersion.builder()
                .id((long) versionNumber)
                .tableDefinition(table)
                .versionNumber(versionNumber)
                .snapshotData(snapshotData)
                .deployedBy("admin")
                .deployedAt(Instant.now())
                .changeLog("Version " + versionNumber)
                .build();
    }

    // ==================== Deploy Tests ====================

    @Nested
    @DisplayName("deploy() - First Deploy (metadata snapshot)")
    class FirstDeployTests {

        @Test
        @DisplayName("Should create version 1 on first deploy without physical DDL")
        void shouldCreateTableOnFirstDeploy() throws JsonProcessingException {
            RelationTableDefinition table = buildTableDefinition(1L, "test_table", 0);
            RelationFieldDefinition pkField = buildPrimaryKeyField(table, "id", 0);
            RelationFieldDefinition nameField = buildField(table, "name", RelationDataType.VARCHAR, 1);
            table.getFieldDefinitions().addAll(List.of(pkField, nameField));

            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(table));
            when(objectMapper.writeValueAsString(any())).thenReturn("[{\"fieldName\":\"id\"},{\"fieldName\":\"name\"}]");
            when(versionRepository.save(any(RelationTableVersion.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(tableDefinitionRepository.save(any(RelationTableDefinition.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            try (MockedStatic<SecurityContextUtils> securityMock = mockStatic(SecurityContextUtils.class)) {
                securityMock.when(SecurityContextUtils::getCurrentUsername)
                        .thenReturn(Optional.of("admin"));

                RelationTableResponse result = service.deploy(1L);

                // JSON 存储模式：部署不执行 CREATE TABLE DDL
                verify(jdbcTemplate, never()).execute(anyString());

                // Verify version was created
                ArgumentCaptor<RelationTableVersion> versionCaptor = ArgumentCaptor.forClass(RelationTableVersion.class);
                verify(versionRepository).save(versionCaptor.capture());
                RelationTableVersion savedVersion = versionCaptor.getValue();
                assertThat(savedVersion.getVersionNumber()).isEqualTo(1);
                assertThat(savedVersion.getDeployedBy()).isEqualTo("admin");
                assertThat(savedVersion.getChangeLog()).isEqualTo("Initial deployment");

                // Verify table status updated
                assertThat(result.getStatus()).isEqualTo(RelationTableStatus.DEPLOYED);
                assertThat(result.getCurrentVersion()).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("Should throw exception when table has no fields")
        void shouldThrowWhenNoFields() {
            RelationTableDefinition table = buildTableDefinition(1L, "empty_table", 0);

            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(table));

            assertThatThrownBy(() -> service.deploy(1L))
                    .isInstanceOf(RelationTableDeploymentException.class)
                    .hasMessageContaining("没有定义任何字段");
        }

        @Test
        @DisplayName("Should throw exception when table not found")
        void shouldThrowWhenTableNotFound() {
            when(tableDefinitionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deploy(999L))
                    .isInstanceOf(RelationTableNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deploy() - Incremental Deploy (metadata only)")
    class IncrementalDeployTests {

        @Test
        @DisplayName("Should create new version snapshot on incremental deploy without DDL")
        void shouldCreateVersionOnIncrementalDeploy() throws JsonProcessingException {
            RelationTableDefinition table = buildTableDefinition(1L, "existing_table", 1);
            table.setStatus(RelationTableStatus.DEPLOYED);

            // Current fields: id (PK), name (VARCHAR), email (VARCHAR) - added email, no age
            RelationFieldDefinition pkField = buildPrimaryKeyField(table, "id", 0);
            RelationFieldDefinition nameField = buildField(table, "name", RelationDataType.VARCHAR, 1);
            RelationFieldDefinition emailField = buildField(table, "email", RelationDataType.VARCHAR, 2);
            table.getFieldDefinitions().addAll(List.of(pkField, nameField, emailField));

            // Previous snapshot had: id, name, age (so email is new, age is dropped)
            List<RelationFieldDTO> previousFields = List.of(
                    RelationFieldDTO.builder().fieldName("id").dataType(RelationDataType.BIGINT).sortOrder(0).build(),
                    RelationFieldDTO.builder().fieldName("name").dataType(RelationDataType.VARCHAR).length(255).sortOrder(1).build(),
                    RelationFieldDTO.builder().fieldName("age").dataType(RelationDataType.INTEGER).sortOrder(2).build()
            );

            RelationTableVersion latestVersion = buildVersion(table, 1, "snapshot_v1");

            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(table));
            when(objectMapper.writeValueAsString(any())).thenReturn("[{\"fieldName\":\"id\"},{\"fieldName\":\"name\"},{\"fieldName\":\"email\"}]");
            when(versionRepository.save(any(RelationTableVersion.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(tableDefinitionRepository.save(any(RelationTableDefinition.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            try (MockedStatic<SecurityContextUtils> securityMock = mockStatic(SecurityContextUtils.class)) {
                securityMock.when(SecurityContextUtils::getCurrentUsername)
                        .thenReturn(Optional.of("admin"));

                RelationTableResponse result = service.deploy(1L);

                verify(jdbcTemplate, never()).execute(anyString());

                // Verify version incremented to 2
                ArgumentCaptor<RelationTableVersion> versionCaptor = ArgumentCaptor.forClass(RelationTableVersion.class);
                verify(versionRepository).save(versionCaptor.capture());
                assertThat(versionCaptor.getValue().getVersionNumber()).isEqualTo(2);
                assertThat(versionCaptor.getValue().getChangeLog()).isEqualTo("Structure update deployment");

                // Verify status and version
                assertThat(result.getStatus()).isEqualTo(RelationTableStatus.DEPLOYED);
                assertThat(result.getCurrentVersion()).isEqualTo(2);
            }
        }
    }

    // Deploy no longer executes DDL — failure tests removed (JSON row storage model)

    // ==================== Rollback Tests ====================

    @Nested
    @DisplayName("rollback() - Rollback to History Version")
    class RollbackTests {

        @Test
        @DisplayName("Should rollback to target version, restore fields, create new version with ROLLBACK status")
        void shouldRollbackToTargetVersion() throws JsonProcessingException {
            RelationTableDefinition table = buildTableDefinition(1L, "rollback_table", 2);
            table.setStatus(RelationTableStatus.DEPLOYED);

            // Current fields
            RelationFieldDefinition currentField = buildField(table, "current_field", RelationDataType.TEXT, 0);
            table.getFieldDefinitions().add(currentField);

            // Target version snapshot (version 1) with different fields
            List<RelationFieldDTO> snapshotFields = List.of(
                    RelationFieldDTO.builder()
                            .fieldName("old_id")
                            .dataType(RelationDataType.BIGINT)
                            .nullable(false)
                            .isPrimaryKey(true)
                            .sortOrder(0)
                            .build(),
                    RelationFieldDTO.builder()
                            .fieldName("old_name")
                            .dataType(RelationDataType.VARCHAR)
                            .length(100)
                            .nullable(true)
                            .isPrimaryKey(false)
                            .displayName("Original name field")
                            .sortOrder(1)
                            .build()
            );

            String snapshotJson = "[{\"fieldName\":\"old_id\"},{\"fieldName\":\"old_name\"}]";
            RelationTableVersion targetVersion = buildVersion(table, 1, snapshotJson);

            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(table));
            when(versionRepository.findById(10L)).thenReturn(Optional.of(targetVersion));
            when(objectMapper.readValue(eq(snapshotJson), any(TypeReference.class))).thenReturn(snapshotFields);
            when(versionRepository.save(any(RelationTableVersion.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(tableDefinitionRepository.save(any(RelationTableDefinition.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            try (MockedStatic<SecurityContextUtils> securityMock = mockStatic(SecurityContextUtils.class)) {
                securityMock.when(SecurityContextUtils::getCurrentUsername)
                        .thenReturn(Optional.of("admin"));

                RollbackRequest request = RollbackRequest.builder().targetVersionId(10L).build();
                RelationTableResponse result = service.rollback(1L, request);

                // Verify field definitions were restored from snapshot
                assertThat(table.getFieldDefinitions()).hasSize(2);
                assertThat(table.getFieldDefinitions().get(0).getFieldName()).isEqualTo("old_id");
                assertThat(table.getFieldDefinitions().get(0).getIsPrimaryKey()).isTrue();
                assertThat(table.getFieldDefinitions().get(1).getFieldName()).isEqualTo("old_name");
                assertThat(table.getFieldDefinitions().get(1).getDataType()).isEqualTo(RelationDataType.VARCHAR);

                // Verify new version was created (version 3)
                ArgumentCaptor<RelationTableVersion> versionCaptor = ArgumentCaptor.forClass(RelationTableVersion.class);
                verify(versionRepository).save(versionCaptor.capture());
                RelationTableVersion rollbackVersion = versionCaptor.getValue();
                assertThat(rollbackVersion.getVersionNumber()).isEqualTo(3);
                assertThat(rollbackVersion.getSnapshotData()).isEqualTo(snapshotJson);
                assertThat(rollbackVersion.getChangeLog()).contains("Rollback to version 1");

                // Verify status is ROLLBACK
                assertThat(result.getStatus()).isEqualTo(RelationTableStatus.ROLLBACK);
                assertThat(result.getCurrentVersion()).isEqualTo(3);
            }
        }

        @Test
        @DisplayName("Should throw exception when target version not found")
        void shouldThrowWhenTargetVersionNotFound() {
            RelationTableDefinition table = buildTableDefinition(1L, "rollback_table", 2);

            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(table));
            when(versionRepository.findById(999L)).thenReturn(Optional.empty());

            RollbackRequest request = RollbackRequest.builder().targetVersionId(999L).build();

            assertThatThrownBy(() -> service.rollback(1L, request))
                    .isInstanceOf(RelationTableDeploymentException.class)
                    .hasMessageContaining("目标版本不存在");
        }

        @Test
        @DisplayName("Should throw exception when target version belongs to different table")
        void shouldThrowWhenVersionBelongsToDifferentTable() {
            RelationTableDefinition table1 = buildTableDefinition(1L, "table_one", 2);
            RelationTableDefinition table2 = buildTableDefinition(2L, "table_two", 1);
            RelationTableVersion versionOfTable2 = buildVersion(table2, 1, "{}");

            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(table1));
            when(versionRepository.findById(5L)).thenReturn(Optional.of(versionOfTable2));

            RollbackRequest request = RollbackRequest.builder().targetVersionId(5L).build();

            assertThatThrownBy(() -> service.rollback(1L, request))
                    .isInstanceOf(RelationTableDeploymentException.class)
                    .hasMessageContaining("不属于表");
        }

        @Test
        @DisplayName("Should throw exception when table not found for rollback")
        void shouldThrowWhenTableNotFoundForRollback() {
            when(tableDefinitionRepository.findById(999L)).thenReturn(Optional.empty());

            RollbackRequest request = RollbackRequest.builder().targetVersionId(1L).build();

            assertThatThrownBy(() -> service.rollback(999L, request))
                    .isInstanceOf(RelationTableNotFoundException.class);
        }
    }

    // ==================== Version History Tests ====================

    @Nested
    @DisplayName("getVersionHistory() Tests")
    class VersionHistoryTests {

        @Test
        @DisplayName("Should return version history ordered by version number desc")
        void shouldReturnVersionHistory() {
            RelationTableDefinition table = buildTableDefinition(1L, "versioned_table", 2);

            RelationTableVersion v2 = buildVersion(table, 2, "snapshot_v2");
            RelationTableVersion v1 = buildVersion(table, 1, "snapshot_v1");

            when(tableDefinitionRepository.existsById(1L)).thenReturn(true);
            when(versionRepository.findByTableDefinitionIdOrderByVersionNumberDesc(1L))
                    .thenReturn(List.of(v2, v1));

            List<RelationTableVersionResponse> result = service.getVersionHistory(1L);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getVersionNumber()).isEqualTo(2);
            assertThat(result.get(1).getVersionNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should throw exception when table not found for version history")
        void shouldThrowWhenTableNotFoundForVersionHistory() {
            when(tableDefinitionRepository.existsById(999L)).thenReturn(false);

            assertThatThrownBy(() -> service.getVersionHistory(999L))
                    .isInstanceOf(RelationTableNotFoundException.class);
        }
    }
}
