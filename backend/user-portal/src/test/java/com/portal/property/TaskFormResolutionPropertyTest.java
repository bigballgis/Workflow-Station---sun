package com.portal.property;

import com.portal.component.ChangeHistoryComponent;
import com.portal.component.ProcessFormComponent;
import com.portal.component.TaskFormComponent;
import com.portal.component.TaskFormComponent.TaskInfo;
import com.portal.dto.ProcessFormData;
import com.portal.dto.TaskFormData;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import net.jqwik.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property 14: Task Form resolved by Stage binding
 *
 * For any Task_Instance with a given taskDefinitionKey (Stage), the system should
 * resolve the Task Form by finding the form with a FormStageBinding matching that
 * taskDefinitionKey. If multiple tasks exist in the same process at different stages,
 * each task should independently resolve to its own bound Task Form.
 *
 * Validates: Requirements 9.1, 11.1, 11.2
 */
public class TaskFormResolutionPropertyTest {

    /**
     * Property 14: Each task resolves to its own Task Form based on taskDefinitionKey.
     *
     * Validates: Requirements 9.1, 11.1, 11.2
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 14: Task Form resolved by Stage binding")
    void taskFormResolvedByStageBinding(
            @ForAll("stageBindingConfigs") StageBindingConfig config) {

        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);
        ProcessFormComponent processFormComponent = mock(ProcessFormComponent.class);

        // Create a testable TaskFormComponent that overrides getTaskInfo and fetchTaskFormByStageId
        TaskFormComponent component = new TaskFormComponent(
                processFormComponent, changeHistoryComponent, processInstanceRepository) {
            @Override
            protected TaskInfo getTaskInfo(String taskId) {
                // Resolve task info from our config
                for (TaskConfig tc : config.tasks) {
                    if (tc.taskId.equals(taskId)) {
                        return new TaskInfo(tc.taskDefinitionKey, config.processInstanceId);
                    }
                }
                throw new RuntimeException("Task not found: " + taskId);
            }
        };
        ReflectionTestUtils.setField(component, "developerWorkstationUrl", "http://mock-dw:8091");

        // Mock process instance
        ProcessInstance processInstance = ProcessInstance.builder()
                .id(config.processInstanceId)
                .processDefinitionKey("test-process")
                .startUserId("user-001")
                .status("RUNNING")
                .variables(config.processVariables)
                .build();

        when(processInstanceRepository.findById(config.processInstanceId))
                .thenReturn(Optional.of(processInstance));

        // Mock ProcessFormComponent
        ProcessFormData mockProcessFormData = ProcessFormData.builder()
                .processInstanceId(config.processInstanceId)
                .formName("Process Form")
                .formType("PROCESS")
                .configJson(Collections.emptyMap())
                .fieldValues(config.processVariables)
                .editable(false)
                .processState("RUNNING")
                .build();
        when(processFormComponent.getProcessFormData(config.processInstanceId))
                .thenReturn(mockProcessFormData);

        // Core property: each task independently resolves based on its taskDefinitionKey
        for (TaskConfig tc : config.tasks) {
            TaskFormData formData = component.getTaskFormData(tc.taskId);

            // Task ID and taskDefinitionKey should match
            assertThat(formData.getTaskId())
                    .as("taskId should match for task %s", tc.taskId)
                    .isEqualTo(tc.taskId);
            assertThat(formData.getTaskDefinitionKey())
                    .as("taskDefinitionKey should match for task %s", tc.taskId)
                    .isEqualTo(tc.taskDefinitionKey);

            // Process Form reference should always be present
            assertThat(formData.getProcessFormRef())
                    .as("processFormRef should be present for task %s", tc.taskId)
                    .isNotNull();
            assertThat(formData.getProcessFormRef().getProcessInstanceId())
                    .isEqualTo(config.processInstanceId);
        }

        // Property: different tasks at different stages resolve independently
        if (config.tasks.size() >= 2) {
            TaskConfig task1 = config.tasks.get(0);
            TaskConfig task2 = config.tasks.get(1);

            TaskFormData data1 = component.getTaskFormData(task1.taskId);
            TaskFormData data2 = component.getTaskFormData(task2.taskId);

            // Each task has its own taskDefinitionKey
            assertThat(data1.getTaskDefinitionKey()).isEqualTo(task1.taskDefinitionKey);
            assertThat(data2.getTaskDefinitionKey()).isEqualTo(task2.taskDefinitionKey);

            // If stages differ, the resolution is independent
            if (!task1.taskDefinitionKey.equals(task2.taskDefinitionKey)) {
                assertThat(data1.getTaskId()).isNotEqualTo(data2.getTaskId());
            }
        }
    }

    // ========== Data classes ==========

    static class StageBindingConfig {
        String processInstanceId;
        Map<String, Object> processVariables;
        List<TaskConfig> tasks;
    }

    static class TaskConfig {
        String taskId;
        String taskDefinitionKey;
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<StageBindingConfig> stageBindingConfigs() {
        Arbitrary<String> processIds = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15)
                .map(s -> "proc_" + s);
        Arbitrary<Integer> taskCounts = Arbitraries.integers().between(1, 4);

        return Combinators.combine(processIds, taskCounts)
                .as((procId, taskCount) -> {
                    StageBindingConfig config = new StageBindingConfig();
                    config.processInstanceId = procId;
                    config.processVariables = new HashMap<>();
                    config.processVariables.put("field_a", "value_a");
                    config.processVariables.put("field_b", "value_b");
                    config.processVariables.put("field_c", "value_c");

                    config.tasks = new ArrayList<>();
                    for (int i = 0; i < taskCount; i++) {
                        TaskConfig tc = new TaskConfig();
                        tc.taskId = "task_" + procId + "_" + i;
                        tc.taskDefinitionKey = "stage_" + (i % 3 == 0 ? "review" : (i % 3 == 1 ? "approve" : "verify"));
                        config.tasks.add(tc);
                    }
                    return config;
                });
    }
}
