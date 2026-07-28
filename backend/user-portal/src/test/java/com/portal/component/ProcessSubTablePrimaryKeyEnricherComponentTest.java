package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.PkGenerationConfig;
import com.platform.common.fk.PrimaryKeyAllocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProcessSubTablePrimaryKeyEnricherComponentTest {

    private static final long FU_ID = 50018L;
    private static final long SHIPMENT_TABLE = 50099L;
    private static final long PACKAGE_TABLE = 50100L;
    private static final long SHIPMENT_BINDING = 50140L;
    private static final long PACKAGE_BINDING = 50141L;

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PrimaryKeyAllocationService primaryKeyAllocationService;
    @Mock
    private PortalPrimaryKeyAllocationComponent portalPrimaryKeyAllocationComponent;

    private ProcessSubTablePrimaryKeyEnricherComponent enricher;

    @BeforeEach
    void setUp() {
        enricher = new ProcessSubTablePrimaryKeyEnricherComponent(
                jdbcTemplate,
                primaryKeyAllocationService,
                new ObjectMapper(),
                portalPrimaryKeyAllocationComponent);
    }

    @Test
    void allocateMissingPrimaryKeysInVariables_withoutSubTables_isNoOp() {
        enricher.allocateMissingPrimaryKeysInVariables("fu-code", Map.of("title", "x"));
        verifyNoInteractions(primaryKeyAllocationService);
    }

    /**
     * Sub-table inside a sub-table: grandchild rows live under {@code parentRow.__subTables__} and
     * are real rows of their own table. Before the recursion fix only the top-level slice was
     * enriched, so a nested row kept a blank Auto number for good.
     */
    @Test
    void allocateMissingPrimaryKeysInVariables_enrichesRowsNestedUnderAParentRow() {
        stubMetadata();
        stubSequentialAllocation();

        Map<String, Object> nestedPackage = new LinkedHashMap<>(Map.of("package_label", "PKG-1"));
        Map<String, Object> shipmentRow = new LinkedHashMap<>();
        shipmentRow.put("shipment_name", "SHP-A");
        shipmentRow.put("__subTables__", new LinkedHashMap<>(Map.of(
                String.valueOf(PACKAGE_BINDING), new ArrayList<>(List.of(nestedPackage)))));

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("__subTables__", new LinkedHashMap<>(Map.of(
                String.valueOf(SHIPMENT_BINDING), new ArrayList<>(List.of(shipmentRow)))));

        enricher.allocateMissingPrimaryKeysInVariables("fu-code", variables);

        assertThat(shipmentRow.get("id_idw")).isEqualTo(SHIPMENT_TABLE + "-1");
        assertThat(nestedPackage.get("id_idw")).isEqualTo(PACKAGE_TABLE + "-1");
    }

    /** A value the frontend already allocated is authoritative — re-keying would orphan child FKs. */
    @Test
    void allocateMissingPrimaryKeysInVariables_keepsExistingNestedKey() {
        stubMetadata();
        stubSequentialAllocation();

        Map<String, Object> nestedPackage = new LinkedHashMap<>();
        nestedPackage.put("package_label", "PKG-1");
        nestedPackage.put("id_idw", "3");
        Map<String, Object> shipmentRow = new LinkedHashMap<>();
        shipmentRow.put("id_idw", "7");
        shipmentRow.put("__subTables__", new LinkedHashMap<>(Map.of(
                String.valueOf(PACKAGE_BINDING), new ArrayList<>(List.of(nestedPackage)))));

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("__subTables__", new LinkedHashMap<>(Map.of(
                String.valueOf(SHIPMENT_BINDING), new ArrayList<>(List.of(shipmentRow)))));

        enricher.allocateMissingPrimaryKeysInVariables("fu-code", variables);

        assertThat(shipmentRow.get("id_idw")).isEqualTo("7");
        assertThat(nestedPackage.get("id_idw")).isEqualTo("3");
        verifyNoInteractions(primaryKeyAllocationService);
    }

    /**
     * Display-name aliases hold duplicate copies of a mapped slice's rows; enriching them too would
     * hand the same logical row a second, different Auto number.
     */
    @Test
    void allocateMissingPrimaryKeysInVariables_skipsUnmappedAliasSlices() {
        stubMetadata();
        stubSequentialAllocation();

        Map<String, Object> aliasNestedPackage = new LinkedHashMap<>(Map.of("package_label", "PKG-1"));
        Map<String, Object> aliasShipmentRow = new LinkedHashMap<>();
        aliasShipmentRow.put("shipment_name", "SHP-A");
        aliasShipmentRow.put("__subTables__", new LinkedHashMap<>(Map.of(
                String.valueOf(PACKAGE_BINDING), new ArrayList<>(List.of(aliasNestedPackage)))));

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("__subTables__", new LinkedHashMap<>(Map.of(
                "Shipment", new ArrayList<>(List.of(aliasShipmentRow)))));

        enricher.allocateMissingPrimaryKeysInVariables("fu-code", variables);

        assertThat(aliasShipmentRow).doesNotContainKey("id_idw");
        assertThat(aliasNestedPackage).doesNotContainKey("id_idw");
        verifyNoInteractions(primaryKeyAllocationService);
    }

    // ── stubs ────────────────────────────────────────────────────────────────

    private void stubMetadata() {
        when(portalPrimaryKeyAllocationComponent.resolveFunctionUnitIdForAllocation(anyString()))
                .thenReturn(FU_ID);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(FU_ID)))
                .thenReturn(List.of(
                        bindingRow(SHIPMENT_BINDING, SHIPMENT_TABLE, "nst_shipment"),
                        bindingRow(PACKAGE_BINDING, PACKAGE_TABLE, "nst_package")));

        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq(FU_ID)))
                .thenAnswer(inv -> {
                    ResultSetExtractor<?> extractor = inv.getArgument(1);
                    return extractor.extractData(autoPkResultSet());
                });
    }

    /** Every allocation returns "<tableId>-<n>" so the test can tell the two tables apart. */
    private void stubSequentialAllocation() {
        Map<Long, AtomicInteger> counters = new HashMap<>();
        when(primaryKeyAllocationService.allocate(
                anyLong(), anyString(), any(PkGenerationConfig.class), anyInt(), anyString()))
                .thenAnswer(inv -> {
                    long tableId = inv.getArgument(0);
                    int n = counters.computeIfAbsent(tableId, k -> new AtomicInteger()).incrementAndGet();
                    return List.of(tableId + "-" + n);
                });
    }

    private static Map<String, Object> bindingRow(long bindingId, long tableId, String tableName) {
        Map<String, Object> m = new HashMap<>();
        m.put("bindingId", bindingId);
        m.put("tableId", tableId);
        m.put("tableName", tableName);
        return m;
    }

    /** Both sub-tables declare an autoIncrement ("Auto number") primary key. */
    private static ResultSet autoPkResultSet() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getLong("table_id")).thenReturn(SHIPMENT_TABLE, PACKAGE_TABLE);
        when(rs.getString("field_name")).thenReturn("id_idw", "id_idw");
        when(rs.getString("json")).thenReturn(
                "{\"strategy\":\"autoIncrement\",\"startValue\":1}",
                "{\"strategy\":\"autoIncrement\",\"startValue\":1}");
        return rs;
    }
}
