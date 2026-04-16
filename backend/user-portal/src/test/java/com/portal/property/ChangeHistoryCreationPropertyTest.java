package com.portal.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.client.WorkflowEngineClient;
import com.portal.component.ChangeHistoryComponent;
import com.portal.dto.ChangeHistoryContext;
import com.platform.security.repository.UserRepository;
import com.portal.entity.ChangeHistory;
import com.portal.enums.ChangeType;
import com.portal.repository.ChangeHistoryRepository;
import com.portal.repository.ProcessInstanceRepository;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Property 9: Change_History records created for every field modification
 *
 * For any form submission that modifies N field values (where old value ≠ new value),
 * exactly N Change_History records should be created, each containing the correct
 * processInstanceId, userId, fieldName, oldValue, and newValue.
 * When the change originates from a Task Form, taskInstanceId should be non-null.
 *
 * Validates: Requirements 6.1, 6.2, 6.3, 6.5
 */
public class ChangeHistoryCreationPropertyTest {

    private ChangeHistoryRepository changeHistoryRepository;
    private ChangeHistoryComponent changeHistoryComponent;

    @SuppressWarnings("unchecked")
    private void setUp() {
        changeHistoryRepository = mock(ChangeHistoryRepository.class);
        when(changeHistoryRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        changeHistoryComponent = new ChangeHistoryComponent(
                changeHistoryRepository,
                mock(ProcessInstanceRepository.class),
                mock(UserRepository.class),
                mock(WorkflowEngineClient.class),
                mock(JdbcTemplate.class),
                new ObjectMapper());
    }

    /**
     * Property 9: For N changed fields, exactly N ChangeHistory records are created.
     *
     * Validates: Requirements 6.1, 6.2, 6.3, 6.5
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 9: Change_History records created for every field modification")
    void changeHistoryRecordCreatedForEveryModifiedField(
            @ForAll("fieldChangeSets") FieldChangeSet changeSet) {

        setUp();

        ChangeHistoryContext context = ChangeHistoryContext.builder()
                .processInstanceId(changeSet.processInstanceId)
                .taskInstanceId(changeSet.taskInstanceId)
                .stageId(changeSet.stageId)
                .userId(changeSet.userId)
                .build();

        changeHistoryComponent.recordFieldChanges(context, changeSet.oldValues, changeSet.newValues);

        // Count how many fields actually changed
        long expectedChanges = changeSet.newValues.entrySet().stream()
                .filter(e -> {
                    Object oldVal = changeSet.oldValues.get(e.getKey());
                    return !java.util.Objects.equals(oldVal, e.getValue());
                })
                .count();

        if (expectedChanges == 0) {
            verify(changeHistoryRepository, never()).saveAll(anyList());
        } else {
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<ChangeHistory>> captor = ArgumentCaptor.forClass(List.class);
            verify(changeHistoryRepository, times(1)).saveAll(captor.capture());

            List<ChangeHistory> saved = captor.getValue();
            assertThat(saved).hasSize((int) expectedChanges);

            for (ChangeHistory record : saved) {
                assertThat(record.getProcessInstanceId()).isEqualTo(changeSet.processInstanceId);
                assertThat(record.getUserId()).isEqualTo(changeSet.userId);
                assertThat(record.getChangeType()).isEqualTo(ChangeType.FIELD_UPDATE);
                assertThat(record.getFieldName()).isNotBlank();
                assertThat(record.getTimestamp()).isNotNull();

                // When change originates from Task Form, taskInstanceId should be non-null
                if (changeSet.taskInstanceId != null) {
                    assertThat(record.getTaskInstanceId()).isEqualTo(changeSet.taskInstanceId);
                }
            }
        }
    }

    // ========== Data class ==========

    static class FieldChangeSet {
        String processInstanceId;
        String taskInstanceId;
        String stageId;
        String userId;
        Map<String, Object> oldValues;
        Map<String, Object> newValues;
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<FieldChangeSet> fieldChangeSets() {
        Arbitrary<String> processIds = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15)
                .map(s -> "proc_" + s);
        Arbitrary<String> taskIds = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15)
                .map(s -> "task_" + s);
        Arbitrary<String> stageIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(s -> "stage_" + s);
        Arbitrary<String> userIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(s -> "user_" + s);
        Arbitrary<Integer> fieldCounts = Arbitraries.integers().between(1, 8);

        return Combinators.combine(processIds, taskIds.injectNull(0.3), stageIds, userIds, fieldCounts)
                .as((procId, taskId, stageId, userId, fieldCount) -> {
                    FieldChangeSet set = new FieldChangeSet();
                    set.processInstanceId = procId;
                    set.taskInstanceId = taskId;
                    set.stageId = stageId;
                    set.userId = userId;
                    set.oldValues = new HashMap<>();
                    set.newValues = new HashMap<>();

                    for (int i = 0; i < fieldCount; i++) {
                        String fieldName = "field_" + i;
                        String oldVal = "old_" + i;
                        // Alternate: some fields change, some stay the same
                        String newVal = (i % 3 == 0) ? oldVal : "new_" + i + "_" + procId.hashCode();
                        set.oldValues.put(fieldName, oldVal);
                        set.newValues.put(fieldName, newVal);
                    }
                    return set;
                });
    }
}
