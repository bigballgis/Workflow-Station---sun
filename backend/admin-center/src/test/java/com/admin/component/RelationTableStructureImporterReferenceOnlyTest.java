package com.admin.component;

import com.admin.entity.RelationFieldDefinition;
import com.admin.entity.RelationTableDefinition;
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
 * A foreign key's parent table travels in a package only so the release snapshot is complete: the importing
 * Function Unit references it and never updates or claims it; an existing one must have the same columns.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RelationTableStructureImporter reference-only tables")
class RelationTableStructureImporterReferenceOnlyTest {

    private static final String TABLE_NAME = "shared_dict";
    private static final Long TABLE_ID = 10L;
    private static final Long SOURCE_ID = 99L;
    private static final String FU = "fu-x-id";

    @Mock
    private RelationTableDefinitionRepository repository;
    @Mock
    private RelationTableFunctionUnitRepository relationTableFunctionUnitRepository;
    @Mock
    private FunctionUnitRepository functionUnitRepository;

    private RelationTableStructureImporter importer;

    @BeforeEach
    void setUp() {
        importer = new RelationTableStructureImporter(
                repository, new RelationTableFieldMapper(repository), new ObjectMapper(),
                relationTableFunctionUnitRepository, functionUnitRepository);
    }

    private RelationTableDefinition existingTable() {
        RelationTableDefinition table = RelationTableDefinition.builder()
                .id(TABLE_ID).tableName(TABLE_NAME).displayName("Shared Dict").description("Shared")
                .status(RelationTableStatus.DEPLOYED).enabled(true).portalVisible(false).currentVersion(2)
                .fieldDefinitions(new ArrayList<>()).build();
        table.getFieldDefinitions().add(RelationFieldDefinition.builder()
                .id(1L).tableDefinition(table).fieldName("code").dataType(RelationDataType.VARCHAR).length(64)
                .nullable(false).isPrimaryKey(true).displayName("Code").isForeignKey(false)
                .fkDisplayMode("readonly").isComputed(false).sortOrder(0).build());
        return table;
    }

    private Map<String, Object> referencePayload(String displayName, String fieldDisplayName) {
        return referencePayload(displayName, List.of(codeField(fieldDisplayName)));
    }

    private Map<String, Object> referencePayload(String displayName, List<Map<String, Object>> fields) {
        return Map.of(
                "relationTableId", SOURCE_ID,
                "tableName", TABLE_NAME,
                "displayName", displayName,
                "description", "Shared",
                RelationTableStructureImporter.REFERENCE_ONLY, true,
                "fields", fields);
    }

    private static Map<String, Object> codeField(String displayName) {
        return Map.ofEntries(
                Map.entry("fieldName", "code"),
                Map.entry("dataType", "VARCHAR"),
                Map.entry("length", 64),
                Map.entry("nullable", false),
                Map.entry("isPrimaryKey", true),
                Map.entry("displayName", displayName),
                Map.entry("isForeignKey", false),
                Map.entry("fkDisplayMode", "readonly"),
                Map.entry("isComputed", false),
                Map.entry("sortOrder", 0));
    }

    @Test
    @DisplayName("Existing table with the same columns is used as is: no update, no link")
    void existingSameColumns_usedWithoutUpdateOrLink() {
        when(repository.findByTableName(TABLE_NAME)).thenReturn(Optional.of(existingTable()));

        Map<Long, Long> ids = importer.importRelationTables(
                List.of(referencePayload("Renamed by its owner", "Code")), "tester", FU);

        assertThat(ids).containsExactly(Map.entry(SOURCE_ID, TABLE_ID));
        verify(repository, never()).save(any());
        verify(relationTableFunctionUnitRepository, never()).save(any());
    }

    @Test
    @DisplayName("Existing table with different columns fails the import")
    void existingDifferentColumns_rejected() {
        when(repository.findByTableName(TABLE_NAME)).thenReturn(Optional.of(existingTable()));

        assertThatThrownBy(() -> importer.importRelationTables(
                List.of(referencePayload("Shared Dict", "Code (changed)")), "tester", FU))
                .isInstanceOf(AdminBusinessException.class)
                .satisfies(ex -> assertThat(((AdminBusinessException) ex).getErrorCode())
                        .isEqualTo("FU_IMPORT_RT_REFERENCE_MISMATCH"));
        verify(repository, never()).save(any());
        verify(relationTableFunctionUnitRepository, never()).save(any());
    }

    @Test
    @DisplayName("A LOOKUP column pointing at the same table by another environment's id is still the same column")
    void lookupTableIdOfAnotherEnvironmentIsNotAStructureDifference() {
        RelationTableDefinition existing = existingTable();
        existing.getFieldDefinitions().add(RelationFieldDefinition.builder()
                .id(2L).tableDefinition(existing).fieldName("category").dataType(RelationDataType.LOOKUP)
                .nullable(true).isPrimaryKey(false).displayName("Category").isForeignKey(false)
                .fkDisplayMode("readonly").isComputed(false).sortOrder(1)
                .lookupConfig(Map.of("refTableId", 812, "searchFields", List.of("name"))).build());
        when(repository.findByTableName(TABLE_NAME)).thenReturn(Optional.of(existing));
        Map<String, Object> payload = referencePayload("Shared Dict", List.of(codeField("Code"), Map.ofEntries(
                Map.entry("fieldName", "category"),
                Map.entry("dataType", "LOOKUP"),
                Map.entry("nullable", true),
                Map.entry("isPrimaryKey", false),
                Map.entry("displayName", "Category"),
                Map.entry("isForeignKey", false),
                Map.entry("fkDisplayMode", "readonly"),
                Map.entry("isComputed", false),
                Map.entry("sortOrder", 1),
                Map.entry("lookupConfig", Map.of("refTableId", 47, "searchFields", List.of("name"))))));

        assertThat(importer.importRelationTables(List.of(payload), "tester", FU))
                .containsExactly(Map.entry(SOURCE_ID, TABLE_ID));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Missing table is created but not linked to the importing Function Unit")
    void missing_createdWithoutLink() {
        when(repository.findByTableName(TABLE_NAME)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> {
            RelationTableDefinition created = inv.getArgument(0);
            created.setId(TABLE_ID);
            return created;
        });

        Map<Long, Long> ids = importer.importRelationTables(
                List.of(referencePayload("Shared Dict", "Code")), "tester", FU);

        assertThat(ids).containsExactly(Map.entry(SOURCE_ID, TABLE_ID));
        verify(repository).save(any());
        verify(relationTableFunctionUnitRepository, never()).save(any());
    }
}
