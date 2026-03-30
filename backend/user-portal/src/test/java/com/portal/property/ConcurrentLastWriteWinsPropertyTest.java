package com.portal.property;

import com.portal.component.ChangeHistoryComponent;
import com.portal.component.ProcessFormComponent;
import com.portal.component.TaskFormComponent;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import net.jqwik.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property 26: Overlapping concurrent submissions — last write wins
 *
 * For any two concurrent submissions that modify the same field,
 * the final process variable value for that field should equal the value
 * from the submission that was processed last (by timestamp).
 *
 * Validates: Requirements 15.4
 */
public class ConcurrentLastWriteWinsPropertyTest {

    /**
     * Property 26: When two users modify the same field, the last submission's value wins.
     *
     * Validates: Requirements 15.4
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 26: Overlapping concurrent submissions — last write wins")
    void overlappingConcurrentSubmissionsLastWriteWins(
            @ForAll("lastWriteWinsConfigs") LastWriteConfig config) {

        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);
        ProcessFormComponent processFormComponent = mock(ProcessFormComponent.class);

        // Testable subclass that overrides getTaskInfo
        TaskFormComponent component = new TaskFormComponent(
                processFormComponent, changeHistoryComponent, processInstanceRepository) {
            @Override
            protected TaskInfo getTaskInfo(String taskId) {
                return new TaskInfo("stage_" + taskId, config.processInstanceId);
            }
        };
        ReflectionTestUtils.setField(component, "developerWorkstationUrl", "http://mock-dw:8091");

        // Both users loaded the same baseline
        Map<String, Object> baseline = new HashMap<>(config.initialVariables);

        // --- First submission ---
        ProcessInstance pi1 = ProcessInstance.builder()
                .id(config.processInstanceId)
                .processDefinitionKey("test-process")
                .startUserId("starter")
                .status("RUNNING")
                .variables(new HashMap<>(config.initialVariables))
                .build();

        when(processInstanceRepository.findById(config.processInstanceId))
                .thenReturn(Optional.of(pi1));
        when(processInstanceRepository.save(any(ProcessInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        component.submitTaskForm("task1", config.firstUser, config.firstSubmission, baseline);

        // After first submission
        Map<String, Object> afterFirst = new HashMap<>(config.initialVariables);
        afterFirst.putAll(config.firstSubmission);

        // --- Second submission (last write) ---
        ProcessInstance pi2 = ProcessInstance.builder()
                .id(config.processInstanceId)
                .processDefinitionKey("test-process")
                .startUserId("starter")
                .status("RUNNING")
                .variables(new HashMap<>(afterFirst))
                .build();

        when(processInstanceRepository.findById(config.processInstanceId))
                .thenReturn(Optional.of(pi2));

        component.submitTaskForm("task2", config.secondUser, config.secondSubmission, baseline);

        // Core property: for overlapping fields, the final value equals the LAST submission's value
        Map<String, Object> finalVars = pi2.getVariables();
        for (String overlappingField : config.overlappingFields) {
            assertThat(finalVars.get(overlappingField))
                    .as("Field '%s' should have last-write value", overlappingField)
                    .isEqualTo(config.secondSubmission.get(overlappingField));
        }
    }

    // ========== Data classes ==========

    static class LastWriteConfig {
        String processInstanceId;
        String firstUser;
        String secondUser;
        Map<String, Object> initialVariables;
        Map<String, Object> firstSubmission;
        Map<String, Object> secondSubmission;
        Set<String> overlappingFields;
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<LastWriteConfig> lastWriteWinsConfigs() {
        Arbitrary<String> processIds = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(10)
                .map(s -> "proc_" + s);
        Arbitrary<String> userIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(8)
                .map(s -> "user_" + s);
        Arbitrary<Integer> overlapCounts = Arbitraries.integers().between(1, 3);

        return Combinators.combine(processIds, userIds, userIds, overlapCounts)
                .as((procId, u1, u2, overlapCount) -> {
                    LastWriteConfig config = new LastWriteConfig();
                    config.processInstanceId = procId;
                    config.firstUser = u1;
                    config.secondUser = u2.equals(u1) ? u2 + "_b" : u2;

                    // Initial variables with overlapping fields
                    config.initialVariables = new HashMap<>();
                    config.firstSubmission = new HashMap<>();
                    config.secondSubmission = new HashMap<>();
                    config.overlappingFields = new HashSet<>();

                    for (int i = 0; i < overlapCount; i++) {
                        String fieldName = "shared_field_" + i;
                        config.initialVariables.put(fieldName, "initial_" + i);
                        config.firstSubmission.put(fieldName, "first_val_" + i);
                        config.secondSubmission.put(fieldName, "second_val_" + i);
                        config.overlappingFields.add(fieldName);
                    }

                    // Add some non-overlapping fields
                    config.initialVariables.put("only_first", "init");
                    config.firstSubmission.put("only_first", "first_only");
                    config.initialVariables.put("only_second", "init");
                    config.secondSubmission.put("only_second", "second_only");

                    return config;
                });
    }
}
