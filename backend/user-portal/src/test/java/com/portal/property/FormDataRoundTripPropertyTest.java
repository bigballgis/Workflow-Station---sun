package com.portal.property;

import com.portal.client.WorkflowEngineClient;
import com.portal.component.ChangeHistoryComponent;
import com.portal.component.ProcessFormComponent;
import com.portal.component.TaskFormComponent;
import com.portal.component.TaskFormComponent.TaskInfo;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 7: Form data round-trip through process variables
 *
 * For any form submission (Process Form or Task Form) containing valid field values,
 * writing the data to Flowable process variables and then reading it back should produce
 * values equal to the submitted values for all submitted fields.
 *
 * Validates: Requirements 4.1, 4.2, 4.3
 */
public class FormDataRoundTripPropertyTest {

    /**
     * Property 7: Process Form data round-trip preserves values.
     *
     * Validates: Requirements 4.1, 4.2, 4.3
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 7: Form data round-trip through process variables (Process Form)")
    void processFormDataRoundTrip(
            @ForAll("roundTripConfigs") RoundTripConfig config) {

        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);

        ProcessFormComponent component = new ProcessFormComponent(
                processInstanceRepository, changeHistoryComponent, mock(RestTemplate.class),
                new ObjectMapper(), mock(JdbcTemplate.class));
        ReflectionTestUtils.setField(component, "adminCenterUrl", "http://mock-admin:8090");

        // Create process instance in RETURN_TO_REQUESTER state (so submit is allowed)
        ProcessInstance processInstance = ProcessInstance.builder()
                .id(config.processInstanceId)
                .processDefinitionKey("test-process")
                .startUserId(config.userId)
                .status("RETURN_TO_REQUESTER")
                .variables(new HashMap<>(config.existingVariables))
                .build();

        when(processInstanceRepository.findById(config.processInstanceId))
                .thenReturn(Optional.of(processInstance));
        when(processInstanceRepository.save(any(ProcessInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Submit form data
        component.submitProcessFormUpdate(config.processInstanceId, config.userId, config.submittedData);

        // Capture saved process instance
        ArgumentCaptor<ProcessInstance> captor = ArgumentCaptor.forClass(ProcessInstance.class);
        verify(processInstanceRepository).save(captor.capture());

        Map<String, Object> savedVariables = captor.getValue().getVariables();

        // Core property: all submitted fields should be readable with same values
        for (Map.Entry<String, Object> entry : config.submittedData.entrySet()) {
            assertThat(savedVariables)
                    .as("Submitted field '%s' should be in saved variables", entry.getKey())
                    .containsKey(entry.getKey());
            assertThat(savedVariables.get(entry.getKey()))
                    .as("Field '%s' value should match submitted value", entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    /**
     * Property 7: Task Form data round-trip preserves values for editable fields.
     *
     * Validates: Requirements 4.1, 4.2, 4.3
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 7: Form data round-trip through process variables (Task Form)")
    void taskFormDataRoundTrip(
            @ForAll("taskRoundTripConfigs") TaskRoundTripConfig config) {

        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);
        ProcessFormComponent processFormComponent = mock(ProcessFormComponent.class);

        TaskFormComponent component = new TaskFormComponent(
                processFormComponent, changeHistoryComponent, processInstanceRepository,
                mock(WorkflowEngineClient.class), mock(RestTemplate.class), new com.fasterxml.jackson.databind.ObjectMapper(), mock(org.springframework.jdbc.core.JdbcTemplate.class)) {
            @Override
            protected TaskInfo getTaskInfo(String taskId) {
                return new TaskInfo(config.taskDefinitionKey, config.processInstanceId);
            }
        };
        ReflectionTestUtils.setField(component, "developerWorkstationUrl", "http://mock-dw:8091");

        ProcessInstance processInstance = ProcessInstance.builder()
                .id(config.processInstanceId)
                .processDefinitionKey("test-process")
                .startUserId("user-001")
                .status("RUNNING")
                .variables(new HashMap<>(config.existingVariables))
                .build();

        when(processInstanceRepository.findById(config.processInstanceId))
                .thenReturn(Optional.of(processInstance));
        when(processInstanceRepository.save(any(ProcessInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Submit task form data
        component.submitTaskForm(config.taskId, config.userId, config.submittedData);

        // Capture saved process instance
        ArgumentCaptor<ProcessInstance> captor = ArgumentCaptor.forClass(ProcessInstance.class);
        verify(processInstanceRepository).save(captor.capture());

        Map<String, Object> savedVariables = captor.getValue().getVariables();

        // Core property: submitted data should be readable with same values
        // (since no form definition is fetched, all fields are treated as editable)
        for (Map.Entry<String, Object> entry : config.submittedData.entrySet()) {
            assertThat(savedVariables)
                    .as("Submitted field '%s' should be in saved variables", entry.getKey())
                    .containsKey(entry.getKey());
            assertThat(savedVariables.get(entry.getKey()))
                    .as("Field '%s' value should match submitted value", entry.getKey())
                    .isEqualTo(entry.getValue());
        }

        // Existing variables not in submitted data should be preserved
        for (Map.Entry<String, Object> entry : config.existingVariables.entrySet()) {
            if (!config.submittedData.containsKey(entry.getKey())) {
                assertThat(savedVariables)
                        .as("Existing field '%s' should be preserved", entry.getKey())
                        .containsEntry(entry.getKey(), entry.getValue());
            }
        }
    }

    // ========== Data classes ==========

    static class RoundTripConfig {
        String processInstanceId;
        String userId;
        Map<String, Object> existingVariables;
        Map<String, Object> submittedData;
    }

    static class TaskRoundTripConfig {
        String taskId;
        String taskDefinitionKey;
        String processInstanceId;
        String userId;
        Map<String, Object> existingVariables;
        Map<String, Object> submittedData;
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<RoundTripConfig> roundTripConfigs() {
        Arbitrary<String> processIds = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15)
                .map(s -> "proc_" + s);
        Arbitrary<String> userIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(s -> "user_" + s);
        Arbitrary<Integer> existingCounts = Arbitraries.integers().between(0, 4);
        Arbitrary<Integer> submitCounts = Arbitraries.integers().between(1, 5);

        return Combinators.combine(processIds, userIds, existingCounts, submitCounts)
                .as((procId, userId, existCount, submitCount) -> {
                    RoundTripConfig config = new RoundTripConfig();
                    config.processInstanceId = procId;
                    config.userId = userId;
                    config.existingVariables = new HashMap<>();
                    config.submittedData = new HashMap<>();

                    for (int i = 0; i < existCount; i++) {
                        config.existingVariables.put("existing_" + i, "old_" + i);
                    }
                    for (int i = 0; i < submitCount; i++) {
                        config.submittedData.put("field_" + i, "new_value_" + i);
                    }
                    return config;
                });
    }

    @Provide
    Arbitrary<TaskRoundTripConfig> taskRoundTripConfigs() {
        Arbitrary<String> taskIds = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15)
                .map(s -> "task_" + s);
        Arbitrary<String> stageIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(s -> "stage_" + s);
        Arbitrary<String> processIds = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15)
                .map(s -> "proc_" + s);
        Arbitrary<String> userIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(s -> "user_" + s);
        Arbitrary<Integer> existingCounts = Arbitraries.integers().between(0, 3);
        Arbitrary<Integer> submitCounts = Arbitraries.integers().between(1, 5);

        return Combinators.combine(taskIds, stageIds, processIds, userIds, existingCounts, submitCounts)
                .flatAs((taskId, stageId, procId, userId, existCount, submitCount) -> {
                    TaskRoundTripConfig config = new TaskRoundTripConfig();
                    config.taskId = taskId;
                    config.taskDefinitionKey = stageId;
                    config.processInstanceId = procId;
                    config.userId = userId;
                    config.existingVariables = new HashMap<>();
                    config.submittedData = new HashMap<>();

                    for (int i = 0; i < existCount; i++) {
                        config.existingVariables.put("existing_" + i, "old_" + i);
                    }
                    for (int i = 0; i < submitCount; i++) {
                        config.submittedData.put("field_" + i, "new_value_" + i);
                    }
                    return Arbitraries.just(config);
                });
    }
}
