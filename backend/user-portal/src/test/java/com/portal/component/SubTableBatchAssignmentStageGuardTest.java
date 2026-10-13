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
                .thenReturn(Optional.of(Map.of("tasks", List.of(Map.of("taskDefinitionKey", "other-task")))));

        SubTableBatchAssignmentStageGuard.validate("host-task", "process-1", variables(), form(),
                List.of(assignChange()), objectMapper, engine);
    }

    @Test
    void rejectsWhenTheFrozenTargetTaskAlreadyExists() {
        WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
        when(engine.getProcessInstanceTasks("process-1"))
                .thenReturn(Optional.of(Map.of("tasks", List.of(Map.of("taskDefinitionKey", "mi-task")))));

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
    }

    @Test
    void readsTheProcessStateOnceForMultipleAssignmentPatches() {
        WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
        when(engine.getProcessInstanceTasks("process-1")).thenReturn(Optional.of(Map.of("tasks", List.of())));

        SubTableBatchAssignmentStageGuard.validate("host-task", "process-1", variables(), form(),
                List.of(assignChange(), assignChange()), objectMapper, engine);

        verify(engine, times(1)).getProcessInstanceTasks("process-1");
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
