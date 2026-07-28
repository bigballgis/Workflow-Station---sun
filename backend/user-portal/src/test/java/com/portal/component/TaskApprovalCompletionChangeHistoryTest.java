package com.portal.component;

import com.portal.dto.SubTableChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("Task approval sub-table change-history baselines")
class TaskApprovalCompletionChangeHistoryTest {
    @Test
    @DisplayName("uses the pre-completion process state")
    void usesPreSyncSubTables() {
        Map<String, Object> existingSubTables = Map.of(
                "50938", List.of(Map.of("row_id", "transaction-1", "amount", 100)));
        Map<String, Object> preSyncVariables = Map.of("__subTables__", existingSubTables);
        Object resolved = TaskApprovalCompletionComponent.resolveSubTableHistoryBaseline(preSyncVariables);
        assertSame(existingSubTables, resolved);
        assertEquals(List.of(), TaskApprovalCompletionComponent.computeSubTableRowChanges(
                List.of(Map.of("row_id", "transaction-1", "amount", 100)),
                List.of(Map.of("row_id", "transaction-1", "amount", 100))));
    }

    @Test
    @DisplayName("uses the latest saved process state instead of replaying the first-save baseline")
    void usesLatestPreSyncStateAfterIncrementalSave() {
        Map<String, Object> latestSavedSubTables = Map.of(
                "people", List.of(Map.of("id", "person-1", "age", 1)));
        Map<String, Object> preSyncVariables = Map.of("__subTables__", latestSavedSubTables);
        Object resolved = TaskApprovalCompletionComponent.resolveSubTableHistoryBaseline(preSyncVariables);
        assertSame(latestSavedSubTables, resolved);
    }

    @Test
    @DisplayName("does not audit workflow-node progress fields as a user sub-table edit")
    void ignoresWorkflowNodeProgressFields() {
        List<SubTableChange> changes = TaskApprovalCompletionComponent.computeSubTableRowChanges(
                List.of(Map.of(
                        "row_id", "transaction-1",
                        "amount", 100,
                        "task_current_node", "Transaction Investigation",
                        "sub_task_current_node", "Transaction Investigation",
                        "task_status", "IN_PROGRESS")),
                List.of(Map.of(
                        "row_id", "transaction-1",
                        "amount", 100,
                        "task_current_node", "Mark Completed",
                        "sub_task_current_node", "Mark Completed",
                        "task_status", "COMPLETED")));
        assertEquals(List.of(), changes);
    }

    @Test
    @DisplayName("continues to record an actual user-editable sub-table field change")
    void recordsActualBusinessFieldChange() {
        List<SubTableChange> changes = TaskApprovalCompletionComponent.computeSubTableRowChanges(
                List.of(Map.of("row_id", "transaction-1", "amount", 100)),
                List.of(Map.of("row_id", "transaction-1", "amount", 125)));
        assertEquals(1, changes.size());
        assertEquals("ROW_UPDATE", changes.get(0).getChangeType());
        assertEquals(Map.of("amount", 100), changes.get(0).getOldValues());
        assertEquals(Map.of("amount", 125), changes.get(0).getNewValues());
    }
}