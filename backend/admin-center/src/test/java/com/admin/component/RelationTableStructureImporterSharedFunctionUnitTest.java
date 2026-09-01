package com.admin.component;

import com.admin.entity.FunctionUnit;
import com.admin.entity.RelationFieldDefinition;
import com.admin.entity.RelationTableDefinition;
import com.admin.entity.RelationTableFunctionUnit;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.FunctionUnitRepository;
import com.admin.repository.RelationTableDefinitionRepository;
import com.admin.repository.RelationTableFunctionUnitRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.enums.RelationDataType;
import com.platform.common.enums.RelationTableStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Multiple Function Units may reference the same relation table when the import payload matches
 * the stored structure (shared reuse + append link). Conflicting structure remains fail-closed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RelationTableStructureImporter shared Function Unit import")
class RelationTableStructureImporterSharedFunctionUnitTest {

    private static final String TABLE_NAME = "shared_dict";
    private static final Long TABLE_ID = 10L;
    private static final String FU_A = "fu-a-id";
    private static final String FU_B = "fu-b-id";

    @Mock
    private RelationTableDefinitionRepository repository;
    @Mock
    private RelationTableFunctionUnitRepository relationTableFunctionUnitRepository;
    @Mock
    private FunctionUnitRepository functionUnitRepository;

    private RelationTableStructureImporter importer;

    @BeforeEach
    void setUp() {
        RelationTableFieldMapper relationTableFieldMapper = new RelationTableFieldMapper(repository);
        importer = new RelationTableStructureImporter(
                repository, relationTableFieldMapper, new ObjectMapper(),
                relationTableFunctionUnitRepository, functionUnitRepository);
    }

    private RelationTableDefinition existingTable() {
        RelationTableDefinition table = RelationTableDefinition.builder()
                .id(TABLE_ID)
                .tableName(TABLE_NAME)
                .displayName("Shared Dict")
                .description("Shared")
                .status(RelationTableStatus.DEPLOYED)
                .enabled(true)
                .portalVisible(false)
                .currentVersion(2)
                .fieldDefinitions(new ArrayList<>())
                .build();
        table.getFieldDefinitions().add(RelationFieldDefinition.builder()
                .id(1L)
                .tableDefinition(table)
                .fieldName("code")
                .dataType(RelationDataType.VARCHAR)
                .length(64)
                .nullable(false)
                .isPrimaryKey(true)
                .displayName("Code")
                .isForeignKey(false)
                .fkDisplayMode("readonly")
                .isComputed(false)
                .sortOrder(0)
                .build());
        return table;
    }

    private Map<String, Object> identicalPayload() {
        return Map.of(
                "tableName", TABLE_NAME,
                "displayName", "Shared Dict",
                "description", "Shared",
                "fields", List.of(Map.ofEntries(
                        Map.entry("fieldName", "code"),
                        Map.entry("dataType", "VARCHAR"),
                        Map.entry("length", 64),
                        Map.entry("nullable", false),
                        Map.entry("isPrimaryKey", true),
                        Map.entry("displayName", "Code"),
                        Map.entry("isForeignKey", false),
                        Map.entry("fkDisplayMode", "readonly"),
                        Map.entry("isComputed", false),
                        Map.entry("sortOrder", 0))));
    }

    @Test
    @DisplayName("Second FU with identical structure appends link without upserting fields")
    void secondFuIdenticalStructure_appendsLinkWithoutUpsert() {
        RelationTableDefinition existing = existingTable();
        when(repository.findByTableName(TABLE_NAME)).thenReturn(Optional.of(existing));
        when(relationTableFunctionUnitRepository.findByRelationTableId(TABLE_ID))
                .thenReturn(List.of(RelationTableFunctionUnit.builder()
                        .id("link-a")
                        .relationTableId(TABLE_ID)
                        .functionUnitId(FU_A)
                        .build()));
        when(functionUnitRepository.findById(FU_B)).thenReturn(Optional.of(FunctionUnit.builder().id(FU_B).code("fu-b").build()));
        when(functionUnitRepository.findById(FU_A)).thenReturn(Optional.of(FunctionUnit.builder().id(FU_A).code("fu-a").build()));
        when(relationTableFunctionUnitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        importer.importRelationTables(List.of(identicalPayload()), "tester", FU_B);

        verify(repository, never()).save(any());
        ArgumentCaptor<RelationTableFunctionUnit> linkCaptor = ArgumentCaptor.forClass(RelationTableFunctionUnit.class);
        verify(relationTableFunctionUnitRepository).save(linkCaptor.capture());
        assertThat(linkCaptor.getValue().getFunctionUnitId()).isEqualTo(FU_B);
        assertThat(linkCaptor.getValue().getRelationTableId()).isEqualTo(TABLE_ID);
    }

    @Test
    @DisplayName("Second FU with conflicting structure is rejected")
    void secondFuConflictingStructure_rejected() {
        RelationTableDefinition existing = existingTable();
        when(repository.findByTableName(TABLE_NAME)).thenReturn(Optional.of(existing));
        when(relationTableFunctionUnitRepository.findByRelationTableId(TABLE_ID))
                .thenReturn(List.of(RelationTableFunctionUnit.builder()
                        .id("link-a")
                        .relationTableId(TABLE_ID)
                        .functionUnitId(FU_A)
                        .build()));
        when(functionUnitRepository.findById(FU_B)).thenReturn(Optional.of(FunctionUnit.builder().id(FU_B).code("fu-b").build()));
        when(functionUnitRepository.findById(FU_A)).thenReturn(Optional.of(FunctionUnit.builder().id(FU_A).code("fu-a").build()));

        Map<String, Object> conflicting = Map.of(
                "tableName", TABLE_NAME,
                "displayName", "Shared Dict",
                "description", "Shared",
                "fields", List.of(Map.ofEntries(
                        Map.entry("fieldName", "code"),
                        Map.entry("dataType", "VARCHAR"),
                        Map.entry("length", 64),
                        Map.entry("nullable", false),
                        Map.entry("isPrimaryKey", true),
                        Map.entry("displayName", "Code (changed)"),
                        Map.entry("isForeignKey", false),
                        Map.entry("fkDisplayMode", "readonly"),
                        Map.entry("isComputed", false),
                        Map.entry("sortOrder", 0))));

        assertThatThrownBy(() -> importer.importRelationTables(List.of(conflicting), "tester", FU_B))
                .isInstanceOf(AdminBusinessException.class)
                .satisfies(ex -> assertThat(((AdminBusinessException) ex).getErrorCode())
                        .isEqualTo("FU_IMPORT_RT_BOUND_OTHER_FU"));
        verify(repository, never()).save(any());
        verify(relationTableFunctionUnitRepository, never()).save(any());
    }
}
