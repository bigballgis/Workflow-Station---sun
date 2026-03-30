package com.portal.property;

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

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 20: Task completion does not affect other tasks' snapshots
 *
 * For any two Task_Instances in the same Process_Instance, completing one task and
 * capturing its snapshot should not modify the other task's existing snapshot (if completed)
 * or affect the other task's live data resolution (if active).
 *
 * Validates: Requirements 11.5
 */
public class TaskSnapshotIsolationPropertyTest {

    /**
     * Property 20: Completing task A does not modify task B's snapshot.
     *
     * Validates: Requirements 11.5
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 20: Task completion does not affect other tasks' snapshots")
    void taskCompletionDoesNotAffectOtherSnapshots(
            @ForAll("twoTaskConfigs") TwoTaskConfig config) {

        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);
        ProcessFormComponent processFormComponent = mock(ProcessFormComponent.class);

        TaskFormComponent component = new TaskFormComponent(
                processFormComponent, changeHistoryComponent, processInstanceRepository) {
            @Override
            protected TaskInfo getTaskInfo(String taskId) {
                if (taskId.equals(config.taskAId)) {
                    return new TaskInfo(config.taskAStage, config.processInstanceId);
                } else {
                    return new TaskInfo(config.taskBStage, config.processInstanceId);
                }
            }
        };
        ReflectionTestUtils.setField(component, "developerWorkstationUrl", "http://mock-dw:8091");

        // Build process variables with task B's existing snapshot
        Map<String, Object> processVariables = new HashMap<>(config.processVariables);

        // Task B already has a snapshot
        String taskBSnapshotKey = "_snapshot_" + config.taskBId;
        Map<String, Object> taskBSnapshotMap = component.snapshotToMap(
                TaskFormSnapshot.builder()
                        .taskId(config.taskBId)
                        .taskDefinitionKey(config.taskBStage)
                        .assignee("user_b")
                        .completedAt(Instant.now().minusSeconds(3600))
                        .fieldValues(config.taskBSnapshotValues)
                        .build());
        processVariables.put(taskBSnapshotKey, taskBSnapshotMap);

        // Save a copy of task B's snapshot for comparison
        Map<String, Object> taskBSnapshotBefore = new HashMap<>(taskBSnapshotMap);

        ProcessInstance processInstance = ProcessInstance.builder()
                .id(config.processInstanceId)
                .processDefinitionKey("test-process")
                .startUserId("user-001")
                .status("RUNNING")
                .variables(processVariables)
                .build();

        when(processInstanceRepository.findById(config.processInstanceId))
                .thenReturn(Optional.of(processInstance));
        when(processInstanceRepository.save(any(ProcessInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Capture snapshot for task A
        component.captureTaskFormSnapshot(config.taskAId, "user_a");

        // Verify the saved process instance
        ArgumentCaptor<ProcessInstance> captor = ArgumentCaptor.forClass(ProcessInstance.class);
        verify(processInstanceRepository).save(captor.capture());

        ProcessInstance savedInstance = captor.getValue();
        Map<String, Object> savedVariables = savedInstance.getVariables();

        // Core property: task A's snapshot exists
        String taskASnapshotKey = "_snapshot_" + config.taskAId;
        assertThat(savedVariables)
                .as("Task A's snapshot should exist")
                .containsKey(taskASnapshotKey);

        // Core property: task B's snapshot is unchanged
        assertThat(savedVariables)
                .as("Task B's snapshot should still exist")
                .containsKey(taskBSnapshotKey);

        @SuppressWarnings("unchecked")
        Map<String, Object> taskBSnapshotAfter = (Map<String, Object>) savedVariables.get(taskBSnapshotKey);

        assertThat(taskBSnapshotAfter)
                .as("Task B's snapshot should be unchanged after task A's completion")
                .isEqualTo(taskBSnapshotBefore);

        // Core property: task B's snapshot field values are preserved
        TaskFormSnapshot taskBRestored = component.mapToSnapshot(taskBSnapshotAfter);
        assertThat(taskBRestored.getTaskId()).isEqualTo(config.taskBId);
        assertThat(taskBRestored.getFieldValues()).isEqualTo(config.taskBSnapshotValues);

        // Core property: original process variables (non-snapshot) are preserved
        for (Map.Entry<String, Object> entry : config.processVariables.entrySet()) {
            assertThat(savedVariables)
                    .as("Original variable '%s' should be preserved", entry.getKey())
                    .containsEntry(entry.getKey(), entry.getValue());
        }
    }

    // ========== Data class ==========

    static class TwoTaskConfig {
        String processInstanceId;
        String taskAId;
        String taskAStage;
        String taskBId;
        String taskBStage;
        Map<String, Object> processVariables;
        Map<String, Object> taskBSnapshotValues;
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<TwoTaskConfig> twoTaskConfigs() {
        Arbitrary<String> processIds = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15)
                .map(s -> "proc_" + s);
        Arbitrary<Integer> fieldCounts = Arbitraries.integers().between(1, 5);

        return Combinators.combine(processIds, fieldCounts)
                .as((procId, count) -> {
                    TwoTaskConfig config = new TwoTaskConfig();
                    config.processInstanceId = procId;
                    config.taskAId = "taskA_" + procId;
                    config.taskAStage = "stage_review";
                    config.taskBId = "taskB_" + procId;
                    config.taskBStage = "stage_approve";

                    config.processVariables = new HashMap<>();
                    config.taskBSnapshotValues = new HashMap<>();
                    for (int i = 0; i < count; i++) {
                        config.processVariables.put("field_" + i, "current_" + i);
                        config.taskBSnapshotValues.put("field_" + i, "snapshot_b_" + i);
                    }
                    return config;
                });
    }
}
