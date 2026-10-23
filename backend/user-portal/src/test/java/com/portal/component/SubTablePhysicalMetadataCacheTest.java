package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.functionunit.CatalogSnapshotReader;
import com.platform.common.functionunit.FunctionUnitVersionException;
import com.platform.common.jdbc.PostgresPhysicalTablePrimaryKeys;
import com.portal.client.WorkflowEngineClient;
import com.portal.component.MiOverlaySupport.MiRowProgress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Sub-table row keys on the Portal come from the request's release, never from the designer's tables. */
class SubTablePhysicalMetadataCacheTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CatalogSnapshotReader snapshots = new CatalogSnapshotReader(jdbc, new ObjectMapper());
    private final SubTablePhysicalMetadataCache cache = new SubTablePhysicalMetadataCache(jdbc, snapshots);

    @BeforeEach
    void setUp() {
        PostgresPhysicalTablePrimaryKeys.clearCache();
        when(jdbc.query(contains("PRIMARY KEY"), ArgumentMatchers.<RowMapper<String>>any(), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbc.queryForList(anyString(), eq(String.class), anyString(), eq("FORM"))).thenReturn(List.of());
        release("rel-1", "{\"tableId\":1,\"tableName\":\"people\",\"tableType\":\"SUB\",\"fields\":["
                + "{\"fieldName\":\"name\"},{\"fieldName\":\"id_idw\",\"isPrimaryKey\":true}]}",
                "{\"relationTableId\":7,\"tableName\":\"rt_vendor\",\"tableType\":\"RELATION\",\"fields\":[]}");
        release("rel-2", "{\"tableId\":1,\"tableName\":\"people\",\"tableType\":\"SUB\",\"fields\":["
                + "{\"fieldName\":\"person_code\",\"isPrimaryKey\":true}]}");
    }

    private void release(String catalogId, String... dataTables) {
        when(jdbc.queryForList(anyString(), eq(String.class), eq(catalogId), eq("DATA_TABLE")))
                .thenReturn(List.of(dataTables));
    }

    private void physicalKey(String table, String column) {
        when(jdbc.query(contains("PRIMARY KEY"), ArgumentMatchers.<RowMapper<String>>any(), eq(table)))
                .thenReturn(List.of(column));
    }

    @Test
    void jsonStoredTableIsKeyedByEachRequestsOwnRelease() {
        assertThat(cache.rowKeyColumns("rel-1", "people")).contains(List.of("id_idw"));
        assertThat(cache.rowKeyColumns("rel-2", "people")).contains(List.of("person_code"));
    }

    @Test
    void releaseTableWithAPhysicalPrimaryKeyKeepsThatKey() {
        physicalKey("people", "id");
        assertThat(cache.rowKeyColumns("rel-1", "people")).contains(List.of("id"));
    }

    /**
     * The user directory travels in {@code __subTables__} as {@code rt:sys_users} and is also a physical
     * table: it is not one of the release's data tables, so its rows are never merged with sys_users rows.
     */
    @Test
    void physicalTableTheReleaseDoesNotDefineHasNoRowKey() {
        physicalKey("sys_users", "id");
        assertThat(cache.rowKeyColumns("rel-1", "sys_users")).isEmpty();
        verify(jdbc, never()).query(contains("PRIMARY KEY"), ArgumentMatchers.<RowMapper<String>>any(), eq("sys_users"));
    }

    @Test
    void relationTableHasNoRowKey() {
        assertThat(cache.rowKeyColumns("rel-1", "rt_vendor")).isEmpty();
    }

    @Test
    void requestWithoutAReleaseNeedsRecovery() {
        assertThatThrownBy(() -> cache.rowKeyColumns(null, "people"))
                .isInstanceOf(FunctionUnitVersionException.class)
                .satisfies(e -> assertThat(((FunctionUnitVersionException) e).getDetails())
                        .containsEntry("reason", "VERSION_RECOVERY_REQUIRED"));
    }

    @Test
    void miProgressRowsAreKeyedByTheReleasePrimaryKey() {
        MiOverlayComponent overlay = new MiOverlayComponent(engineReportingOneTaskOn("people"), cache);

        Map<String, Map<String, MiRowProgress>> progress = overlay.resolveMiRowProgress("pi-1", "rel-1", "RUNNING");

        assertThat(progress).containsOnlyKeys("people");
        assertThat(progress.get("people").values()).singleElement()
                .satisfies(p -> assertThat(p.status()).isEqualTo("IN_PROGRESS"));
    }

    @Test
    void miTableTheReleaseDoesNotDefineIsAnIncompleteSnapshot() {
        MiOverlayComponent overlay = new MiOverlayComponent(engineReportingOneTaskOn("orphan_rows"), cache);

        assertThatThrownBy(() -> overlay.resolveMiRowProgress("pi-1", "rel-1", "RUNNING"))
                .isInstanceOf(FunctionUnitVersionException.class)
                .satisfies(e -> assertThat(((FunctionUnitVersionException) e).getDetails())
                        .containsEntry("reason", "VERSION_SNAPSHOT_INCOMPLETE"));
    }

    private static WorkflowEngineClient engineReportingOneTaskOn(String table) {
        WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
        when(engine.isAvailable()).thenReturn(true);
        when(engine.getMultiInstanceStatus("pi-1")).thenReturn(Optional.of(Map.of("tasks", List.of(Map.of(
                "taskId", "t-1", "taskName", "Review", "status", "ASSIGNED", "subTableName", table,
                "subTableRowKey", Map.of("id_idw", "row-1"))))));
        return engine;
    }
}
