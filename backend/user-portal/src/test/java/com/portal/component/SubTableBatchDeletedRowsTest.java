package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.dto.SubTableBatchChange;
import com.portal.util.SubTableBatchMetadata;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SubTableBatchDeletedRowsTest {
    private Map<String, Object> variables(List<?> rows) {
        Map<String, Object> binding = Map.of("tableName", "renamedLines", "primaryKeyFields", List.of("lineKey"),
                "foreignKeyField", "parentRef", "fieldDefinitions", List.of(Map.of("fieldName", "parentRef",
                        "isForeignKey", true, "refTableId", 10, "refPrimaryKeyFields", List.of("caseKey"))));
        Map<String, Object> design = Map.of("binding", binding, "parentTablesById",
                Map.of("10", Map.of("tableName", "cases", "tableType", "MAIN")));
        return Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of("forms", Map.of("form", Map.of("controls",
                List.of(Map.of("batchControlId", "control-one", "bindingId", "binding-one", "rowDesign", design))))),
                "__subTables__", Map.of("dw:renamedlines", rows));
    }

    private List<SubTableBatchChange> change(String operation) {
        SubTableBatchChange change = new SubTableBatchChange();
        change.setBindingId("binding-one"); change.setBatchControlId("control-one"); change.setOperation(operation);
        return List.of(change);
    }

    @Test
    void recordsActualDeletionsAndClearsOnlyReaddedIdentitiesWithoutMutatingTheSnapshot() {
        Map<String, Object> baseline = variables(List.of(Map.of("lineKey", "old"), Map.of("lineKey", "kept")));
        Map<String, Object> updated = new HashMap<>(variables(List.of(Map.of("lineKey", "kept"))));
        SubTableBatchDeletedRows.record(updated, baseline, change("DELETE"));
        assertEquals(List.of(Map.of("lineKey", "new-service")), SubTableBatchDeletedRows.filter(
                List.of(Map.of("lineKey", "old"), Map.of("lineKey", "new-service")),
                SubTableBatchDeletedRows.read(updated).get("dw:renamedlines")));
        assertFalse(baseline.containsKey(SubTableBatchMetadata.DELETED_ROWS_FIELD));
        Map<String, Object> readded = new HashMap<>(variables(List.of(Map.of("lineKey", "kept"), Map.of("lineKey", "old"))));
        SubTableBatchDeletedRows.record(readded, updated, change("ADD"));
        assertTrue(SubTableBatchDeletedRows.read(readded).get("dw:renamedlines").identities().isEmpty());
        Map<String, Object> ordinaryReadd = new HashMap<>(variables(List.of(Map.of("lineKey", "old"))));
        SubTableBatchDeletedRows.record(ordinaryReadd, updated, null);
        assertTrue(SubTableBatchDeletedRows.read(ordinaryReadd).get("dw:renamedlines").identities().isEmpty());
        Map<String, Object> completedReadd = SubTableBatchInitialRows.mergeCompletion(updated,
                Map.of("__subTables__", Map.of("dw:renamedlines", List.of(Map.of("lineKey", "old")))),
                null, new ObjectMapper());
        assertEquals(List.of(Map.of("lineKey", "old")), ((Map<?, ?>) completedReadd.get("__subTables__")).get("dw:renamedlines"));
        assertEquals(baseline.get(SubTableWriteIsolation.BATCH_RUNTIME_FIELD), updated.get(SubTableWriteIsolation.BATCH_RUNTIME_FIELD));
    }

    @Test
    void engineCannotForgeOrEraseLocalDeletionStateOrResurrectDeletedRowsAtCompletion() {
        Map<String, Object> baseline = variables(List.of(Map.of("lineKey", "old")));
        Map<String, Object> deleted = new HashMap<>(variables(List.of()));
        SubTableBatchDeletedRows.record(deleted, baseline, change("DELETE"));
        Map<String, Object> merged = SubTableBatchInitialRows.mergeSnapshot(deleted,
                Map.of(SubTableBatchMetadata.DELETED_ROWS_FIELD, "forged", "__subTables__",
                        Map.of("dw:renamedlines", List.of(Map.of("lineKey", "old"), Map.of("lineKey", "new-service")))),
                new ObjectMapper());
        assertEquals(deleted.get(SubTableBatchMetadata.DELETED_ROWS_FIELD), merged.get(SubTableBatchMetadata.DELETED_ROWS_FIELD));
        assertEquals(List.of(Map.of("lineKey", "new-service")), ((Map<?, ?>) merged.get("__subTables__")).get("dw:renamedlines"));
        Map<String, Object> client = new HashMap<>(merged);
        SubTableWriteIsolation.stripTransportMetadata(client);
        assertFalse(client.containsKey(SubTableBatchMetadata.DELETED_ROWS_FIELD));
        assertThrows(com.portal.exception.PortalException.class, () -> SubTableBatchDeletedRows.read(
                Map.of(SubTableBatchMetadata.DELETED_ROWS_FIELD, "invalid")));
    }
}
