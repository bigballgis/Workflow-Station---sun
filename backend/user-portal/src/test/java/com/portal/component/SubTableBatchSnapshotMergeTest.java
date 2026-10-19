package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SubTableBatchSnapshotMergeTest {
    @Test
    void enginePayloadCannotCreateBatchMetadataForLegacyInstance() {
        Map<String, Object> merged = SubTableBatchInitialRows.mergeSnapshot(Map.of(),
                Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of("forged", true), "business", "kept"),
                new ObjectMapper());
        assertFalse(merged.containsKey(SubTableWriteIsolation.BATCH_RUNTIME_FIELD));
        assertEquals("kept", merged.get("business"));
    }

    @Test
    void completedEngineRowsMustNotEraseServerOwnedVersions() {
        Map<String, Object> snapshot = Map.of("forms", Map.of("form", Map.of("controls", List.of(
                Map.of("rowDesign", Map.of("binding", Map.of("tableName", "lines", "primaryKeyFields", List.of("key"))))))));
        Map<String, Object> saved = Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot,
                "__subTables__", Map.of("dw:lines", List.of(Map.of("key", "one", "caption", "saved", "_wsRowVersion", 3))));
        Map<String, Object> incoming = Map.of("__subTables__", Map.of("dw:lines", List.of(
                Map.of("key", "one", "caption", "completed"), Map.of("key", "two", "caption", "new"))));
        Map<String, Object> merged = SubTableBatchInitialRows.mergeSnapshot(saved, incoming, new ObjectMapper());
        List<?> rows = (List<?>) ((Map<?, ?>) merged.get("__subTables__")).get("dw:lines");
        assertEquals(3, ((Map<?, ?>) rows.get(0)).get("_wsRowVersion"));
        assertEquals("completed", ((Map<?, ?>) rows.get(0)).get("caption"));
        assertFalse(((Map<?, ?>) rows.get(1)).containsKey("_wsRowVersion"));
        assertSame(snapshot, merged.get(SubTableWriteIsolation.BATCH_RUNTIME_FIELD));
        assertFalse(((Map<?, ?>) ((List<?>) ((Map<?, ?>) incoming.get("__subTables__")).get("dw:lines")).get(0)).containsKey("_wsRowVersion"));
    }
}
