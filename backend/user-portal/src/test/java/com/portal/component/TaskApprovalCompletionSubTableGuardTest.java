package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the completion write-path against wiping a service task's {@code __subTables__} output:
 * a bare approval whose outbound variables carry an empty sub-table slice must be refilled from the
 * live engine before {@code completeTask}, never sent as {@code []} (which would overwrite engine rows).
 */
@DisplayName("TaskApprovalCompletionComponent.preserveEngineSubTablesOnComplete")
class TaskApprovalCompletionSubTableGuardTest {

    private static final String PID = "pi-1";

    private TaskApprovalCompletionComponent newComponent(WorkflowEngineClient client) {
        // The guard delegates its fill-empty merge to EngineSubTableHydrator (over the same engine
        // client); the remaining collaborators are unused here.
        return new TaskApprovalCompletionComponent(
                client, new EngineSubTableHydrator(client), null, null, null, null, null);
    }

    private List<Map<String, Object>> fourRows() {
        return List.of(
                Map.of("id", "csv-1", "name", "Alice Chan"),
                Map.of("id", "csv-2", "name", "Bob Wong"),
                Map.of("id", "csv-3", "name", "Carol Lee"),
                Map.of("id", "csv-4", "name", "David Ng"));
    }

    private WorkflowEngineClient engineWithRows(Map<String, Object> subTables) {
        WorkflowEngineClient client = mock(WorkflowEngineClient.class);
        when(client.getProcessInstance(anyString()))
                .thenReturn(Optional.of(Map.of("variables", Map.of("__subTables__", subTables))));
        return client;
    }

    @Test
    @DisplayName("fills an empty outbound slice from the live engine (the bare-approval wipe scenario)")
    @SuppressWarnings("unchecked")
    void fillsEmptySliceFromEngine() {
        WorkflowEngineClient client = engineWithRows(Map.of("50111", fourRows()));

        Map<String, Object> variables = new LinkedHashMap<>();
        Map<String, Object> outSubTables = new LinkedHashMap<>();
        outSubTables.put("50111", List.of()); // empty slice — would overwrite the engine's 4 rows with []
        variables.put("__subTables__", outSubTables);

        newComponent(client).preserveEngineSubTablesOnComplete(PID, variables);

        Map<String, Object> after = (Map<String, Object>) variables.get("__subTables__");
        List<?> slice = (List<?>) after.get("50111");
        assertEquals(4, slice.size(), "empty slice must be refilled from the engine");
    }

    @Test
    @DisplayName("leaves a non-empty outbound slice untouched and does not call the engine")
    @SuppressWarnings("unchecked")
    void leavesNonEmptySliceUntouched() {
        WorkflowEngineClient client = mock(WorkflowEngineClient.class);

        Map<String, Object> variables = new LinkedHashMap<>();
        Map<String, Object> outSubTables = new LinkedHashMap<>();
        outSubTables.put("50111", List.of(Map.of("id", "user-edit-1")));
        variables.put("__subTables__", outSubTables);

        newComponent(client).preserveEngineSubTablesOnComplete(PID, variables);

        // No empty slice → no round-trip, user edits preserved verbatim.
        verify(client, never()).getProcessInstance(anyString());
        List<?> slice = (List<?>) ((Map<String, Object>) variables.get("__subTables__")).get("50111");
        assertEquals(1, slice.size());
    }

    @Test
    @DisplayName("no __subTables__ in outbound variables is a no-op (engine keeps its own value)")
    void absentSubTablesIsNoOp() {
        WorkflowEngineClient client = mock(WorkflowEngineClient.class);

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("decision", "yes");

        newComponent(client).preserveEngineSubTablesOnComplete(PID, variables);

        verify(client, never()).getProcessInstance(anyString());
        assertEquals(Map.of("decision", "yes"), variables);
    }

    @Test
    @DisplayName("fills only the empty slice, keeps a sibling non-empty slice as submitted")
    @SuppressWarnings("unchecked")
    void fillsOnlyEmptySliceInMixedMap() {
        Map<String, Object> engineSubTables = new LinkedHashMap<>();
        engineSubTables.put("50111", fourRows());
        engineSubTables.put("50222", fourRows()); // engine also has rows here, but the user edited it
        WorkflowEngineClient client = engineWithRows(engineSubTables);

        Map<String, Object> variables = new LinkedHashMap<>();
        Map<String, Object> outSubTables = new LinkedHashMap<>();
        outSubTables.put("50111", List.of());                       // empty → should be filled to 4
        outSubTables.put("50222", List.of(Map.of("id", "kept-1"))); // non-empty → must stay as-is
        variables.put("__subTables__", outSubTables);

        newComponent(client).preserveEngineSubTablesOnComplete(PID, variables);

        Map<String, Object> after = (Map<String, Object>) variables.get("__subTables__");
        assertEquals(4, ((List<?>) after.get("50111")).size(), "empty slice filled from engine");
        List<?> kept = (List<?>) after.get("50222");
        assertEquals(1, kept.size(), "non-empty slice must not be overwritten by engine rows");
        assertInstanceOf(Map.class, kept.get(0));
        assertEquals("kept-1", ((Map<String, Object>) kept.get(0)).get("id"));
    }
}
