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
 * Property 8: Sub-table data shared across form types
 *
 * For any sub-table modification through either a Process Form or Task Form,
 * the updated sub-table data stored in process variables should be identical
 * when read from either form type's perspective.
 *
 * Validates: Requirements 5.1, 5.4, 5.6
 */
public class SubTableDataSharingPropertyTest {

    /**
     * Property 8: Sub-table data written via Task Form is visible via Process Form.
     *
     * Validates: Requirements 5.1, 5.4, 5.6
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 8: Sub-table data shared across form types")
    void subTableDataSharedAcrossFormTypes(
            @ForAll("subTableConfigs") SubTableConfig config) {

        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);
        ProcessFormComponent processFormComponent = mock(ProcessFormComponent.class);

        TaskFormComponent taskFormComponent = new TaskFormComponent(
                processFormComponent, changeHistoryComponent, processInstanceRepository,
                mock(WorkflowEngineClient.class), mock(RestTemplate.class), new com.fasterxml.jackson.databind.ObjectMapper(), mock(org.springframework.jdbc.core.JdbcTemplate.class)) {
            @Override
            protected TaskInfo getTaskInfo(String taskId) {
                return new TaskInfo(config.taskDefinitionKey, config.processInstanceId);
            }
        };
        ReflectionTestUtils.setField(taskFormComponent, "developerWorkstationUrl", "http://mock-dw:8091");

        // Initial process variables with sub-table data
        Map<String, Object> initialVariables = new HashMap<>();
        initialVariables.put("_subtable_" + config.subTableName, config.initialSubTableData);
        initialVariables.putAll(config.regularFields);

        ProcessInstance processInstance = ProcessInstance.builder()
                .id(config.processInstanceId)
                .processDefinitionKey("test-process")
                .startUserId("user-001")
                .status("RUNNING")
                .variables(new HashMap<>(initialVariables))
                .build();

        when(processInstanceRepository.findById(config.processInstanceId))
                .thenReturn(Optional.of(processInstance));
        when(processInstanceRepository.save(any(ProcessInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Submit task form with updated sub-table data
        Map<String, Object> submittedData = new HashMap<>();
        submittedData.put("_subtable_" + config.subTableName, config.updatedSubTableData);
        submittedData.putAll(config.updatedFields);

        taskFormComponent.submitTaskForm(config.taskId, config.userId, submittedData);

        // Capture saved process instance
        ArgumentCaptor<ProcessInstance> captor = ArgumentCaptor.forClass(ProcessInstance.class);
        verify(processInstanceRepository).save(captor.capture());

        Map<String, Object> savedVariables = captor.getValue().getVariables();

        // Core property: sub-table data in process variables matches what was submitted
        String subTableKey = "_subtable_" + config.subTableName;
        assertThat(savedVariables)
                .as("Sub-table data should be stored in process variables")
                .containsKey(subTableKey);
        assertThat(savedVariables.get(subTableKey))
                .as("Sub-table data should match the submitted data")
                .isEqualTo(config.updatedSubTableData);

        // Core property: the same process variables are used by both form types
        // When ProcessFormComponent reads the same process instance, it sees the same data
        // This is guaranteed by the single data source architecture:
        // both components read from processInstance.getVariables()

        // Verify that reading the sub-table data from the saved variables
        // produces the same result regardless of which component reads it
        Map<String, Object> processFormView = new HashMap<>(savedVariables);
        Map<String, Object> taskFormView = taskFormComponent.extractFieldSubset(
                savedVariables, savedVariables.keySet());

        assertThat(processFormView.get(subTableKey))
                .as("Process Form view of sub-table should equal Task Form view")
                .isEqualTo(taskFormView.get(subTableKey));

        // Regular fields should also be consistent
        for (Map.Entry<String, Object> entry : config.updatedFields.entrySet()) {
            assertThat(savedVariables.get(entry.getKey()))
                    .as("Regular field '%s' should be consistent across views", entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    /**
     * Property 8: Sub-table data written via Process Form is visible via Task Form.
     *
     * Validates: Requirements 5.1, 5.4, 5.6
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 8: Sub-table data written via Process Form visible in Task Form")
    void subTableDataWrittenViaProcessFormVisibleInTaskForm(
            @ForAll("subTableConfigs") SubTableConfig config) {

        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);

        ProcessFormComponent processFormComponent = new ProcessFormComponent(
                processInstanceRepository, changeHistoryComponent, mock(RestTemplate.class),
                new ObjectMapper(), mock(JdbcTemplate.class));
        ReflectionTestUtils.setField(processFormComponent, "adminCenterUrl", "http://mock-admin:8090");

        // Process instance in RETURN_TO_REQUESTER state (so Process Form submit is allowed)
        Map<String, Object> initialVariables = new HashMap<>();
        initialVariables.put("_subtable_" + config.subTableName, config.initialSubTableData);

        ProcessInstance processInstance = ProcessInstance.builder()
                .id(config.processInstanceId)
                .processDefinitionKey("test-process")
                .startUserId(config.userId)
                .status("RETURN_TO_REQUESTER")
                .variables(new HashMap<>(initialVariables))
                .build();

        when(processInstanceRepository.findById(config.processInstanceId))
                .thenReturn(Optional.of(processInstance));
        when(processInstanceRepository.save(any(ProcessInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Submit via Process Form
        Map<String, Object> submittedData = new HashMap<>();
        submittedData.put("_subtable_" + config.subTableName, config.updatedSubTableData);

        processFormComponent.submitProcessFormUpdate(config.processInstanceId, config.userId, submittedData);

        // Capture saved variables
        ArgumentCaptor<ProcessInstance> captor = ArgumentCaptor.forClass(ProcessInstance.class);
        verify(processInstanceRepository).save(captor.capture());

        Map<String, Object> savedVariables = captor.getValue().getVariables();

        // Core property: sub-table data is the same single source
        String subTableKey = "_subtable_" + config.subTableName;
        assertThat(savedVariables.get(subTableKey))
                .as("Sub-table data should be updated via Process Form")
                .isEqualTo(config.updatedSubTableData);

        // Task Form would read from the same savedVariables
        TaskFormComponent taskFormComponent = new TaskFormComponent(
                processFormComponent, changeHistoryComponent, processInstanceRepository,
                mock(WorkflowEngineClient.class), mock(RestTemplate.class), new com.fasterxml.jackson.databind.ObjectMapper(), mock(org.springframework.jdbc.core.JdbcTemplate.class));
        Map<String, Object> taskView = taskFormComponent.extractFieldSubset(
                savedVariables, Set.of(subTableKey));

        assertThat(taskView.get(subTableKey))
                .as("Task Form should see the same sub-table data written by Process Form")
                .isEqualTo(config.updatedSubTableData);
    }

    // ========== Data class ==========

    static class SubTableConfig {
        String processInstanceId;
        String taskId;
        String taskDefinitionKey;
        String userId;
        String subTableName;
        List<Map<String, Object>> initialSubTableData;
        List<Map<String, Object>> updatedSubTableData;
        Map<String, Object> regularFields;
        Map<String, Object> updatedFields;
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<SubTableConfig> subTableConfigs() {
        Arbitrary<String> processIds = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15)
                .map(s -> "proc_" + s);
        Arbitrary<String> taskIds = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15)
                .map(s -> "task_" + s);
        Arbitrary<String> stageIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(s -> "stage_" + s);
        Arbitrary<String> userIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(s -> "user_" + s);
        Arbitrary<String> tableNames = Arbitraries.of("items", "attachments", "approvers", "details");
        Arbitrary<Integer> rowCounts = Arbitraries.integers().between(1, 4);

        return Combinators.combine(processIds, taskIds, stageIds, userIds, tableNames, rowCounts)
                .flatAs((procId, taskId, stageId, userId, tableName, rowCount) -> {
                    SubTableConfig config = new SubTableConfig();
                    config.processInstanceId = procId;
                    config.taskId = taskId;
                    config.taskDefinitionKey = stageId;
                    config.userId = userId;
                    config.subTableName = tableName;

                    config.initialSubTableData = new ArrayList<>();
                    config.updatedSubTableData = new ArrayList<>();
                    for (int i = 0; i < rowCount; i++) {
                        Map<String, Object> initialRow = new HashMap<>();
                        initialRow.put("id", "row_" + i);
                        initialRow.put("name", "initial_" + i);
                        config.initialSubTableData.add(initialRow);

                        Map<String, Object> updatedRow = new HashMap<>();
                        updatedRow.put("id", "row_" + i);
                        updatedRow.put("name", "updated_" + i);
                        config.updatedSubTableData.add(updatedRow);
                    }

                    config.regularFields = new HashMap<>();
                    config.regularFields.put("amount", 100);
                    config.updatedFields = new HashMap<>();
                    config.updatedFields.put("amount", 200);

                    return Arbitraries.just(config);
                });
    }
}
