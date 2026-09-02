package com.developer.component;

import com.developer.component.impl.TableDesignComponentImpl;
import com.developer.dto.FieldDefinitionRequest;
import com.developer.dto.TableDefinitionRequest;
import com.developer.entity.FieldDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.entity.TableDefinition;
import com.developer.enums.DataType;
import com.developer.enums.TableType;
import com.developer.repository.*;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Renaming a table's PRIMARY KEY column must rewrite {@code ref_primary_key_fields} on every other
 * table's FK that references it.
 *
 * <p>Regression (Portal To Do, FU "main/subtable/people"): {@code subtable}'s PK was renamed
 * {@code id_idw} → {@code id_idwnn}, but {@code people.sub_task_id} kept
 * {@code refPrimaryKeyFields=["id_idw"]}. Nothing failed loudly — {@code FieldFkPkSyncService}
 * silently skips an FK whose ref PK column cannot be resolved — so at runtime the Portal's FK guard
 * refused every People row Add with "Please create a Meeting record before adding People data",
 * even though the parent row was present and populated.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TableDesignComponent - PK rename cascades into referencing FK ref_primary_key_fields")
class TableDesignPkRenameFkCascadeTest {

    private static final Long FU_ID = 900L;
    private static final Long PARENT_TABLE_ID = 50331L;
    private static final Long CHILD_TABLE_ID = 50333L;
    private static final Long PK_FIELD_ID = 7001L;
    private static final Long PLAIN_FIELD_ID = 7002L;

    @Mock private TableDefinitionRepository tableDefinitionRepository;
    @Mock private FieldDefinitionRepository fieldDefinitionRepository;
    @Mock private ForeignKeyRepository foreignKeyRepository;
    @Mock private FunctionUnitRepository functionUnitRepository;
    @Mock private FormDefinitionRepository formDefinitionRepository;
    @Mock private FormTableBindingRepository formTableBindingRepository;
    @Mock private I18nService i18nService;
    @Mock private com.developer.util.DeveloperWorkstationSequenceSynchronizer sequenceSynchronizer;
    @Mock private com.developer.service.FieldFkPkSyncService fieldFkPkSyncService;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private com.developer.service.MainTableViewService mainTableViewService;

    private TableDesignComponent component;
    private TableDefinition parentTable;
    private TableDefinition childTable;
    private FieldDefinition childFk;

    @BeforeEach
    void setUp() {
        component = new TableDesignComponentImpl(
                tableDefinitionRepository, fieldDefinitionRepository, foreignKeyRepository,
                functionUnitRepository, formDefinitionRepository, formTableBindingRepository,
                i18nService, sequenceSynchronizer, fieldFkPkSyncService,
                new com.developer.service.ComputedFieldValidator(), jdbcTemplate, mainTableViewService);

        FunctionUnit fu = new FunctionUnit();
        fu.setId(FU_ID);

        parentTable = new TableDefinition();
        parentTable.setId(PARENT_TABLE_ID);
        parentTable.setTableName("subtable");
        parentTable.setTableType(TableType.SUB);
        parentTable.setFunctionUnit(fu);
        parentTable.setFieldDefinitions(new ArrayList<>(List.of(
                pk(PK_FIELD_ID, "id_idw"),
                plain(PLAIN_FIELD_ID, "label"))));

        childFk = fkTo(PARENT_TABLE_ID, "sub_task_id", "id_idw");
        childTable = new TableDefinition();
        childTable.setId(CHILD_TABLE_ID);
        childTable.setTableName("people");
        childTable.setTableType(TableType.SUB);
        childTable.setFunctionUnit(fu);
        childTable.setFieldDefinitions(new ArrayList<>(List.of(childFk)));

        when(tableDefinitionRepository.findByIdWithFields(PARENT_TABLE_ID)).thenReturn(Optional.of(parentTable));
        when(tableDefinitionRepository.findById(PARENT_TABLE_ID)).thenReturn(Optional.of(parentTable));
        when(tableDefinitionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tableDefinitionRepository.findByFunctionUnitIdWithFields(FU_ID))
                .thenReturn(List.of(parentTable, childTable));
        when(tableDefinitionRepository.findByFunctionUnitId(FU_ID)).thenReturn(List.of(parentTable, childTable));
        when(formDefinitionRepository.findByFunctionUnitId(anyLong())).thenReturn(List.of());
        when(formTableBindingRepository.findByTableId(anyLong())).thenReturn(List.of());
    }

    private static FieldDefinition pk(Long id, String name) {
        FieldDefinition f = base(id, name);
        f.setIsPrimaryKey(true);
        f.setNullable(false);
        return f;
    }

    private static FieldDefinition plain(Long id, String name) {
        return base(id, name);
    }

    private static FieldDefinition base(Long id, String name) {
        FieldDefinition f = new FieldDefinition();
        f.setId(id);
        f.setFieldName(name);
        f.setDisplayName(name);
        f.setDataType(DataType.VARCHAR);
        f.setLength(64);
        f.setNullable(true);
        return f;
    }

    private static FieldDefinition fkTo(Long refTableId, String fieldName, String refPk) {
        FieldDefinition f = base(9001L, fieldName);
        f.setIsForeignKey(true);
        f.setRefTableId(refTableId);
        f.setRefPrimaryKeyFields(new ArrayList<>(List.of(refPk)));
        return f;
    }

    private static FieldDefinitionRequest req(Long id, String fieldName, boolean primaryKey) {
        FieldDefinitionRequest r = new FieldDefinitionRequest();
        r.setId(id);
        r.setFieldName(fieldName);
        r.setDisplayName(fieldName);
        r.setDataType(DataType.VARCHAR);
        r.setLength(64);
        r.setIsPrimaryKey(primaryKey);
        r.setNullable(!primaryKey);
        return r;
    }

    private static TableDefinitionRequest tableReq(FieldDefinitionRequest... fields) {
        TableDefinitionRequest t = new TableDefinitionRequest();
        t.setTableName("subtable");
        t.setTableType(TableType.SUB);
        t.setFields(new ArrayList<>(List.of(fields)));
        return t;
    }

    @Test
    @DisplayName("renaming the parent PK rewrites the child FK's refPrimaryKeyFields")
    void renamingParentPkRewritesChildFkRef() {
        // After the rename lands on the entity, the PK lookup reports the NEW name.
        when(fieldDefinitionRepository.findByTableDefinitionIdOrderBySortOrderAsc(PARENT_TABLE_ID))
                .thenReturn(List.of(pk(PK_FIELD_ID, "id_idwnn")));

        component.update(PARENT_TABLE_ID, tableReq(
                req(PK_FIELD_ID, "id_idwnn", true),
                req(PLAIN_FIELD_ID, "label", false)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FieldDefinition>> captor = ArgumentCaptor.forClass(List.class);
        verify(fieldDefinitionRepository).saveAll(captor.capture());

        List<FieldDefinition> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getFieldName()).isEqualTo("sub_task_id");
        assertThat(saved.get(0).getRefPrimaryKeyFields())
                .as("child FK must follow the parent PK rename, or the Portal FK guard blocks every Add")
                .containsExactly("id_idwnn");
    }

    @Test
    @DisplayName("renaming a NON-primary-key column leaves referencing FKs untouched")
    void renamingNonPkLeavesFksAlone() {
        when(fieldDefinitionRepository.findByTableDefinitionIdOrderBySortOrderAsc(PARENT_TABLE_ID))
                .thenReturn(List.of(pk(PK_FIELD_ID, "id_idw")));

        // Only the non-PK 'label' column is renamed; nobody's refPrimaryKeyFields can point at it.
        component.update(PARENT_TABLE_ID, tableReq(
                req(PK_FIELD_ID, "id_idw", true),
                req(PLAIN_FIELD_ID, "label_renamed", false)));

        assertThat(childFk.getRefPrimaryKeyFields()).containsExactly("id_idw");
        verify(fieldDefinitionRepository, never()).saveAll(any());
    }
}
