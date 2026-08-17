package com.admin.component;

import com.admin.entity.RelationFieldDefinition;
import com.admin.entity.RelationTableDefinition;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.RelationTableDefinitionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Admin Function Unit import must persist relation-table computed columns. Dropping
 * {@code isComputed}/{@code computedField} would make re-import look successful while the
 * portal write path has nothing to evaluate.
 */
@ExtendWith(MockitoExtension.class)
class RelationTableStructureImporterComputedFieldTest {

    @Mock
    private RelationTableDefinitionRepository repository;

    private RelationTableStructureImporter importer;

    @BeforeEach
    void setUp() {
        importer = new RelationTableStructureImporter(repository, new ObjectMapper());
    }

    @Test
    void importRelationTables_persistsComputedFieldJson() {
        when(repository.findByTableName("prices")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> {
            RelationTableDefinition def = invocation.getArgument(0);
            if (def.getId() == null) {
                def.setId(7L);
            }
            return def;
        });
        when(repository.findByIdWithFields(7L)).thenReturn(Optional.empty());

        importer.importRelationTables(List.of(Map.of(
                "tableName", "prices",
                "displayName", "Prices",
                "fields", List.of(Map.of(
                        "fieldName", "amount",
                        "dataType", "DECIMAL",
                        "isComputed", true,
                        "computedField", Map.of("source", "qty * price", "scope", "row"),
                        "sortOrder", 0)))), "tester");

        ArgumentCaptor<RelationTableDefinition> captor =
                ArgumentCaptor.forClass(RelationTableDefinition.class);
        verify(repository).save(captor.capture());
        RelationFieldDefinition field = captor.getValue().getFieldDefinitions().get(0);
        assertEquals("amount", field.getFieldName());
        assertEquals(Boolean.TRUE, field.getIsComputed());
        assertEquals("qty * price", field.getComputedFieldJson().get("source"));
    }

    @Test
    void importRelationTables_rejectsComputedFlagWithoutFormula() {
        when(repository.findByTableName("prices")).thenReturn(Optional.empty());

        AdminBusinessException ex = assertThrows(AdminBusinessException.class, () ->
                importer.importRelationTables(List.of(Map.of(
                        "tableName", "prices",
                        "fields", List.of(Map.of(
                                "fieldName", "amount",
                                "dataType", "DECIMAL",
                                "isComputed", true)))), "tester"));

        assertEquals("COMPUTED_FIELD_IMPORT_INVALID", ex.getErrorCode());
        verify(repository, never()).save(any());
    }

    @Test
    void importRelationTables_acceptsComputedFieldJsonString() {
        when(repository.findByTableName("prices")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> {
            RelationTableDefinition def = invocation.getArgument(0);
            if (def.getId() == null) {
                def.setId(7L);
            }
            return def;
        });
        when(repository.findByIdWithFields(7L)).thenReturn(Optional.empty());

        importer.importRelationTables(List.of(Map.of(
                "tableName", "prices",
                "displayName", "Prices",
                "fields", List.of(Map.of(
                        "fieldName", "amount",
                        "dataType", "DECIMAL",
                        "isComputed", true,
                        "computedField", "{\"source\":\"qty * price\",\"scope\":\"row\"}",
                        "sortOrder", 0)))), "tester");

        ArgumentCaptor<RelationTableDefinition> captor =
                ArgumentCaptor.forClass(RelationTableDefinition.class);
        verify(repository).save(captor.capture());
        RelationFieldDefinition field = captor.getValue().getFieldDefinitions().get(0);
        assertEquals(Boolean.TRUE, field.getIsComputed());
        assertEquals("qty * price", field.getComputedFieldJson().get("source"));
    }
}
