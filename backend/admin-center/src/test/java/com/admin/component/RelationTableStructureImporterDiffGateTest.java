package com.admin.component;

import com.admin.entity.RelationFieldDefinition;
import com.admin.entity.RelationTableDefinition;
import com.admin.repository.RelationTableDefinitionRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Re-importing an existing relation table (update branch of {@link RelationTableStructureImporter})
 * must be a no-op for status/version when the incoming structure is byte-identical to what's
 * stored, and must flip to UPDATED + bump the version when it actually differs. Covers the "new
 * table" create branch separately in {@code RelationTableStructureImporterComputedFieldTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RelationTableStructureImporter update-branch diff gate")
class RelationTableStructureImporterDiffGateTest {

    @Mock
    private RelationTableDefinitionRepository repository;

    private RelationTableStructureImporter importer;

    @BeforeEach
    void setUp() {
        RelationTableFieldMapper relationTableFieldMapper = new RelationTableFieldMapper(repository);
        importer = new RelationTableStructureImporter(repository, relationTableFieldMapper, new ObjectMapper());
    }

    private RelationTableDefinition existingTable(String displayName, String description) {
        RelationTableDefinition table = RelationTableDefinition.builder()
                .id(5L)
                .tableName("orders")
                .displayName(displayName)
                .description(description)
                .status(RelationTableStatus.DEPLOYED)
                .enabled(true)
                .portalVisible(false)
                .currentVersion(3)
                .fieldDefinitions(new ArrayList<>())
                .build();
        table.getFieldDefinitions().add(RelationFieldDefinition.builder()
                .id(100L)
                .tableDefinition(table)
                .fieldName("amount")
                .dataType(RelationDataType.DECIMAL)
                .length(10)
                .precision(10)
                .scale(2)
                .nullable(true)
                .isPrimaryKey(false)
                .displayName("Amount")
                .isForeignKey(false)
                .fkDisplayMode("readonly")
                .isComputed(false)
                .sortOrder(0)
                .build());
        return table;
    }

    private Map<String, Object> payloadField(String displayName) {
        return Map.ofEntries(
                Map.entry("fieldName", "amount"),
                Map.entry("dataType", "DECIMAL"),
                Map.entry("length", 10),
                Map.entry("precision", 10),
                Map.entry("scale", 2),
                Map.entry("nullable", true),
                Map.entry("isPrimaryKey", false),
                Map.entry("displayName", displayName),
                Map.entry("isForeignKey", false),
                Map.entry("fkDisplayMode", "readonly"),
                Map.entry("isComputed", false),
                Map.entry("sortOrder", 0));
    }

    @Test
    @DisplayName("Re-importing byte-identical content leaves status and version untouched")
    void reimportingIdenticalContent_leavesStatusAndVersionUnchanged() {
        RelationTableDefinition existing = existingTable("Orders", "Order records");
        when(repository.findByTableName("orders")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findByIdWithFields(5L)).thenReturn(Optional.empty());

        Map<String, Object> table = Map.of(
                "tableName", "orders",
                "displayName", "Orders",
                "description", "Order records",
                "fields", List.of(payloadField("Amount")));

        importer.importRelationTables(List.of(table), "tester");

        ArgumentCaptor<RelationTableDefinition> captor = ArgumentCaptor.forClass(RelationTableDefinition.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        RelationTableDefinition saved = captor.getValue();

        assertThat(saved.getStatus())
                .as("A no-op re-import of a DEPLOYED table must not be marked as pending redeploy")
                .isEqualTo(RelationTableStatus.DEPLOYED);
        assertThat(saved.getCurrentVersion())
                .as("Version must not bump when nothing importable actually changed")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("Re-importing with an actual field difference flips to UPDATED and bumps the version")
    void reimportingWithFieldDifference_flipsToUpdatedAndBumpsVersion() {
        RelationTableDefinition existing = existingTable("Orders", "Order records");
        when(repository.findByTableName("orders")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findByIdWithFields(5L)).thenReturn(Optional.empty());

        // displayName on the field differs ("Amount (USD)" vs stored "Amount") — a real change.
        Map<String, Object> table = Map.of(
                "tableName", "orders",
                "displayName", "Orders",
                "description", "Order records",
                "fields", List.of(payloadField("Amount (USD)")));

        importer.importRelationTables(List.of(table), "tester");

        ArgumentCaptor<RelationTableDefinition> captor = ArgumentCaptor.forClass(RelationTableDefinition.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        RelationTableDefinition saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(RelationTableStatus.UPDATED);
        assertThat(saved.getCurrentVersion())
                .as("Version must bump by exactly 1 on a real structural change")
                .isEqualTo(4);
        assertThat(saved.getUpdatedBy()).isEqualTo("tester");
    }

    @Test
    @DisplayName("Re-importing with a changed display name (table-level, not field-level) also flips to UPDATED")
    void reimportingWithDisplayNameChange_flipsToUpdated() {
        RelationTableDefinition existing = existingTable("Orders", "Order records");
        when(repository.findByTableName("orders")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findByIdWithFields(5L)).thenReturn(Optional.empty());

        Map<String, Object> table = Map.of(
                "tableName", "orders",
                "displayName", "Customer Orders",
                "description", "Order records",
                "fields", List.of(payloadField("Amount")));

        importer.importRelationTables(List.of(table), "tester");

        ArgumentCaptor<RelationTableDefinition> captor = ArgumentCaptor.forClass(RelationTableDefinition.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        RelationTableDefinition saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(RelationTableStatus.UPDATED);
        assertThat(saved.getCurrentVersion()).isEqualTo(4);
        assertThat(saved.getDisplayName()).isEqualTo("Customer Orders");
    }
}
