package com.developer.component.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Re-importing an existing relation table via the JDBC-based
 * {@link RelationTableStructurePortability#importAll(List, String)} update branch must be a no-op
 * for status/version when the incoming structure is byte-identical to what's stored, and must flip
 * to UPDATED + bump the version when it actually differs. Mirrors
 * {@code RelationTableStructureImporterDiffGateTest} (admin-center's JPA-based equivalent). Covers
 * the "new table" create branch separately in {@code RelationTableComputedFieldPortabilityTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RelationTableStructurePortability update-branch diff gate")
class RelationTableStructurePortabilityDiffGateTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private RelationTableStructurePortability portability;

    @BeforeEach
    void setUp() {
        portability = new RelationTableStructurePortability(jdbcTemplate, new ObjectMapper());
    }

    /** Stubs the "does a table with this name already exist" lookup to return the given id. */
    @SuppressWarnings("unchecked")
    private void stubExistingTableId(String tableName, Long id) {
        when(jdbcTemplate.query(eq("SELECT id FROM rt_table_definitions WHERE table_name = ?"),
                any(RowMapper.class), eq(tableName)))
                .thenReturn(List.of(id));
    }

    private void stubCurrentDisplayNameAndDescription(Long id, String displayName, String description) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("display_name", displayName);
        row.put("description", description);
        when(jdbcTemplate.queryForMap(
                "SELECT display_name, description FROM rt_table_definitions WHERE id = ?", id))
                .thenReturn(row);
    }

    /** Stubs the current-fields lookup (used to build the diff-gate comparison map). */
    @SuppressWarnings("unchecked")
    private void stubCurrentFields(Long id, String fieldName, String displayName) {
        when(jdbcTemplate.query(contains("FROM rt_field_definitions f WHERE f.table_id"),
                any(RowMapper.class), eq(id)))
                .thenAnswer(inv -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("fieldName", fieldName);
                    m.put("dataType", "DECIMAL");
                    m.put("length", 10);
                    m.put("precision", 10);
                    m.put("scale", 2);
                    m.put("nullable", true);
                    m.put("isPrimaryKey", false);
                    m.put("defaultValue", null);
                    m.put("displayName", displayName);
                    m.put("isForeignKey", false);
                    m.put("refTableName", null);
                    m.put("refPrimaryKeyFields", null);
                    m.put("pkGenerationJson", null);
                    m.put("fkDisplayMode", "readonly");
                    // lookupConfig/sortOrder participate in the diff gate: a LOOKUP reconfiguration or
                    // a field reorder is a real design change and must flip the table to UPDATED.
                    m.put("lookupConfig", null);
                    m.put("sortOrder", 0);
                    m.put("isComputed", false);
                    m.put("computedField", null);
                    return List.of(m);
                });
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
        Long tableId = 5L;
        stubExistingTableId("orders", tableId);
        stubCurrentDisplayNameAndDescription(tableId, "Orders", "Order records");
        stubCurrentFields(tableId, "amount", "Amount");

        Map<String, Object> table = Map.of(
                "tableName", "orders",
                "displayName", "Orders",
                "description", "Order records",
                "fields", List.of(payloadField("Amount")));

        portability.importAll(List.of(table), "tester");

        verify(jdbcTemplate).update(
                eq("UPDATE rt_table_definitions SET display_name = ?, description = ?, updated_at = ?, updated_by = ? WHERE id = ?"),
                eq("Orders"), eq("Order records"), any(), eq("tester"), eq(tableId));
        verify(jdbcTemplate, never()).update(contains("status = 'UPDATED'"), (Object[]) any());
    }

    @Test
    @DisplayName("Re-importing with an actual field difference flips to UPDATED and bumps the version")
    void reimportingWithFieldDifference_flipsToUpdatedAndBumpsVersion() {
        Long tableId = 5L;
        stubExistingTableId("orders", tableId);
        stubCurrentDisplayNameAndDescription(tableId, "Orders", "Order records");
        stubCurrentFields(tableId, "amount", "Amount");

        // displayName on the field differs ("Amount (USD)" vs stored "Amount") — a real change.
        Map<String, Object> table = Map.of(
                "tableName", "orders",
                "displayName", "Orders",
                "description", "Order records",
                "fields", List.of(payloadField("Amount (USD)")));

        portability.importAll(List.of(table), "tester");

        verify(jdbcTemplate).update(
                contains("status = 'UPDATED', current_version = COALESCE(current_version, 0) + 1"),
                eq("Orders"), eq("Order records"), any(), eq("tester"), eq(tableId));
    }

    @Test
    @DisplayName("Re-importing with a changed table-level display name also flips to UPDATED")
    void reimportingWithDisplayNameChange_flipsToUpdated() {
        Long tableId = 5L;
        stubExistingTableId("orders", tableId);
        stubCurrentDisplayNameAndDescription(tableId, "Orders", "Order records");
        stubCurrentFields(tableId, "amount", "Amount");

        Map<String, Object> table = Map.of(
                "tableName", "orders",
                "displayName", "Customer Orders",
                "description", "Order records",
                "fields", List.of(payloadField("Amount")));

        portability.importAll(List.of(table), "tester");

        verify(jdbcTemplate).update(
                contains("status = 'UPDATED', current_version = COALESCE(current_version, 0) + 1"),
                eq("Customer Orders"), eq("Order records"), any(), eq("tester"), eq(tableId));
    }
}
