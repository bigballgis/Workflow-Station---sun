package com.platform.common.functionunit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RELATED bindings as Developer Workstation exports them: no tableName, only relationTableId; and the
 * platform's virtual Users relation, which no package (and so no snapshot) carries a table for.
 */
class CatalogSnapshotReaderRelatedBindingTest {

    private static final String CATALOG = "cat-1";

    private CatalogSnapshotReader reader;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(CATALOG), eq("DATA_TABLE"))).thenReturn(List.of(
                "{\"tableId\":10,\"tableName\":\"main\",\"tableType\":\"MAIN\",\"fields\":[]}",
                "{\"tableId\":11,\"tableName\":\"bookings\",\"tableType\":\"SUB\","
                        + "\"fields\":[{\"fieldName\":\"booking_no\",\"dataType\":\"STRING\",\"isPrimaryKey\":true}]}",
                "{\"tableId\":12,\"tableName\":\"showcase_doc\",\"tableType\":\"RELATION\","
                        + "\"fields\":[{\"fieldName\":\"doc_no\",\"dataType\":\"STRING\",\"isPrimaryKey\":true}]}",
                "{\"relationTableId\":2,\"tableName\":\"meeting_room\",\"tableType\":\"RELATION\","
                        + "\"fields\":[{\"fieldName\":\"room\",\"dataType\":\"STRING\",\"isPrimaryKey\":true}]}"));
        reader = new CatalogSnapshotReader(jdbc, new ObjectMapper());
    }

    private static Map<String, Object> related(int bindingId, Object relationTableId) {
        Map<String, Object> binding = new HashMap<>();
        binding.put("bindingId", bindingId);
        binding.put("bindingType", "RELATED");
        binding.put("tableName", null);
        binding.put("relationTableId", relationTableId);
        return binding;
    }

    private List<Map<String, Object>> bindingsOf(Map<String, Object>... bindings) {
        return reader.formBindings(CATALOG, Map.of("tableBindings", List.of(bindings)));
    }

    @Test
    void unnamedRelatedBindingResolvesItsRelationTableById() {
        Map<String, Object> binding = bindingsOf(related(1, 2)).get(0);

        assertThat(binding).containsEntry("tableId", 2).containsEntry("relationTableId", 2)
                .containsEntry("tableName", "meeting_room").containsEntry("relationTableName", "meeting_room")
                .containsEntry("tableType", "RELATION").containsEntry("primaryKeyFields", List.of("room"));
    }

    @Test
    void builtInUsersRelationPassesThroughWithoutASnapshotTable() {
        Map<String, Object> binding = bindingsOf(related(2, -1_000_000_001)).get(0);

        assertThat(binding).containsEntry("bindingId", 2).containsEntry("relationTableId", -1_000_000_001)
                .containsEntry("tableId", null).containsEntry("tableType", "RELATION")
                .containsEntry("fieldDefinitions", List.of());
    }

    /** A Lookup over one of the release's own tables has no relation table: it binds that table, as a SUB binding would. */
    @Test
    void relatedBindingToTheReleasesOwnTableResolvesThatTable() {
        Map<String, Object> raw = related(4, null);
        raw.put("tableName", "bookings");
        raw.put("tableId", 999); // whatever the binding carries, the release's table decides
        raw.put("tableType", "MAIN");
        Map<String, Object> form = Map.of("tableBindings", List.of(raw));

        Map<String, Object> binding = reader.formBindings(CATALOG, form).get(0);

        assertThat(binding).containsEntry("tableId", 11).containsEntry("tableType", "SUB")
                .containsEntry("tableName", "bookings").containsEntry("relationTableId", null)
                .doesNotContainKey("relationTableName").containsEntry("primaryKeyFields", List.of("booking_no"));
        // Activation and new requests read the same binding.
        assertThat(reader.completeFormBindings(CATALOG, form)).containsExactly(binding);
    }

    @Test
    void relatedBindingToAnOwnTableTheReleaseLacksIsIncomplete() {
        Map<String, Object> raw = related(5, null);
        raw.put("tableName", "missing");

        assertThatThrownBy(() -> bindingsOf(raw))
                .isInstanceOf(FunctionUnitVersionException.class)
                .hasMessageContaining("VERSION_SNAPSHOT_INCOMPLETE");
    }

    /**
     * A RELATED binding to a designer table typed RELATION keeps the relation shape it always had: Portal files its
     * rows under the relation namespace by relationTableName (front end) and tableType (back end).
     */
    @Test
    void relatedBindingToADesignerRelationTableKeepsTheRelationShape() {
        Map<String, Object> raw = related(6, null);
        raw.put("tableName", "showcase_doc");

        Map<String, Object> binding = bindingsOf(raw).get(0);

        assertThat(binding).containsEntry("tableType", "RELATION").containsEntry("relationTableName", "showcase_doc");
    }

    @Test
    void relationTableMissingFromTheSnapshotIsStillIncomplete() {
        assertThatThrownBy(() -> bindingsOf(related(3, 9)))
                .isInstanceOf(FunctionUnitVersionException.class)
                .hasMessageContaining("VERSION_SNAPSHOT_INCOMPLETE");
    }
}
