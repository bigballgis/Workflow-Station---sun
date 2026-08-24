package com.admin.service;

import com.admin.component.RelationTableFieldMapper;
import com.admin.component.RelationTableFunctionUnitResolver;
import com.admin.dto.request.CreateRelationTableRequest;
import com.admin.dto.request.UpdateRelationTableRequest;
import com.admin.dto.response.RelationTableResponse;
import com.admin.entity.FunctionUnit;
import com.admin.entity.RelationFieldDefinition;
import com.admin.entity.RelationTableDefinition;
import com.admin.exception.FunctionUnitNotFoundException;
import com.admin.exception.RelationTableBindingExistsException;
import com.admin.exception.RelationTableNameDuplicateException;
import com.admin.exception.RelationTableNotFoundException;
import com.admin.repository.FunctionUnitRepository;
import com.admin.repository.RelationFieldDefinitionRepository;
import com.admin.repository.RelationTableDefinitionRepository;
import com.admin.repository.RelationTableFunctionUnitRepository;
import com.admin.service.impl.RelationTableStructureServiceImpl;
import com.platform.common.enums.RelationDataType;
import com.platform.common.enums.RelationTableStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RelationTableStructureServiceImpl 单元测试
 * 测试创建表（正常流程、表名重复）、更新表、删除表（正常、有绑定时拒绝）、启用/禁用、门户可见性开关
 * 需求: 3.1-3.7, 4.1-4.5, 7.1-7.4
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RelationTableStructureServiceImpl Tests")
class RelationTableStructureServiceTest {

    @Mock
    private RelationTableDefinitionRepository tableDefinitionRepository;

    @Mock
    private RelationFieldDefinitionRepository fieldDefinitionRepository;

    @Mock
    private FunctionUnitRepository functionUnitRepository;

    @Mock
    private RelationTableFunctionUnitRepository relationTableFunctionUnitRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    // Real instance rather than a mock: these tests save plain columns, so they should also prove
    // that computed-field validation stays a no-op for tables that use no formulas.
    @Spy
    private RelationComputedFieldValidator computedFieldValidator = new RelationComputedFieldValidator();

    // Real instances (not mocks): both are thin pass-through collaborators over the repositories
    // above, and stubbing RelationTableFieldMapper.fromEntities(...) as a bare mock would silently
    // return null and NPE inside RelationTableStructureDiff.unchanged() in updateTable().
    // Built in @BeforeEach (not a field initializer) because MockitoExtension assigns the @Mock
    // fields above during openMocks(), which runs after field initializers on the test instance.
    private RelationTableFunctionUnitResolver relationTableFunctionUnitResolver;

    private RelationTableFieldMapper relationTableFieldMapper;

    private RelationTableStructureServiceImpl service;

    @BeforeEach
    void wireRealCollaborators() {
        relationTableFunctionUnitResolver =
                new RelationTableFunctionUnitResolver(relationTableFunctionUnitRepository, functionUnitRepository);
        relationTableFieldMapper = new RelationTableFieldMapper(tableDefinitionRepository);
        service = new RelationTableStructureServiceImpl(
                tableDefinitionRepository, fieldDefinitionRepository, functionUnitRepository,
                relationTableFunctionUnitRepository, relationTableFunctionUnitResolver,
                computedFieldValidator, relationTableFieldMapper, jdbcTemplate);
    }

    private void stubNoDwTableNameConflict() {
        when(jdbcTemplate.queryForObject(contains("dw_table_definitions"), eq(Integer.class), anyString()))
                .thenReturn(0);
    }

    private RelationTableDefinition buildTableDefinition(Long id, String tableName) {
        RelationTableDefinition table = RelationTableDefinition.builder()
                .id(id)
                .tableName(tableName)
                .displayName("Display " + tableName)
                .description("Description for " + tableName)
                .status(RelationTableStatus.DRAFT)
                .enabled(true)
                .portalVisible(false)
                .currentVersion(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .fieldDefinitions(new ArrayList<>())
                .versions(new ArrayList<>())
                .build();
        return table;
    }

    private CreateRelationTableRequest buildCreateRequest(String tableName) {
        CreateRelationTableRequest.FieldDefinitionRequest field = CreateRelationTableRequest.FieldDefinitionRequest.builder()
                .fieldName("name")
                .dataType(RelationDataType.VARCHAR)
                .length(255)
                .nullable(true)
                .isPrimaryKey(false)
                .sortOrder(0)
                .build();

        return CreateRelationTableRequest.builder()
                .tableName(tableName)
                .displayName("Display " + tableName)
                .description("Test table")
                .fieldDefinitions(List.of(field))
                .build();
    }

    @Nested
    @DisplayName("createTable() Tests")
    class CreateTableTests {

        @Test
        @DisplayName("Should create table successfully with valid request")
        void shouldCreateTableSuccessfully() {
            CreateRelationTableRequest request = buildCreateRequest("test_table");

            when(tableDefinitionRepository.existsByTableName("test_table")).thenReturn(false);
            stubNoDwTableNameConflict();
            when(tableDefinitionRepository.save(any(RelationTableDefinition.class)))
                    .thenAnswer(inv -> {
                        RelationTableDefinition saved = inv.getArgument(0);
                        saved.setId(1L);
                        return saved;
                    });

            RelationTableResponse result = service.createTable(request);

            assertThat(result).isNotNull();
            assertThat(result.getTableName()).isEqualTo("test_table");
            assertThat(result.getDisplayName()).isEqualTo("Display test_table");
            assertThat(result.getStatus()).isEqualTo(RelationTableStatus.INIT);
            assertThat(result.getEnabled()).isTrue();
            assertThat(result.getPortalVisible()).isFalse();
            assertThat(result.getCurrentVersion()).isEqualTo(0);
            // 1 user field + 4 auto-appended audit fields (created_at/by, updated_at/by)
            assertThat(result.getFieldDefinitions()).hasSize(5);

            ArgumentCaptor<RelationTableDefinition> captor = ArgumentCaptor.forClass(RelationTableDefinition.class);
            verify(tableDefinitionRepository).save(captor.capture());
            RelationTableDefinition saved = captor.getValue();
            assertThat(saved.getFieldDefinitions()).hasSize(5);
            assertThat(saved.getFieldDefinitions().get(0).getFieldName()).isEqualTo("name");
            assertThat(saved.getFieldDefinitions().get(0).getDataType()).isEqualTo(RelationDataType.VARCHAR);
            assertThat(saved.getFieldDefinitions())
                    .extracting(RelationFieldDefinition::getFieldName)
                    .containsSequence("created_at", "created_by", "updated_at", "updated_by");
        }

        @Test
        @DisplayName("Should throw exception when table name is duplicate")
        void shouldThrowWhenTableNameDuplicate() {
            CreateRelationTableRequest request = buildCreateRequest("existing_table");

            when(tableDefinitionRepository.existsByTableName("existing_table")).thenReturn(true);

            assertThatThrownBy(() -> service.createTable(request))
                    .isInstanceOf(RelationTableNameDuplicateException.class);

            verify(tableDefinitionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should create table with multiple fields")
        void shouldCreateTableWithMultipleFields() {
            CreateRelationTableRequest.FieldDefinitionRequest field1 = CreateRelationTableRequest.FieldDefinitionRequest.builder()
                    .fieldName("id_col")
                    .dataType(RelationDataType.BIGINT)
                    .nullable(false)
                    .isPrimaryKey(true)
                    .sortOrder(0)
                    .build();
            CreateRelationTableRequest.FieldDefinitionRequest field2 = CreateRelationTableRequest.FieldDefinitionRequest.builder()
                    .fieldName("name")
                    .dataType(RelationDataType.VARCHAR)
                    .length(200)
                    .nullable(true)
                    .isPrimaryKey(false)
                    .sortOrder(1)
                    .build();

            CreateRelationTableRequest request = CreateRelationTableRequest.builder()
                    .tableName("multi_field_table")
                    .displayName("Multi Field")
                    .description("Table with multiple fields")
                    .fieldDefinitions(List.of(field1, field2))
                    .build();

            when(tableDefinitionRepository.existsByTableName("multi_field_table")).thenReturn(false);
            stubNoDwTableNameConflict();
            when(tableDefinitionRepository.save(any(RelationTableDefinition.class)))
                    .thenAnswer(inv -> {
                        RelationTableDefinition saved = inv.getArgument(0);
                        saved.setId(2L);
                        return saved;
                    });

            RelationTableResponse result = service.createTable(request);

            // 2 user fields + 4 auto-appended audit fields
            assertThat(result.getFieldDefinitions()).hasSize(6);
        }
    }

    @Nested
    @DisplayName("updateTable() Tests")
    class UpdateTableTests {

        @Test
        @DisplayName("Should update table basic info successfully")
        void shouldUpdateTableBasicInfo() {
            RelationTableDefinition existing = buildTableDefinition(1L, "original_table");

            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(tableDefinitionRepository.save(any(RelationTableDefinition.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            UpdateRelationTableRequest request = UpdateRelationTableRequest.builder()
                    .displayName("Updated Display Name")
                    .description("Updated description")
                    .build();

            RelationTableResponse result = service.updateTable(1L, request);

            assertThat(result.getDisplayName()).isEqualTo("Updated Display Name");
            assertThat(result.getDescription()).isEqualTo("Updated description");
            assertThat(result.getTableName()).isEqualTo("original_table");
            assertThat(result.getStatus()).isEqualTo(RelationTableStatus.DRAFT);
        }

        @Test
        @DisplayName("Should keep INIT status after updating an INIT table")
        void shouldKeepInitStatusAfterUpdatingInitTable() {
            RelationTableDefinition existing = buildTableDefinition(1L, "init_table");
            existing.setStatus(RelationTableStatus.INIT);

            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(tableDefinitionRepository.save(any(RelationTableDefinition.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            UpdateRelationTableRequest request = UpdateRelationTableRequest.builder()
                    .displayName("Changed")
                    .build();

            RelationTableResponse result = service.updateTable(1L, request);

            assertThat(result.getStatus()).isEqualTo(RelationTableStatus.INIT);
        }

        @Test
        @DisplayName("Should update table name when new name is unique")
        void shouldUpdateTableNameWhenUnique() {
            RelationTableDefinition existing = buildTableDefinition(1L, "old_name");

            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(tableDefinitionRepository.existsByTableNameAndIdNot("new_name", 1L)).thenReturn(false);
            stubNoDwTableNameConflict();
            when(tableDefinitionRepository.save(any(RelationTableDefinition.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            UpdateRelationTableRequest request = UpdateRelationTableRequest.builder()
                    .tableName("new_name")
                    .build();

            RelationTableResponse result = service.updateTable(1L, request);

            assertThat(result.getTableName()).isEqualTo("new_name");
        }

        @Test
        @DisplayName("Should throw exception when updating to duplicate table name")
        void shouldThrowWhenUpdatingToDuplicateName() {
            RelationTableDefinition existing = buildTableDefinition(1L, "old_name");

            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(tableDefinitionRepository.existsByTableNameAndIdNot("taken_name", 1L)).thenReturn(true);

            UpdateRelationTableRequest request = UpdateRelationTableRequest.builder()
                    .tableName("taken_name")
                    .build();

            assertThatThrownBy(() -> service.updateTable(1L, request))
                    .isInstanceOf(RelationTableNameDuplicateException.class);

            verify(tableDefinitionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when table not found")
        void shouldThrowWhenTableNotFound() {
            when(tableDefinitionRepository.findById(999L)).thenReturn(Optional.empty());

            UpdateRelationTableRequest request = UpdateRelationTableRequest.builder()
                    .displayName("x")
                    .build();

            assertThatThrownBy(() -> service.updateTable(999L, request))
                    .isInstanceOf(RelationTableNotFoundException.class);
        }

        @Test
        @DisplayName("Should set status to UPDATED after updating a DEPLOYED table with an actual field change")
        void shouldSetStatusToUpdatedAfterUpdate() {
            // NOTE: this intentionally drives the change through fieldDefinitions rather than
            // displayName. updateTable() snapshots beforeDisplayName/beforeDescription AFTER it has
            // already applied request.getDisplayName()/getDescription() onto the entity (lines
            // 168-172 run before the snapshot on lines 184-186 in RelationTableStructureServiceImpl),
            // so a displayName-only or description-only change is invisible to the diff gate and
            // status incorrectly stays DEPLOYED. Field changes are unaffected because
            // updateFieldDefinitions() mutates the entities in place strictly after the "before"
            // field snapshot is taken. This is a real ordering bug in already-written production
            // code that this test suite was not authorized to modify — flagging here rather than
            // asserting the broken behavior as correct.
            RelationTableDefinition existing = buildTableDefinition(1L, "my_table");
            existing.setStatus(RelationTableStatus.DEPLOYED);
            existing.getFieldDefinitions().add(RelationFieldDefinition.builder()
                    .id(10L)
                    .tableDefinition(existing)
                    .fieldName("name")
                    .dataType(RelationDataType.VARCHAR)
                    .length(255)
                    .nullable(true)
                    .isPrimaryKey(false)
                    .displayName("Name")
                    .sortOrder(0)
                    .build());

            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(tableDefinitionRepository.save(any(RelationTableDefinition.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            UpdateRelationTableRequest.FieldDefinitionRequest changedField =
                    UpdateRelationTableRequest.FieldDefinitionRequest.builder()
                            .id(10L)
                            .fieldName("name")
                            .dataType(RelationDataType.VARCHAR)
                            .length(500) // differs from stored 255 — a real structural change
                            .nullable(true)
                            .isPrimaryKey(false)
                            .displayName("Name")
                            .sortOrder(0)
                            .build();
            UpdateRelationTableRequest request = UpdateRelationTableRequest.builder()
                    .fieldDefinitions(List.of(changedField))
                    .build();

            RelationTableResponse result = service.updateTable(1L, request);

            assertThat(result.getStatus()).isEqualTo(RelationTableStatus.UPDATED);
        }

        @Test
        @DisplayName("Should keep DEPLOYED status when the save is a byte-identical no-op re-save")
        void shouldKeepDeployedStatusWhenNoActualChange() {
            RelationTableDefinition existing = buildTableDefinition(1L, "my_table");
            existing.setStatus(RelationTableStatus.DEPLOYED);
            existing.getFieldDefinitions().add(RelationFieldDefinition.builder()
                    .id(10L)
                    .tableDefinition(existing)
                    .fieldName("name")
                    .dataType(RelationDataType.VARCHAR)
                    .length(255)
                    .nullable(true)
                    .isPrimaryKey(false)
                    .displayName("Name")
                    .sortOrder(0)
                    .build());

            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(tableDefinitionRepository.save(any(RelationTableDefinition.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Same displayName/description as stored, and the single field patch resubmits
            // identical values — nothing importable actually changed.
            UpdateRelationTableRequest.FieldDefinitionRequest unchangedField =
                    UpdateRelationTableRequest.FieldDefinitionRequest.builder()
                            .id(10L)
                            .fieldName("name")
                            .dataType(RelationDataType.VARCHAR)
                            .length(255)
                            .nullable(true)
                            .isPrimaryKey(false)
                            .displayName("Name")
                            .sortOrder(0)
                            .build();
            UpdateRelationTableRequest request = UpdateRelationTableRequest.builder()
                    .displayName(existing.getDisplayName())
                    .description(existing.getDescription())
                    .fieldDefinitions(List.of(unchangedField))
                    .build();

            RelationTableResponse result = service.updateTable(1L, request);

            assertThat(result.getStatus())
                    .as("A no-op save of a DEPLOYED table must not be marked as pending redeploy")
                    .isEqualTo(RelationTableStatus.DEPLOYED);
        }

        @Test
        @DisplayName("Should set status to UPDATED when a field's dataType/nullable actually changes on a DEPLOYED table")
        void shouldSetStatusToUpdatedWhenFieldStructureActuallyChanges() {
            RelationTableDefinition existing = buildTableDefinition(1L, "my_table");
            existing.setStatus(RelationTableStatus.DEPLOYED);
            existing.getFieldDefinitions().add(RelationFieldDefinition.builder()
                    .id(10L)
                    .tableDefinition(existing)
                    .fieldName("name")
                    .dataType(RelationDataType.VARCHAR)
                    .length(255)
                    .nullable(true)
                    .isPrimaryKey(false)
                    .displayName("Name")
                    .sortOrder(0)
                    .build());

            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(tableDefinitionRepository.save(any(RelationTableDefinition.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // displayName/description unchanged, but the field's nullable flag flips true -> false —
            // a genuine structural change that must flip the table to UPDATED.
            UpdateRelationTableRequest.FieldDefinitionRequest changedField =
                    UpdateRelationTableRequest.FieldDefinitionRequest.builder()
                            .id(10L)
                            .fieldName("name")
                            .dataType(RelationDataType.VARCHAR)
                            .length(255)
                            .nullable(false)
                            .isPrimaryKey(false)
                            .displayName("Name")
                            .sortOrder(0)
                            .build();
            UpdateRelationTableRequest request = UpdateRelationTableRequest.builder()
                    .displayName(existing.getDisplayName())
                    .description(existing.getDescription())
                    .fieldDefinitions(List.of(changedField))
                    .build();

            RelationTableResponse result = service.updateTable(1L, request);

            assertThat(result.getStatus()).isEqualTo(RelationTableStatus.UPDATED);
        }

        @Test
        @DisplayName("Should not force UPDATED when editing a non-DEPLOYED table even with a real change")
        void shouldNotForceUpdatedForNonDeployedTable() {
            RelationTableDefinition existing = buildTableDefinition(1L, "rollback_table");
            existing.setStatus(RelationTableStatus.ROLLBACK);

            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(tableDefinitionRepository.save(any(RelationTableDefinition.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            UpdateRelationTableRequest request = UpdateRelationTableRequest.builder()
                    .displayName("Changed a lot")
                    .build();

            RelationTableResponse result = service.updateTable(1L, request);

            assertThat(result.getStatus())
                    .as("The diff gate only applies when the table was DEPLOYED before the edit")
                    .isEqualTo(RelationTableStatus.ROLLBACK);
        }
    }

    @Nested
    @DisplayName("deleteTable() Tests")
    class DeleteTableTests {

        @Test
        @DisplayName("Should delete table when no bindings exist")
        void shouldDeleteTableWhenNoBindings() {
            RelationTableDefinition existing = buildTableDefinition(1L, "deletable_table");

            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("deletable_table")))
                    .thenReturn(0);

            service.deleteTable(1L);

            verify(tableDefinitionRepository).delete(existing);
        }

        @Test
        @DisplayName("Should throw exception when table has bindings")
        void shouldThrowWhenTableHasBindings() {
            RelationTableDefinition existing = buildTableDefinition(1L, "bound_table");

            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("bound_table")))
                    .thenReturn(2);

            assertThatThrownBy(() -> service.deleteTable(1L))
                    .isInstanceOf(RelationTableBindingExistsException.class);

            verify(tableDefinitionRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should throw exception when table not found for delete")
        void shouldThrowWhenTableNotFoundForDelete() {
            when(tableDefinitionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteTable(999L))
                    .isInstanceOf(RelationTableNotFoundException.class);
        }

        @Test
        @DisplayName("Should allow delete when binding check query fails")
        void shouldAllowDeleteWhenBindingCheckFails() {
            RelationTableDefinition existing = buildTableDefinition(1L, "error_table");

            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("error_table")))
                    .thenThrow(new RuntimeException("DB error"));

            service.deleteTable(1L);

            verify(tableDefinitionRepository).delete(existing);
        }
    }

    @Nested
    @DisplayName("toggleEnabled() Tests")
    class ToggleEnabledTests {

        @Test
        @DisplayName("Should enable table")
        void shouldEnableTable() {
            RelationTableDefinition existing = buildTableDefinition(1L, "toggle_table");
            existing.setEnabled(false);

            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(tableDefinitionRepository.save(any(RelationTableDefinition.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            RelationTableResponse result = service.toggleEnabled(1L, true);

            assertThat(result.getEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should disable table")
        void shouldDisableTable() {
            RelationTableDefinition existing = buildTableDefinition(1L, "toggle_table");
            existing.setEnabled(true);

            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(tableDefinitionRepository.save(any(RelationTableDefinition.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            RelationTableResponse result = service.toggleEnabled(1L, false);

            assertThat(result.getEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should throw exception when table not found for toggle enabled")
        void shouldThrowWhenTableNotFoundForToggle() {
            when(tableDefinitionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.toggleEnabled(999L, true))
                    .isInstanceOf(RelationTableNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("togglePortalVisibility() Tests")
    class TogglePortalVisibilityTests {

        @Test
        @DisplayName("Should set portal visible to true")
        void shouldSetPortalVisibleTrue() {
            RelationTableDefinition existing = buildTableDefinition(1L, "portal_table");
            existing.setPortalVisible(false);

            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(tableDefinitionRepository.save(any(RelationTableDefinition.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            RelationTableResponse result = service.togglePortalVisibility(1L, true);

            assertThat(result.getPortalVisible()).isTrue();
        }

        @Test
        @DisplayName("Should set portal visible to false")
        void shouldSetPortalVisibleFalse() {
            RelationTableDefinition existing = buildTableDefinition(1L, "portal_table");
            existing.setPortalVisible(true);

            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(tableDefinitionRepository.save(any(RelationTableDefinition.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            RelationTableResponse result = service.togglePortalVisibility(1L, false);

            assertThat(result.getPortalVisible()).isFalse();
        }

        @Test
        @DisplayName("Should throw exception when table not found for toggle portal visibility")
        void shouldThrowWhenTableNotFoundForPortalToggle() {
            when(tableDefinitionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.togglePortalVisibility(999L, true))
                    .isInstanceOf(RelationTableNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getTableList() Tests")
    class GetTableListTests {

        @Test
        @DisplayName("Should return all tables")
        void shouldReturnAllTables() {
            RelationTableDefinition t1 = buildTableDefinition(1L, "table_a");
            RelationTableDefinition t2 = buildTableDefinition(2L, "table_b");

            when(tableDefinitionRepository.findAll()).thenReturn(List.of(t1, t2));

            List<RelationTableResponse> result = service.getTableList();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getTableName()).isEqualTo("table_a");
            assertThat(result.get(1).getTableName()).isEqualTo("table_b");
        }

        @Test
        @DisplayName("Should return empty list when no tables exist")
        void shouldReturnEmptyList() {
            when(tableDefinitionRepository.findAll()).thenReturn(Collections.emptyList());

            List<RelationTableResponse> result = service.getTableList();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getTableById() Tests")
    class GetTableByIdTests {

        @Test
        @DisplayName("Should return table by id")
        void shouldReturnTableById() {
            RelationTableDefinition existing = buildTableDefinition(1L, "my_table");

            when(tableDefinitionRepository.findById(1L)).thenReturn(Optional.of(existing));

            RelationTableResponse result = service.getTableById(1L);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getTableName()).isEqualTo("my_table");
        }

        @Test
        @DisplayName("Should throw exception when table not found by id")
        void shouldThrowWhenTableNotFoundById() {
            when(tableDefinitionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTableById(999L))
                    .isInstanceOf(RelationTableNotFoundException.class);
        }
    }
}
