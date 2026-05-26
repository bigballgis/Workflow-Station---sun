package com.portal.property;

import com.portal.client.WorkflowEngineClient;
import com.portal.component.ChangeHistoryComponent;
import com.portal.component.ProcessFormComponent;
import com.portal.component.TaskFormComponent;
import com.portal.component.TaskFormComponent.TaskInfo;
import com.portal.dto.TaskFormSnapshot;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 17: Task Form snapshot captured on completion
 *
 * For any Task_Instance that is completed, a Task_Form_Snapshot should exist in the
 * process variables containing the correct taskId, assignee, completedAt (non-null),
 * and fieldValues matching the process variable values at the time of completion
 * for the Task Form's field subset.
 *
 * Validates: Requirements 10.1, 10.8
 */
public class TaskFormSnapshotPropertyTest {

    /**
     * Property 17: Snapshot contains correct taskId, assignee, completedAt, and fieldValues.
     *
     * Validates: Requirements 10.1, 10.8
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 17: Task Form snapshot captured on completion")
    void snapshotCapturedOnCompletion(
            @ForAll("snapshotConfigs") SnapshotConfig config) {

        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);
        ProcessFormComponent processFormComponent = mock(ProcessFormComponent.class);

        // Create testable component with overridden getTaskInfo and fetchTaskFormByStageId
        TaskFormComponent component = new TaskFormComponent(
                processFormComponent, changeHistoryComponent, processInstanceRepository,
                mock(WorkflowEngineClient.class), mock(RestTemplate.class), new com.fasterxml.jackson.databind.ObjectMapper(), mock(org.springframework.jdbc.core.JdbcTemplate.class), com.portal.testsupport.PortalTransactionTestSupport.noopPlatformTransactionManager()) {
            @Override
            protected TaskInfo getTaskInfo(String taskId) {
                return new TaskInfo(config.taskDefinitionKey, config.processInstanceId);
            }
        };
        ReflectionTestUtils.setField(component, "developerWorkstationUrl", "http://mock-dw:8091");

        // Mock process instance with variables
        ProcessInstance processInstance = ProcessInstance.builder()
                .id(config.processInstanceId)
                .processDefinitionKey("test-process")
                .startUserId("user-001")
                .status("RUNNING")
                .variables(new HashMap<>(config.processVariables))
                .build();

        when(processInstanceRepository.findById(config.processInstanceId))
                .thenReturn(Optional.of(processInstance));
        when(processInstanceRepository.save(any(ProcessInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Call captureTaskFormSnapshot
        component.captureTaskFormSnapshot(config.taskId, config.assignee);

        // Verify process instance was saved with snapshot
        ArgumentCaptor<ProcessInstance> captor = ArgumentCaptor.forClass(ProcessInstance.class);
        verify(processInstanceRepository).save(captor.capture());

        ProcessInstance savedInstance = captor.getValue();
        Map<String, Object> savedVariables = savedInstance.getVariables();

        // Core property: snapshot exists in process variables
        String snapshotKey = "_snapshot_" + config.taskId;
        assertThat(savedVariables)
                .as("Snapshot should be stored in process variables with key %s", snapshotKey)
                .containsKey(snapshotKey);

        // Extract and verify snapshot
        @SuppressWarnings("unchecked")
        Map<String, Object> snapshotMap = (Map<String, Object>) savedVariables.get(snapshotKey);
        TaskFormSnapshot snapshot = component.mapToSnapshot(snapshotMap);

        assertThat(snapshot.getTaskId())
                .as("Snapshot taskId should match")
                .isEqualTo(config.taskId);
        assertThat(snapshot.getTaskDefinitionKey())
                .as("Snapshot taskDefinitionKey should match")
                .isEqualTo(config.taskDefinitionKey);
        assertThat(snapshot.getAssignee())
                .as("Snapshot assignee should match")
                .isEqualTo(config.assignee);
        assertThat(snapshot.getCompletedAt())
                .as("Snapshot completedAt should be non-null")
                .isNotNull();

        // Core property: snapshot must always carry a fieldValues container (never null) so
        // downstream readers can iterate without null guards. When no Task Form binding is found
        // (mocked DW REST call returns null), fieldValues MUST be empty — neither top-level form
        // fields NOR __subTables__ are persisted into the snapshot.
        //
        // Rationale (issue 1397, regression-2): __subTables__ is stored under multiple alias keys
        // (bindingId, String(bindingId), tableName, normalizedTableName) by the frontend. Capturing
        // it inside _snapshot_* without a Task Form binding means every task completion copies the
        // ENTIRE alias-bloated subtable tree into the JSON column, with no UI consumer reading it
        // back. Stacked over multi-instance subtask completions, this triggers PostgreSQL parameter
        // encoding OOM (String.encodeUTF8) during Hibernate UPDATE.
        assertThat(snapshot.getFieldValues())
                .as("Snapshot fieldValues should not be null")
                .isNotNull();
        assertThat(snapshot.getFieldValues())
                .as("With no Task Form binding the snapshot MUST be empty — no top-level fields"
                        + " AND no __subTables__ (which would otherwise inflate via alias copies)")
                .isEmpty();

        // Original process variables should still be present
        for (Map.Entry<String, Object> entry : config.processVariables.entrySet()) {
            if (!entry.getKey().startsWith("_snapshot_")) {
                assertThat(savedVariables)
                        .as("Original variable '%s' should still exist", entry.getKey())
                        .containsKey(entry.getKey());
            }
        }
    }

    /**
     * Property 17: Snapshot serialization round-trip preserves data.
     *
     * Validates: Requirements 10.1, 10.8
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 17: Snapshot serialization round-trip")
    void snapshotSerializationRoundTrip(
            @ForAll("snapshotDtos") TaskFormSnapshot original) {

        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);
        ProcessFormComponent processFormComponent = mock(ProcessFormComponent.class);

        TaskFormComponent component = new TaskFormComponent(
                processFormComponent, changeHistoryComponent, processInstanceRepository,
                mock(WorkflowEngineClient.class), mock(RestTemplate.class), new com.fasterxml.jackson.databind.ObjectMapper(), mock(org.springframework.jdbc.core.JdbcTemplate.class), com.portal.testsupport.PortalTransactionTestSupport.noopPlatformTransactionManager());

        // Convert to map and back
        Map<String, Object> map = component.snapshotToMap(original);
        TaskFormSnapshot restored = component.mapToSnapshot(map);

        // Core property: round-trip preserves all fields
        assertThat(restored.getTaskId()).isEqualTo(original.getTaskId());
        assertThat(restored.getTaskDefinitionKey()).isEqualTo(original.getTaskDefinitionKey());
        assertThat(restored.getAssignee()).isEqualTo(original.getAssignee());
        assertThat(restored.getCompletedAt()).isEqualTo(original.getCompletedAt());
        assertThat(restored.getFieldValues()).isEqualTo(original.getFieldValues());
    }

    // ========== Data class ==========

    static class SnapshotConfig {
        String taskId;
        String taskDefinitionKey;
        String processInstanceId;
        String assignee;
        Map<String, Object> processVariables;
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<SnapshotConfig> snapshotConfigs() {
        Arbitrary<String> taskIds = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15)
                .map(s -> "task_" + s);
        Arbitrary<String> stageIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(s -> "stage_" + s);
        Arbitrary<String> processIds = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15)
                .map(s -> "proc_" + s);
        Arbitrary<String> assignees = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(s -> "user_" + s);
        Arbitrary<Integer> fieldCounts = Arbitraries.integers().between(1, 6);

        return Combinators.combine(taskIds, stageIds, processIds, assignees, fieldCounts)
                .as((taskId, stageId, procId, assignee, count) -> {
                    SnapshotConfig config = new SnapshotConfig();
                    config.taskId = taskId;
                    config.taskDefinitionKey = stageId;
                    config.processInstanceId = procId;
                    config.assignee = assignee;
                    config.processVariables = new HashMap<>();
                    for (int i = 0; i < count; i++) {
                        config.processVariables.put("field_" + i, "value_" + i);
                    }
                    return config;
                });
    }

    @Provide
    Arbitrary<TaskFormSnapshot> snapshotDtos() {
        Arbitrary<String> taskIds = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15)
                .map(s -> "task_" + s);
        Arbitrary<String> stageIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(s -> "stage_" + s);
        Arbitrary<String> assignees = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(s -> "user_" + s);
        Arbitrary<Integer> fieldCounts = Arbitraries.integers().between(0, 5);

        return Combinators.combine(taskIds, stageIds, assignees, fieldCounts)
                .as((taskId, stageId, assignee, count) -> {
                    Map<String, Object> fieldValues = new HashMap<>();
                    for (int i = 0; i < count; i++) {
                        fieldValues.put("field_" + i, "value_" + i);
                    }
                    return TaskFormSnapshot.builder()
                            .taskId(taskId)
                            .taskDefinitionKey(stageId)
                            .assignee(assignee)
                            .completedAt(java.time.Instant.now())
                            .fieldValues(fieldValues)
                            .build();
                });
    }
}
