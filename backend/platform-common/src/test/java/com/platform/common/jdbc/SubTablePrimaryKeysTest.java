package com.platform.common.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.functionunit.CatalogSnapshotReader;
import com.platform.common.functionunit.FunctionUnitVersionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sub-table row keys at runtime: a legacy physical table keeps its PRIMARY KEY constraint; a JSON-row
 * table is keyed as the request's release declares it — never as the designer declares it today.
 */
class SubTablePrimaryKeysTest {

    private static final String RELEASE = "rel-1";

    private JdbcTemplate jdbc;
    private CatalogSnapshotReader snapshots;

    @BeforeEach
    void setUp() {
        PostgresPhysicalTablePrimaryKeys.clearCache();
        jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(contains("information_schema"), any(RowMapper.class), anyString())).thenReturn(List.of());
        when(jdbc.queryForList(anyString(), eq(String.class), eq(RELEASE), eq("FORM"))).thenReturn(List.of());
        when(jdbc.queryForList(anyString(), eq(String.class), eq(RELEASE), eq("DATA_TABLE"))).thenReturn(List.of(
                "{\"tableId\":1,\"tableName\":\"people\",\"tableType\":\"SUB\",\"fields\":["
                        + "{\"fieldName\":\"name\"},{\"fieldName\":\"id_idw\",\"isPrimaryKey\":true}]}",
                "{\"tableId\":2,\"tableName\":\"notes\",\"tableType\":\"SUB\",\"fields\":[{\"fieldName\":\"text\"}]}"));
        snapshots = new CatalogSnapshotReader(jdbc, new ObjectMapper());
    }

    @Test
    void jsonRowTableIsKeyedAsItsReleaseDeclares() {
        assertThat(SubTablePrimaryKeys.resolve(jdbc, snapshots, () -> RELEASE, "People")).containsExactly("id_idw");
        verify(jdbc, never()).query(contains("dw_"), any(RowMapper.class), any(Object[].class));
        verify(jdbc, never()).queryForList(contains("dw_"), eq(String.class), any(Object[].class));
    }

    @Test
    void tableWithoutDeclaredKeyUsesThePlatformRowId() {
        assertThat(SubTablePrimaryKeys.resolve(jdbc, snapshots, () -> RELEASE, "notes"))
                .containsExactly(SubTableRowIdentity.CANONICAL_FIELD);
    }

    @Test
    @SuppressWarnings("unchecked")
    void physicalTableKeepsItsConstraintAndNeedsNoRelease() {
        when(jdbc.query(contains("information_schema"), any(RowMapper.class), eq("meeting"))).thenReturn(List.of("id"));
        Supplier<String> release = mock(Supplier.class);

        assertThat(SubTablePrimaryKeys.resolve(jdbc, snapshots, release, "meeting")).containsExactly("id");
        verify(release, never()).get();
    }

    @Test
    void tableMissingFromTheReleaseIsAVersionError() {
        assertThatThrownBy(() -> SubTablePrimaryKeys.resolve(jdbc, snapshots, () -> RELEASE, "other"))
                .isInstanceOf(FunctionUnitVersionException.class);
    }

    @Test
    void requestWithoutAReleaseIsAVersionError() {
        assertThatThrownBy(() -> SubTablePrimaryKeys.resolve(jdbc, snapshots, () -> null, "people"))
                .isInstanceOf(FunctionUnitVersionException.class);
    }

    /** Keying one table must not depend on every other binding of the release resolving. */
    @Test
    void anUnrelatedBrokenBindingDoesNotStopKeyingATable() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq(RELEASE), eq("FORM"))).thenReturn(List.of(
                "{\"formId\":9,\"tableBindings\":[{\"bindingType\":\"SUB\",\"tableName\":\"gone\"}]}"));
        assertThat(SubTablePrimaryKeys.resolve(jdbc, snapshots, () -> RELEASE, "people")).containsExactly("id_idw");
    }

    @Test
    void relationTablesAreNotTheReleasesDataTables() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq(RELEASE), eq("DATA_TABLE"))).thenReturn(List.of(
                "{\"relationTableId\":7,\"tableName\":\"vendors\",\"tableType\":\"RELATION\",\"fields\":[]}"));
        assertThat(snapshots.dataTable(RELEASE, "vendors")).isEmpty();
        assertThatThrownBy(() -> SubTablePrimaryKeys.resolve(jdbc, snapshots, () -> RELEASE, "vendors"))
                .isInstanceOf(FunctionUnitVersionException.class);
    }

    @Test
    void physicalLookupNeverFallsBackToTheDesigner() {
        assertThatThrownBy(() -> PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbc, "people"))
                .isInstanceOf(MissingPhysicalTablePrimaryKeyException.class);
        verify(jdbc, never()).query(contains("dw_"), any(RowMapper.class), any(Object[].class));
    }
}
