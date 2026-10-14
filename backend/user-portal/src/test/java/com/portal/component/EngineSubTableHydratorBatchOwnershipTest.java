package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EngineSubTableHydratorBatchOwnershipTest {
    @Test
    void enablingBatchOnAnInitiallyEmptyStoreMustNotSuppressServiceOutput() {
        WorkflowEngineClient client = mock(WorkflowEngineClient.class);
        when(client.getProcessInstance("synthetic-process")).thenReturn(Optional.of(Map.of("variables",
                Map.of("__subTables__", Map.of("dw:lines", List.of(Map.of("key", "new-service-row")))))));
        Map<String, Object> variables = Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD,
                Map.of("forms", Map.of("form", Map.of("controls", List.of(
                        Map.of("rowDesign", Map.of("binding", Map.of("tableName", "lines"))))))));
        assertTrue(new EngineSubTableHydrator(client).mergeFromEngine("synthetic-process",
                Map.of("dw:lines", List.of()), SubTableBatchDeletedRows.read(variables)).isPresent());
    }

    @Test
    void savedEmptyBatchStoreCannotBeResurrectedButOtherStoresStillHydrate() {
        WorkflowEngineClient client = mock(WorkflowEngineClient.class);
        Map<String, Object> engineTables = Map.of("dw:lines", List.of(Map.of("key", "old")),
                "dw:service_output", List.of(Map.of("key", "generated")));
        when(client.getProcessInstance("synthetic-process")).thenReturn(Optional.of(Map.of("variables", Map.of("__subTables__", engineTables))));
        Map<String, Object> current = Map.of("dw:lines", List.of());
        Map<String, Object> result = new EngineSubTableHydrator(client)
                .mergeFromEngine("synthetic-process", current, Map.of("dw:lines",
                        new SubTableBatchDeletedRows.Store(List.of("key"), Set.of(List.of("old")))))
                .orElseThrow().mergedSubTables();
        assertEquals(List.of(), result.get("dw:lines"));
        assertEquals(engineTables.get("dw:service_output"), result.get("dw:service_output"));
        assertEquals(List.of(), current.get("dw:lines"));
        assertEquals(engineTables, new EngineSubTableHydrator(client)
                .mergeFromEngine("synthetic-process", current).orElseThrow().mergedSubTables(), "Legacy hydration remains unchanged");
        assertEquals(engineTables, new EngineSubTableHydrator(client)
                .mergeFromEngine("synthetic-process", Map.of(), Map.of()).orElseThrow().mergedSubTables(),
                "A genuinely absent store can still hydrate service-task output");
    }

    @Test
    void deletedRowsAreFilteredButNewServiceRowsStillHydrateTheSameStore() {
        WorkflowEngineClient client = mock(WorkflowEngineClient.class);
        when(client.getProcessInstance("synthetic-process")).thenReturn(Optional.of(Map.of("variables",
                Map.of("__subTables__", Map.of("dw:lines", List.of(Map.of("renamedKey", "old"),
                        Map.of("renamedKey", "new-service-row")))))));
        Map<String, Object> result = new EngineSubTableHydrator(client).mergeFromEngine("synthetic-process",
                Map.of("dw:lines", List.of()), Map.of("dw:lines",
                        new SubTableBatchDeletedRows.Store(List.of("renamedKey"), Set.of(List.of("old")))))
                .orElseThrow().mergedSubTables();
        assertEquals(List.of(Map.of("renamedKey", "new-service-row")), result.get("dw:lines"));
    }
}
