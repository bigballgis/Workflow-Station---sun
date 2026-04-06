package com.portal.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.client.WorkflowEngineClient;
import com.portal.component.ChangeHistoryComponent;
import com.portal.component.ProcessFormComponent;
import com.portal.component.TaskFormComponent;
import com.portal.component.TaskFormComponent.TaskInfo;
import com.portal.dto.ProcessFormData;
import com.portal.dto.TaskFormData;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import net.jqwik.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property 15: Fallback to Process Form when no Task Form bound
 *
 * For any Stage that has no Task Form binding, the task detail API should return
 * only the Process Form data in read-only mode, with no Task Form layout.
 *
 * Validates: Requirements 9.5
 */
public class TaskFormFallbackPropertyTest {

    /**
     * Property 15: When no Task Form is bound to a stage, fallback to Process Form read-only.
     *
     * Validates: Requirements 9.5
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 15: Fallback to Process Form when no Task Form bound")
    void fallbackToProcessFormWhenNoBinding(
            @ForAll("unboundStageConfigs") UnboundStageConfig config) {

        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);
        ProcessFormComponent processFormComponent = mock(ProcessFormComponent.class);

        // Create a testable TaskFormComponent where fetchTaskFormByStageId returns null (no binding)
        TaskFormComponent component = new TaskFormComponent(
                processFormComponent, changeHistoryComponent, processInstanceRepository,
                mock(WorkflowEngineClient.class), mock(RestTemplate.class), new ObjectMapper(), mock(JdbcTemplate.class)) {
            @Override
            protected TaskInfo getTaskInfo(String taskId) {
                return new TaskInfo(config.taskDefinitionKey, config.processInstanceId);
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

        // Mock ProcessFormComponent to return read-only data
        ProcessFormData processFormData = ProcessFormData.builder()
                .processInstanceId(config.processInstanceId)
                .formName("Process Form")
                .formType("PROCESS")
                .configJson(Collections.emptyMap())
                .fieldValues(config.processVariables)
                .editable(false)
                .processState("RUNNING")
                .build();
        when(processFormComponent.getProcessFormData(config.processInstanceId))
                .thenReturn(processFormData);

        // Call getTaskFormData
        TaskFormData formData = component.getTaskFormData(config.taskId);

        // Core property: no Task Form layout when no binding
        assertThat(formData.getConfigJson())
                .as("configJson should be null when no Task Form binding")
                .isNull();
        assertThat(formData.getFieldPermissions())
                .as("fieldPermissions should be null when no Task Form binding")
                .isNull();
        assertThat(formData.getFieldValues())
                .as("fieldValues should be null when no Task Form binding")
                .isNull();
        assertThat(formData.getFormName())
                .as("formName should be null when no Task Form binding")
                .isNull();

        // Process Form reference should be present and read-only
        assertThat(formData.getProcessFormRef())
                .as("processFormRef should be present as fallback")
                .isNotNull();
        assertThat(formData.getProcessFormRef().isEditable())
                .as("processFormRef should be read-only")
                .isFalse();
        assertThat(formData.getProcessFormRef().getProcessInstanceId())
                .isEqualTo(config.processInstanceId);

        // Task metadata should still be present
        assertThat(formData.getTaskId()).isEqualTo(config.taskId);
        assertThat(formData.getTaskDefinitionKey()).isEqualTo(config.taskDefinitionKey);
    }

    // ========== Data class ==========

    static class UnboundStageConfig {
        String taskId;
        String taskDefinitionKey;
        String processInstanceId;
        Map<String, Object> processVariables;
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<UnboundStageConfig> unboundStageConfigs() {
        Arbitrary<String> taskIds = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15)
                .map(s -> "task_" + s);
        Arbitrary<String> stageIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(s -> "unbound_stage_" + s);
        Arbitrary<String> processIds = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15)
                .map(s -> "proc_" + s);
        Arbitrary<Integer> fieldCounts = Arbitraries.integers().between(0, 5);

        return Combinators.combine(taskIds, stageIds, processIds, fieldCounts)
                .as((taskId, stageId, procId, count) -> {
                    UnboundStageConfig config = new UnboundStageConfig();
                    config.taskId = taskId;
                    config.taskDefinitionKey = stageId;
                    config.processInstanceId = procId;
                    config.processVariables = new HashMap<>();
                    for (int i = 0; i < count; i++) {
                        config.processVariables.put("field_" + i, "value_" + i);
                    }
                    return config;
                });
    }
}
