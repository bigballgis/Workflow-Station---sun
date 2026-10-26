package com.platform.common.functionunit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Releases imported before relation tables were written into snapshots: requests on them read each bound
 * relation table from Admin Center's relation-table history, at the version deployed when the release was
 * published. A release new requests start on must still be complete by itself.
 */
class CatalogSnapshotReaderLegacyRelationTest {

    private static final String LEGACY = "legacy-release";
    private static final String MEETING_ROOM_V9 = "[{\"id\":65,\"fieldName\":\"id\",\"dataType\":\"INTEGER\",\"isPrimaryKey\":true,"
            + "\"pkGeneration\":{\"strategy\":\"uuid\"},\"refTableId\":null,\"isForeignKey\":false},"
            + "{\"id\":66,\"fieldName\":\"site\",\"dataType\":\"BIGINT\",\"isPrimaryKey\":false,\"pkGeneration\":null,"
            + "\"isForeignKey\":true,\"refTableId\":1,\"refPrimaryKeyFields\":[\"id\"]}]";
    private static final String TEST_V14 = "[{\"id\":1,\"fieldName\":\"id\",\"dataType\":\"INTEGER\",\"isPrimaryKey\":true,"
            + "\"pkGeneration\":null,\"refTableId\":null}]";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CatalogSnapshotReader reader = new CatalogSnapshotReader(jdbc, new ObjectMapper());
    private final Map<String, Object> form = Map.of("formId", 5, "tableBindings", List.of(Map.of(
            "bindingId", 7, "bindingType", "RELATED", "relationTableId", 2)));

    @BeforeEach
    void setUp() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq(LEGACY), eq("DATA_TABLE"))).thenReturn(List.of(
                "{\"tableId\":10,\"tableName\":\"main\",\"tableType\":\"MAIN\",\"fields\":[]}"));
        when(jdbc.queryForList(anyString(), eq(String.class), eq(LEGACY), eq("FORM"))).thenReturn(List.of(
                "{\"formId\":5,\"tableBindings\":[{\"bindingId\":7,\"bindingType\":\"RELATED\",\"relationTableId\":2}]}"));
        lenient().when(jdbc.queryForList(contains("rt_table_versions"), eq("2"), anyString(), eq(LEGACY)))
                .thenReturn(List.of(row(2L, "meeting_room", "Meeting Room", MEETING_ROOM_V9)));
        lenient().when(jdbc.queryForList(contains("SELECT table_name FROM rt_table_definitions"), eq(String.class), eq("1")))
                .thenReturn(List.of("test"));
        lenient().when(jdbc.queryForList(contains("lower(d.table_name)"), eq("test"), anyString(), eq(LEGACY)))
                .thenReturn(List.of(row(1L, "test", "test", TEST_V14)));
    }

    private void legacy(boolean legacy) {
        when(jdbc.queryForList(contains("content_checksum IS NULL"), eq(Boolean.class), eq(LEGACY)))
                .thenReturn(List.of(legacy));
    }

    private static Map<String, Object> row(long id, String name, String displayName, String fields) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("table_name", name);
        row.put("display_name", displayName);
        row.put("description", null);
        row.put("snapshot_data", fields);
        return row;
    }

    @Test
    void legacyReleaseReadsItsRelationTableAsDeployedWhenItWasPublished() {
        legacy(true);

        Map<String, Object> binding = reader.formBindings(LEGACY, form).get(0);

        assertThat(binding).containsEntry("tableName", "meeting_room").containsEntry("relationTableId", 2L)
                .containsEntry("tableType", "RELATION").containsEntry("primaryKeyFields", List.of("id"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) binding.get("fieldDefinitions");
        assertThat(fields.get(0).get("pkGeneration")).isEqualTo(Map.of("strategy", "uuid"));
        // A foreign key to another relation table resolves to that table, also as recorded at the time.
        assertThat(fields.get(1)).containsEntry("refTableId", 1L);
    }

    /** Activation must not lean on the history: a release new requests start on is complete by itself. */
    @Test
    void activationGateDoesNotCompleteALegacyRelease() {
        assertThatThrownBy(() -> reader.completeFormBindings(LEGACY, form))
                .isInstanceOf(FunctionUnitVersionException.class)
                .hasMessageContaining("VERSION_SNAPSHOT_INCOMPLETE");
        verify(jdbc, never()).queryForList(contains("rt_table_versions"), any(Object[].class));
    }

    /** A release imported with content checksums carries its relation tables; one missing is a broken release. */
    @Test
    void currentReleaseMissingARelationTableIsIncomplete() {
        legacy(false);

        assertThatThrownBy(() -> reader.formBindings(LEGACY, form))
                .isInstanceOf(FunctionUnitVersionException.class)
                .hasMessageContaining("VERSION_SNAPSHOT_INCOMPLETE");
        verify(jdbc, never()).queryForList(contains("rt_table_versions"), any(Object[].class));
    }

    /** Releases whose bindings all resolve never ask whether they are legacy: no extra query on the read path. */
    @Test
    void resolvableBindingsNeverAskWhetherTheReleaseIsLegacy() {
        Map<String, Object> mainOnly = Map.of("formId", 6, "tableBindings", List.of(Map.of(
                "bindingId", 8, "bindingType", "PRIMARY", "tableName", "main")));
        reader.formBindings(LEGACY, mainOnly);
        reader.formBindings(LEGACY, mainOnly);
        verify(jdbc, never()).queryForList(contains("content_checksum"), eq(Boolean.class), any(Object[].class));
    }

    @Test
    void legacyStatusAndTablesAreLoadedOncePerRelease() {
        legacy(true);
        reader.formBindings(LEGACY, form);
        reader.formBindings(LEGACY, form);
        verify(jdbc, times(1)).queryForList(contains("content_checksum IS NULL"), eq(Boolean.class), eq(LEGACY));
        verify(jdbc, times(1)).queryForList(contains("rt_table_versions"), eq("2"), anyString(), eq(LEGACY));
    }

    /** Relation tables that reference each other are each read once; the walk ends. */
    @Test
    void mutuallyReferencingRelationTablesEndTheWalk() {
        legacy(true);
        when(jdbc.queryForList(contains("lower(d.table_name)"), eq("test"), anyString(), eq(LEGACY)))
                .thenReturn(List.of(row(1L, "test", "test", "[{\"fieldName\":\"id\",\"isPrimaryKey\":true},"
                        + "{\"fieldName\":\"room\",\"isForeignKey\":true,\"refTableId\":2}]")));
        when(jdbc.queryForList(contains("SELECT table_name FROM rt_table_definitions"), eq(String.class), eq("2")))
                .thenReturn(List.of("meeting_room"));
        lenient().when(jdbc.queryForList(contains("lower(d.table_name)"), eq("meeting_room"), anyString(), eq(LEGACY)))
                .thenReturn(List.of(row(2L, "meeting_room", "Meeting Room", MEETING_ROOM_V9)));

        assertThat(reader.formBindings(LEGACY, form).get(0)).containsEntry("tableName", "meeting_room");
    }

    /** A sub-table's foreign key to a relation table also resolves on a legacy release. */
    @Test
    void subTableForeignKeyToARelationTableResolvesOnALegacyRelease() {
        legacy(true);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(LEGACY), eq("DATA_TABLE"))).thenReturn(List.of(
                "{\"tableId\":10,\"tableName\":\"main\",\"tableType\":\"MAIN\",\"fields\":[]}",
                "{\"tableId\":11,\"tableName\":\"bookings\",\"tableType\":\"SUB\",\"fields\":["
                        + "{\"fieldName\":\"room\",\"isForeignKey\":true,\"refTableName\":\"meeting_room\"}]}"));
        when(jdbc.queryForList(contains("lower(d.table_name)"), eq("meeting_room"), anyString(), eq(LEGACY)))
                .thenReturn(List.of(row(2L, "meeting_room", "Meeting Room", MEETING_ROOM_V9)));
        Map<String, Object> sub = Map.of("formId", 9, "tableBindings", List.of(Map.of(
                "bindingId", 9, "bindingType", "SUB", "tableName", "bookings")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) reader.formBindings(LEGACY, sub).get(0).get("fieldDefinitions");
        assertThat(fields.get(0)).containsEntry("refTableId", 2L);
    }

    /**
     * A Lookup over the release's own table never takes a same-named relation table from the history, whichever
     * binding asks for the history first.
     */
    @Test
    void relatedBindingToAnOwnTableIgnoresASameNamedRelationTableInTheHistory() {
        legacy(true);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(LEGACY), eq("DATA_TABLE"))).thenReturn(List.of(
                "{\"tableId\":10,\"tableName\":\"main\",\"tableType\":\"MAIN\",\"fields\":[]}",
                "{\"tableId\":11,\"tableName\":\"bookings\",\"tableType\":\"SUB\",\"fields\":[]}"));
        Map<String, Object> own = new HashMap<>();
        own.put("bindingId", 8);
        own.put("bindingType", "RELATED");
        own.put("tableName", "bookings");
        own.put("relationTableId", null);
        Map<String, Object> mixed = Map.of("formId", 5, "tableBindings", List.of(
                Map.of("bindingId", 7, "bindingType", "RELATED", "relationTableId", 2), own));
        when(jdbc.queryForList(anyString(), eq(String.class), eq(LEGACY), eq("FORM"))).thenReturn(List.of(
                "{\"formId\":5,\"tableBindings\":[{\"bindingId\":7,\"bindingType\":\"RELATED\",\"relationTableId\":2},"
                        + "{\"bindingId\":8,\"bindingType\":\"RELATED\",\"tableName\":\"bookings\",\"relationTableId\":null}]}"));
        lenient().when(jdbc.queryForList(contains("lower(d.table_name)"), eq("bookings"), anyString(), eq(LEGACY)))
                .thenReturn(List.of(row(3L, "bookings", "Bookings", TEST_V14)));

        Map<String, Object> binding = reader.formBindings(LEGACY, mixed).get(1);

        assertThat(binding).containsEntry("tableId", 11).containsEntry("tableType", "SUB")
                .doesNotContainKey("relationTableName");
        verify(jdbc, never()).queryForList(contains("lower(d.table_name)"), eq("bookings"), anyString(), eq(LEGACY));
    }

    @Test
    void historyIsReadInTheTimeZoneTheVersionsWereRecordedIn() {
        legacy(true);
        ReflectionTestUtils.setField(reader, "recordedTimeZone", "Asia/Shanghai");

        reader.formBindings(LEGACY, form);

        verify(jdbc).queryForList(contains("rt_table_versions"), eq("2"), eq("Asia/Shanghai"), eq(LEGACY));
    }
}
