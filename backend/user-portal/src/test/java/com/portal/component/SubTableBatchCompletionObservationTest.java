package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubTableBatchCompletionObservationTest {
    private final WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
    private final SubTableBatchInitialMiOutcome.Plan plan = new SubTableBatchInitialMiOutcome.Plan("synthetic-instance", "synthetic-definition",
            List.of(new SubTableBatchInitialMiOutcome.Expected("configured-node", true, "configured_table", List.of(Map.of("configured_pk", "own")))));
    private Map<String, Object> live(String id) {
        return new HashMap<>(Map.of("taskId", id, "processInstanceId", "synthetic-instance",
                "processDefinitionId", "synthetic-definition", "taskDefinitionKey", "configured-node"));
    }
    private Map<String, Object> identity(String id, String key) {
        return new HashMap<>(Map.of("taskId", id, "taskDefinitionKey", "configured-node", "subTableName", "configured_table",
                "subTableRowKey", Map.of("configured_pk", key), "assignee", "synthetic-cached-person"));
    }
    private void responses(List<?> live, List<?> identity) {
        when(engine.getProcessInstanceTasks("synthetic-instance")).thenReturn(Optional.of(Map.of("tasks", live, "totalCount", live.size())));
        when(engine.getMultiInstanceStatus("synthetic-instance")).thenReturn(Optional.of(Map.of("processInstanceId", "synthetic-instance", "tasks", identity)));
    }
    @Test void sameNodeForeignScopeCannotBeReportedAsAnUnassignedPersonalTask() {
        responses(List.of(live("foreign-task")), List.of(identity("foreign-task", "foreign")));
        var result = SubTableBatchCompletionObservation.observe(plan, engine);
        assertThat(result.status()).isEqualTo("UNCONFIRMED");
        assertThat(result.toString()).doesNotContain("foreign-task");
    }
    @Test void onlyOwnFrozenRowCanExposeItsActualMissingAssignee() {
        responses(List.of(live("own-task"), live("foreign-task")), List.of(identity("own-task", "own"), identity("foreign-task", "foreign")));
        var result = SubTableBatchCompletionObservation.observe(plan, engine);
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.issues()).extracting(issue -> issue.taskId()).containsExactly("own-task");
        assertThat(result.toString()).doesNotContain("synthetic-cached-person");
        verify(engine).getProcessInstanceTasks("synthetic-instance");
        verify(engine).getMultiInstanceStatus("synthetic-instance");
        verifyNoMoreInteractions(engine);
    }
    @Test void populatedLiveAssigneeCannotBeOverruledByCachedMiAssignment() {
        var live = live("own-task"); live.put("currentAssignee", "synthetic-person");
        var identity = identity("own-task", "own"); identity.remove("assignee");
        responses(List.of(live), List.of(identity));
        assertThat(SubTableBatchCompletionObservation.observe(plan, engine).status()).isEqualTo("UNCONFIRMED");
    }
    @Test void wrongTableNodeOrKeyShapeCannotAuthorizeObservation() {
        for (String field : List.of("taskDefinitionKey", "subTableName", "subTableRowKey")) {
            var identity = identity("own-task", "own"); identity.remove(field);
            responses(List.of(live("own-task")), List.of(identity));
            assertThat(SubTableBatchCompletionObservation.observe(plan, engine).status()).isEqualTo("UNCONFIRMED");
        }
    }
    @Test void duplicateOrMissingIdentityCannotLeakALiveTaskId() {
        for (List<?> identity : List.of(List.of(), List.of(identity("own-task", "own"), identity("own-task", "own")))) {
            responses(List.of(live("own-task")), identity);
            assertThat(SubTableBatchCompletionObservation.observe(plan, engine).toString()).doesNotContain("own-task");
        }
    }
    @Test void failedIdentityQueryCannotUndoACommittedAction() {
        responses(List.of(live("own-task")), List.of());
        when(engine.getMultiInstanceStatus("synthetic-instance")).thenThrow(new IllegalStateException("synthetic-private-error"));
        var result = SubTableBatchCompletionObservation.observe(plan, engine);
        assertThat(result.status()).isEqualTo("UNCONFIRMED");
        assertThat(result.toString()).doesNotContain("synthetic-private-error", "own-task");
    }
    @Test void incompleteLiveQueryDoesNotTryToProveRowsFromIdentityData() {
        when(engine.getProcessInstanceTasks("synthetic-instance")).thenReturn(Optional.of(Map.of("tasks", List.of(live("own-task")), "totalCount", 200)));
        assertThat(SubTableBatchCompletionObservation.observe(plan, engine).status()).isEqualTo("UNCONFIRMED");
        verify(engine, never()).getMultiInstanceStatus(any());
    }
    @Test void legacyPlanDoesNotReadEitherEndpoint() {
        assertThat(SubTableBatchCompletionObservation.observe(null, engine)).isNull();
        verifyNoInteractions(engine);
    }
}
