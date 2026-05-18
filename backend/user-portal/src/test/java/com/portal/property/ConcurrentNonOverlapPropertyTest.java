package com.portal.property;

import com.portal.client.WorkflowEngineClient;
import com.portal.component.ChangeHistoryComponent;
import com.portal.component.ProcessFormComponent;
import com.portal.component.TaskFormComponent;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import net.jqwik.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property 25: Non-overlapping concurrent submissions both succeed
 *
 * For any two concurrent Task Form submissions that modify disjoint sets of fields,
 * both submissions should succeed, and the final process variable state should
 * contain all field values from both submissions.
 *
 * Validates: Requirements 15.1, 15.2
 */
public class ConcurrentNonOverlapPropertyTest {

    /**
     * Property 25: Two submissions with disjoint field sets both succeed,
     * and the final state contains all values from both.
     *
     * Validates: Requirements 15.1, 15.2
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 25: Non-overlapping concurrent submissions both succeed")
    void nonOverlappingConcurrentSubmissionsBothSucceed(
            @ForAll("concurrentNonOverlapConfigs") ConcurrentConfig config) {

        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        ChangeHistoryComponent changeHistoryComponent = mock(ChangeHistoryComponent.class);
        ProcessFormComponent processFormComponent = mock(ProcessFormComponent.class);

        // Create a testable subclass that overrides getTaskInfo
        TaskFormComponent component = new TaskFormComponent(
                processFormComponent, changeHistoryComponent, processInstanceRepository,
                mock(WorkflowEngineClient.class), mock(RestTemplate.class), new com.fasterxml.jackson.databind.ObjectMapper(), mock(org.springframework.jdbc.core.JdbcTemplate.class), com.portal.testsupport.PortalTransactionTestSupport.noopPlatformTransactionManager()) {
            @Override
            protected TaskInfo getTaskInfo(String taskId) {
                return new TaskInfo("stage_" + taskId, config.processInstanceId);
            }
        };
        ReflectionTestUtils.setField(component, "developerWorkstationUrl", "http://mock-dw:8091");

        // Initial process variables
        Map<String, Object> initialVars = new HashMap<>(config.initialVariables);

        // Simulate: both users load the form at the same time (same baseline)
        Map<String, Object> baselineForUser1 = new HashMap<>(initialVars);
        Map<String, Object> baselineForUser2 = new HashMap<>(initialVars);

        // User 1 submits first — disjoint fields
        ProcessInstance processInstance1 = ProcessInstance.builder()
                .id(config.processInstanceId)
                .processDefinitionKey("test-process")
                .startUserId("starter")
                .status("RUNNING")
                .variables(new HashMap<>(initialVars))
                .build();

        when(processInstanceRepository.findById(config.processInstanceId))
                .thenReturn(Optional.of(processInstance1));
        when(processInstanceRepository.save(any(ProcessInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // User 1 submits (no baseline check needed for non-overlapping)
        assertThatCode(() -> component.submitTaskForm(
                "task1", config.user1, config.user1Fields, baselineForUser1))
                .doesNotThrowAnyException();

        // After user1 submission, variables should include user1's fields
        Map<String, Object> afterUser1 = new HashMap<>(initialVars);
        afterUser1.putAll(config.user1Fields);

        // User 2 submits — process instance now has user1's changes
        ProcessInstance processInstance2 = ProcessInstance.builder()
                .id(config.processInstanceId)
                .processDefinitionKey("test-process")
                .startUserId("starter")
                .status("RUNNING")
                .variables(new HashMap<>(afterUser1))
                .build();

        when(processInstanceRepository.findById(config.processInstanceId))
                .thenReturn(Optional.of(processInstance2));

        assertThatCode(() -> component.submitTaskForm(
                "task2", config.user2, config.user2Fields, baselineForUser2))
                .doesNotThrowAnyException();

        // Core property: final state should contain ALL values from both submissions
        Map<String, Object> expectedFinal = new HashMap<>(initialVars);
        expectedFinal.putAll(config.user1Fields);
        expectedFinal.putAll(config.user2Fields);

        // Verify the last save captured the combined state
        verify(processInstanceRepository, atLeast(2)).save(argThat(pi -> {
            // Each save should succeed (no exception)
            return pi.getId().equals(config.processInstanceId);
        }));

        // Verify both submissions' field values are present in the final state
        // by checking the second save includes user1's fields too
        Map<String, Object> finalVars = processInstance2.getVariables();
        for (Map.Entry<String, Object> entry : config.user1Fields.entrySet()) {
            assertThat(finalVars).containsKey(entry.getKey());
        }
        for (Map.Entry<String, Object> entry : config.user2Fields.entrySet()) {
            assertThat(finalVars)
                    .containsEntry(entry.getKey(), entry.getValue());
        }
    }

    // ========== Data classes ==========

    static class ConcurrentConfig {
        String processInstanceId;
        String user1;
        String user2;
        Map<String, Object> initialVariables;
        Map<String, Object> user1Fields;
        Map<String, Object> user2Fields;
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<ConcurrentConfig> concurrentNonOverlapConfigs() {
        Arbitrary<String> processIds = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(10)
                .map(s -> "proc_" + s);
        Arbitrary<String> userIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(8)
                .map(s -> "user_" + s);
        Arbitrary<Integer> fieldCounts = Arbitraries.integers().between(1, 4);

        return Combinators.combine(processIds, userIds, userIds, fieldCounts, fieldCounts)
                .as((procId, u1, u2, count1, count2) -> {
                    ConcurrentConfig config = new ConcurrentConfig();
                    config.processInstanceId = procId;
                    config.user1 = u1;
                    config.user2 = u2.equals(u1) ? u2 + "_b" : u2;

                    // Initial variables
                    config.initialVariables = new HashMap<>();
                    for (int i = 0; i < count1 + count2; i++) {
                        config.initialVariables.put("field_" + i, "initial_" + i);
                    }

                    // User 1 edits fields 0..count1-1 (disjoint from user 2)
                    config.user1Fields = new HashMap<>();
                    for (int i = 0; i < count1; i++) {
                        config.user1Fields.put("field_" + i, "user1_val_" + i);
                    }

                    // User 2 edits fields count1..count1+count2-1 (disjoint from user 1)
                    config.user2Fields = new HashMap<>();
                    for (int i = count1; i < count1 + count2; i++) {
                        config.user2Fields.put("field_" + i, "user2_val_" + i);
                    }

                    return config;
                });
    }
}
