package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.SubTableBatchChange;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubTableBatchAssignmentStageGuardTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void permitsOnlyWhenTheFrozenTargetHasNotStarted() {
        WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
        when(engine.getProcessInstanceTasks("process-1"))
                .thenReturn(Optional.of(Map.of("tasks", List.of(Map.of("taskDefinitionKey", "other-task", "processInstanceId", "process-1")))));
        when(engine.getTaskHistory("process-1")).thenReturn(Optional.of(hostHistory()));

        SubTableBatchAssignmentStageGuard.validate("host-task", "process-1", variables(), form(),
                List.of(assignChange()), objectMapper, engine);
    }

    @Test
    void rejectsWhenTheFrozenTargetTaskAlreadyExists() {
        WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
        when(engine.getProcessInstanceTasks("process-1"))
                .thenReturn(Optional.of(Map.of("tasks", List.of(Map.of("taskDefinitionKey", "mi-task", "processInstanceId", "process-1")))));
        when(engine.getTaskHistory("process-1")).thenReturn(Optional.of(hostHistory()));

        assertThatThrownBy(() -> SubTableBatchAssignmentStageGuard.validate(
                "host-task", "process-1", variables(), form(), List.of(assignChange()), objectMapper, engine))
                .hasMessageContaining("already started");
    }

    @Test
    void failsClosedWhenEngineTaskStateCannotBeRead() {
        WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
        when(engine.getProcessInstanceTasks("process-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> SubTableBatchAssignmentStageGuard.validate(
                "host-task", "process-1", variables(), form(), List.of(assignChange()), objectMapper, engine))
                .hasMessageContaining("cannot be verified");
    }

    @Test
    void rejectsTheCurrentTaskAsAnAlreadyStartedTargetWithoutUsingRowHints() {
        WorkflowEngineClient engine = mock(WorkflowEngineClient.class);

        assertThatThrownBy(() -> SubTableBatchAssignmentStageGuard.validate(
                "mi-task", "process-1", variables(), form(), List.of(assignChange()), objectMapper, engine))
                .hasMessageContaining("already started");
        verify(engine, never()).getProcessInstanceTasks(anyString());
        verify(engine, never()).getTaskHistory(anyString());
    }

    @Test
    void readsTheProcessStateOnceForMultipleAssignmentPatches() {
        WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
        when(engine.getProcessInstanceTasks("process-1")).thenReturn(Optional.of(Map.of("tasks", List.of())));
        when(engine.getTaskHistory("process-1")).thenReturn(Optional.of(hostHistory()));

        SubTableBatchAssignmentStageGuard.validate("host-task", "process-1", variables(), form(),
                List.of(assignChange(), assignChange()), objectMapper, engine);

        verify(engine, times(1)).getProcessInstanceTasks("process-1");
        verify(engine, times(1)).getTaskHistory("process-1");
    }

    @Test
    void rejectsACompletedTargetEvenWhenItIsAbsentFromTheActiveTaskList() {
        WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
        when(engine.getProcessInstanceTasks("process-1")).thenReturn(Optional.of(Map.of("tasks", List.of())));
        when(engine.getTaskHistory("process-1")).thenReturn(Optional.of(List.of(
                hostHistory().get(0),
                Map.of("activityId", "mi-task", "processInstanceId", "process-1", "endTime", "completed"))));

        assertThatThrownBy(() -> SubTableBatchAssignmentStageGuard.validate(
                "host-task", "process-1", variables(), form(), List.of(assignChange()), objectMapper, engine))
                .hasMessageContaining("already started");
    }

    @Test
    void rejectsACancelledTargetRatherThanTreatingItAsAnUncreatedTask() {
        WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
        when(engine.getProcessInstanceTasks("process-1")).thenReturn(Optional.of(Map.of("tasks", List.of())));
        when(engine.getTaskHistory("process-1")).thenReturn(Optional.of(List.of(
                hostHistory().get(0),
                Map.of("activityId", "mi-task", "processInstanceId", "process-1", "deleteReason", "cancelled"))));

        assertThatThrownBy(() -> SubTableBatchAssignmentStageGuard.validate(
                "host-task", "process-1", variables(), form(), List.of(assignChange()), objectMapper, engine))
                .hasMessageContaining("already started");
    }

    @Test
    void failsClosedOnUnavailableOrEmptyHistory() {
        WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
        when(engine.getProcessInstanceTasks("process-1")).thenReturn(Optional.of(Map.of("tasks", List.of())));
        when(engine.getTaskHistory("process-1")).thenReturn(Optional.empty(), Optional.of(List.of()));
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> SubTableBatchAssignmentStageGuard.validate(
                    "host-task", "process-1", variables(), form(), List.of(assignChange()), objectMapper, engine))
                    .hasMessageContaining("cannot be verified");
        }
    }

    @Test
    void rejectsHistoryForAnotherInstanceOrWithoutExactNodeIdentity() {
        WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
        when(engine.getProcessInstanceTasks("process-1")).thenReturn(Optional.of(Map.of("tasks", List.of())));
        when(engine.getTaskHistory("process-1")).thenReturn(
                Optional.of(List.of(Map.of("activityId", "host-task", "processInstanceId", "other-process"))),
                Optional.of(List.of(Map.of("taskName", "host-task", "processInstanceId", "process-1"))));
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> SubTableBatchAssignmentStageGuard.validate(
                    "host-task", "process-1", variables(), form(), List.of(assignChange()), objectMapper, engine))
                    .hasMessageContaining("identity cannot be verified");
        }
    }

    @Test
    void doesNotQueryHistoryForAnOrdinaryEdit() {
        WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
        SubTableBatchChange edit = assignChange();
        edit.setOperation("EDIT");
        SubTableBatchAssignmentStageGuard.validate("host-task", "process-1", variables(), form(),
                List.of(edit), objectMapper, engine);
        verify(engine, never()).getProcessInstanceTasks(anyString());
        verify(engine, never()).getTaskHistory(anyString());
    }

    @Test
    void requiresExplicitActiveHostHistoryRatherThanMissingLifecycleFields() {
        WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
        when(engine.getProcessInstanceTasks("process-1")).thenReturn(Optional.of(Map.of("tasks", List.of())));
        Map<String, Object> endedHost = new java.util.LinkedHashMap<>(hostHistory().get(0));
        endedHost.put("endTime", "completed");
        when(engine.getTaskHistory("process-1")).thenReturn(
                Optional.of(List.of(Map.of("activityId", "host-task", "processInstanceId", "process-1"))),
                Optional.of(List.of(endedHost)),
                Optional.of(List.of(Map.of("activityId", "different-host", "processInstanceId", "process-1"))));
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> SubTableBatchAssignmentStageGuard.validate(
                    "host-task", "process-1", variables(), form(), List.of(assignChange()), objectMapper, engine))
                    .hasMessageContaining("host history cannot be verified");
        }
    }

    @Test
    void rejectsMalformedActiveTaskIdentitiesRatherThanSkippingThem() {
        WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
        when(engine.getProcessInstanceTasks("process-1"))
                .thenReturn(Optional.of(Map.of("tasks", List.of("not-a-task"))));
        assertThatThrownBy(() -> SubTableBatchAssignmentStageGuard.validate(
                "host-task", "process-1", variables(), form(), List.of(assignChange()), objectMapper, engine))
                .hasMessageContaining("identity cannot be verified");
        verify(engine, never()).getTaskHistory(anyString());
    }

    private static List<Map<String, Object>> hostHistory() {
        Map<String, Object> host = new java.util.LinkedHashMap<>();
        host.put("activityId", "host-task");
        host.put("processInstanceId", "process-1");
        host.put("endTime", null);
        host.put("deleteReason", null);
        return List.of(host);
    }

    private static Map<String, Object> variables() {
        return Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of("schemaVersion", 1,
                "forms", Map.of("form-1", Map.of("controls", List.of(Map.of(
                        "batchControlId", "control-1",
                        "bindingId", "101",
                        "capabilities", Map.of("allowBatchAssign", true),
                        "originalCrud", Map.of("allowEdit", true),
                        "assignmentConfig", Map.of("taskDefinitionKey", "mi-task")))))));
    }

    private static Map<String, Object> form() {
        return Map.of("sourceId", "form-1");
    }

    private static SubTableBatchChange assignChange() {
        SubTableBatchChange change = new SubTableBatchChange();
        change.setBatchControlId("control-1");
        change.setBindingId("101");
        change.setOperation("ASSIGN");
        return change;
    }
}
