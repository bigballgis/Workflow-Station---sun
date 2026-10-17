package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcessStartBatchFirstOutcomeTest {
    private final ProcessStartComponent component = mock(ProcessStartComponent.class, CALLS_REAL_METHODS);
    private final WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
    private final MiCollectionVariableBuilder builder = mock(MiCollectionVariableBuilder.class);
    private final ProcessInstanceRepository instances = mock(ProcessInstanceRepository.class);
    private final TaskFormComponent forms = mock(TaskFormComponent.class);
    private final List<SubTableBatchInitialMiOutcome.Expected> expected = List.of(
            new SubTableBatchInitialMiOutcome.Expected("child", true, "synthetic_lines", List.of(Map.of("line_key", "one")), List.of()));

    @BeforeEach void setUp() {
        ReflectionTestUtils.setField(component, "workflowEngineClient", engine);
        ReflectionTestUtils.setField(component, "miCollectionVariableBuilder", builder);
        ReflectionTestUtils.setField(component, "processInstanceRepository", instances);
        ReflectionTestUtils.setField(component, "ownerFieldComponent", mock(OwnerFieldComponent.class));
        ReflectionTestUtils.setField(component, "taskFormComponent", forms);
        ProcessInstance instance = new ProcessInstance(); instance.setProcessDefinitionId("frozen-definition");
        instance.setVariables(Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of("schemaVersion", 1)));
        when(instances.findById("synthetic-instance")).thenReturn(Optional.of(instance));
        when(engine.getProcessInstanceTasks("synthetic-instance")).thenReturn(Optional.of(Map.of("tasks", List.of(firstTask()))))
                .thenReturn(Optional.of(Map.of("tasks", List.of(Map.of("taskId", "child-task", "taskName", "Child")))));
        when(engine.claimTask("intake-task", "synthetic-user")).thenReturn(Optional.of(Map.of("success", true)));
        when(builder.injectMiCollectionForFirstTask(anyString(), anyString(), anyString(), anyMap(), anyMap())).thenReturn(expected);
    }

    private static Map<String, Object> firstTask() {
        return Map.of("taskId", "intake-task", "taskName", "Intake", "taskDefinitionKey", "intake",
                "processDefinitionKey", "synthetic-process", "bpmnAssigneeType", "INITIATOR");
    }
    private Object complete() {
        return ReflectionTestUtils.invokeMethod(component, "autoCompleteFirstTask", "synthetic-instance", "synthetic-user",
                new HashMap<String, Object>(), new ProcessStartComponent.ActiveCatalogPin("catalog", "synthetic-process", "1"));
    }

    @Test void onlySuccessfulEngineCompletionRetainsTheFrozenObservationPlan() {
        when(engine.completeTask(eq("intake-task"), eq("synthetic-user"), eq("SUBMIT"), anyMap()))
                .thenReturn(Optional.of(Map.of("success", true)));
        Object outcome = complete();
        assertThat(ReflectionTestUtils.getField(outcome, "assignmentPlan")).isEqualTo(
                new SubTableBatchInitialMiOutcome.Plan("synthetic-instance", "frozen-definition", expected));
        assertThat(ReflectionTestUtils.getField(outcome, "firstStepError")).isNull();
        verify(engine).completeTask(eq("intake-task"), eq("synthetic-user"), eq("SUBMIT"), anyMap());
        // Observation is deferred until the start's subsequent bookkeeping transaction has committed.
        verify(engine, never()).getMultiInstanceStatus(anyString());
    }
    @Test void rejectedCompletionDoesNotPublishThePreparedPlanOrRetry() {
        when(engine.completeTask(eq("intake-task"), eq("synthetic-user"), eq("SUBMIT"), anyMap()))
                .thenReturn(Optional.of(Map.of("success", false, "message", "Synthetic rejection")));
        Object outcome = complete();
        assertThat(ReflectionTestUtils.getField(outcome, "assignmentPlan")).isNull();
        assertThat(ReflectionTestUtils.getField(outcome, "firstStepError")).isEqualTo("Synthetic rejection");
        verify(engine).completeTask(eq("intake-task"), eq("synthetic-user"), eq("SUBMIT"), anyMap());
    }
    @Test void postCompletionSnapshotFailureCannotTurnCreatedChildrenIntoARetryableFirstStepError() {
        when(engine.completeTask(eq("intake-task"), eq("synthetic-user"), eq("SUBMIT"), anyMap()))
                .thenReturn(Optional.of(Map.of("success", true)));
        doThrow(new IllegalStateException("Synthetic snapshot failure")).when(forms)
                .captureTaskFormSnapshot(eq("intake-task"), eq("synthetic-user"), eq("intake"), eq("synthetic-instance"), anyMap());
        Object outcome = complete();
        assertThat(ReflectionTestUtils.getField(outcome, "assignmentPlan")).isNotNull();
        assertThat(ReflectionTestUtils.getField(outcome, "firstStepError")).isNull();
        verify(engine).completeTask(eq("intake-task"), eq("synthetic-user"), eq("SUBMIT"), anyMap());
        verify(engine, never()).getMultiInstanceStatus(anyString());
    }
    @Test void unavailableCompletionDoesNotTreatPreparationAsTaskCreation() {
        when(engine.completeTask(eq("intake-task"), eq("synthetic-user"), eq("SUBMIT"), anyMap()))
                .thenThrow(new IllegalStateException("Synthetic transport failure"));
        Object outcome = complete();
        assertThat(ReflectionTestUtils.getField(outcome, "assignmentPlan")).isNull();
        assertThat(ReflectionTestUtils.getField(outcome, "firstStepError")).isNotNull();
    }
    @Test void failedPreflightCannotCompleteOrObserveAnyChildTasks() {
        when(builder.injectMiCollectionForFirstTask(anyString(), anyString(), anyString(), anyMap(), anyMap()))
                .thenThrow(new IllegalStateException("Synthetic target invalid"));
        Object outcome = complete();
        assertThat(ReflectionTestUtils.getField(outcome, "assignmentPlan")).isNull();
        verify(engine, never()).completeTask(anyString(), anyString(), anyString(), anyMap());
        verify(engine, never()).getMultiInstanceStatus(anyString());
    }
    @Test void firstApprovalTaskStaysOpenWithoutAnAssignmentObservationPlan() {
        var task = new HashMap<>(firstTask()); task.put("bpmnAssigneeType", "BU_ROLE");
        when(engine.getProcessInstanceTasks("synthetic-instance")).thenReturn(Optional.of(Map.of("tasks", List.of(task))));
        assertThat(ReflectionTestUtils.getField(complete(), "assignmentPlan")).isNull();
        verifyNoInteractions(builder, instances);
        verify(engine, never()).completeTask(anyString(), anyString(), anyString(), anyMap());
    }
}
