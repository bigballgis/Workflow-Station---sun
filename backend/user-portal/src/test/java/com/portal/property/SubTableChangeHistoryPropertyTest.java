package com.portal.property;

import com.portal.client.WorkflowEngineClient;
import com.portal.component.ChangeHistoryComponent;
import com.portal.dto.ChangeHistoryContext;
import com.platform.security.repository.UserRepository;
import com.portal.dto.SubTableChange;
import com.portal.entity.ChangeHistory;
import com.portal.enums.ChangeType;
import com.portal.repository.ChangeHistoryRepository;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Property 11: Sub-table Change_History includes table metadata
 *
 * For any sub-table modification that creates Change_History records,
 * each record should have a non-null subTableName and rowIdentifier,
 * and the changeType should be one of SUB_TABLE_ROW_ADD, SUB_TABLE_ROW_UPDATE,
 * or SUB_TABLE_ROW_DELETE.
 *
 * Validates: Requirements 6.8
 */
public class SubTableChangeHistoryPropertyTest {

    private static final Set<ChangeType> VALID_SUB_TABLE_CHANGE_TYPES = Set.of(
            ChangeType.SUB_TABLE_ROW_ADD,
            ChangeType.SUB_TABLE_ROW_UPDATE,
            ChangeType.SUB_TABLE_ROW_DELETE
    );

    private ChangeHistoryRepository changeHistoryRepository;
    private ChangeHistoryComponent changeHistoryComponent;

    @SuppressWarnings("unchecked")
    private void setUp() {
        changeHistoryRepository = mock(ChangeHistoryRepository.class);
        when(changeHistoryRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        changeHistoryComponent = new ChangeHistoryComponent(
                changeHistoryRepository,
                mock(UserRepository.class),
                mock(WorkflowEngineClient.class));
    }

    /**
     * Property 11: Sub-table Change_History includes table metadata.
     *
     * Validates: Requirements 6.8
     */
    @Property(tries = 100)
    @Label("Feature: process-task-form-separation, Property 11: Sub-table Change_History includes table metadata")
    void subTableChangeHistoryIncludesMetadata(
            @ForAll("subTableChangeSets") SubTableChangeSet changeSet) {

        setUp();

        ChangeHistoryContext context = ChangeHistoryContext.builder()
                .processInstanceId(changeSet.processInstanceId)
                .taskInstanceId(changeSet.taskInstanceId)
                .stageId(changeSet.stageId)
                .userId(changeSet.userId)
                .build();

        changeHistoryComponent.recordSubTableChanges(context, changeSet.subTableName, changeSet.changes);

        if (changeSet.changes.isEmpty()) {
            verify(changeHistoryRepository, never()).saveAll(anyList());
        } else {
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<ChangeHistory>> captor = ArgumentCaptor.forClass(List.class);
            verify(changeHistoryRepository, times(1)).saveAll(captor.capture());

            List<ChangeHistory> saved = captor.getValue();
            assertThat(saved).hasSize(changeSet.changes.size());

            for (ChangeHistory record : saved) {
                // subTableName must be non-null
                assertThat(record.getSubTableName()).isNotNull();
                assertThat(record.getSubTableName()).isEqualTo(changeSet.subTableName);

                // rowIdentifier must be non-null
                assertThat(record.getRowIdentifier()).isNotNull();

                // changeType must be one of the valid sub-table types
                assertThat(record.getChangeType()).isIn(VALID_SUB_TABLE_CHANGE_TYPES);

                // Verify context fields
                assertThat(record.getProcessInstanceId()).isEqualTo(changeSet.processInstanceId);
                assertThat(record.getUserId()).isEqualTo(changeSet.userId);
                assertThat(record.getTimestamp()).isNotNull();
            }
        }
    }

    // ========== Data class ==========

    static class SubTableChangeSet {
        String processInstanceId;
        String taskInstanceId;
        String stageId;
        String userId;
        String subTableName;
        List<SubTableChange> changes;
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<SubTableChangeSet> subTableChangeSets() {
        Arbitrary<String> processIds = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15)
                .map(s -> "proc_" + s);
        Arbitrary<String> taskIds = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15)
                .map(s -> "task_" + s);
        Arbitrary<String> stageIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(s -> "stage_" + s);
        Arbitrary<String> userIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(s -> "user_" + s);
        Arbitrary<String> tableNames = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15)
                .map(s -> "table_" + s);
        Arbitrary<Integer> changeCounts = Arbitraries.integers().between(1, 8);

        return Combinators.combine(processIds, taskIds.injectNull(0.3), stageIds, userIds, tableNames, changeCounts)
                .as((procId, taskId, stageId, userId, tableName, count) -> {
                    SubTableChangeSet set = new SubTableChangeSet();
                    set.processInstanceId = procId;
                    set.taskInstanceId = taskId;
                    set.stageId = stageId;
                    set.userId = userId;
                    set.subTableName = tableName;
                    set.changes = new ArrayList<>();

                    String[] changeTypes = {"ROW_ADD", "ROW_UPDATE", "ROW_DELETE"};
                    for (int i = 0; i < count; i++) {
                        String changeType = changeTypes[i % changeTypes.length];
                        Map<String, Object> oldVals = "ROW_ADD".equals(changeType) ? null : Map.of("col_" + i, "old_" + i);
                        Map<String, Object> newVals = "ROW_DELETE".equals(changeType) ? null : Map.of("col_" + i, "new_" + i);

                        SubTableChange change = SubTableChange.builder()
                                .changeType(changeType)
                                .rowIdentifier("row_" + i)
                                .oldValues(oldVals)
                                .newValues(newVals)
                                .build();
                        set.changes.add(change);
                    }
                    return set;
                });
    }
}
