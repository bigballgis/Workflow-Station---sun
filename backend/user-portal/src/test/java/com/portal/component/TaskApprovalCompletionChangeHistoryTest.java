package com.portal.component;

import com.portal.dto.SubTableChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
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
    @DisplayName("same row_id with a changed business field is a row update")
    void sameRowIdWithChangedFieldIsUpdate() {
        List<SubTableChange> changes = TaskApprovalCompletionComponent.computeSubTableRowChanges(
                List.of(Map.of("row_id", "corr-1", "channel", "Email", "assignee", "user-a")),
                List.of(Map.of("row_id", "corr-1", "channel", "Email", "assignee", "user-b")));
        assertEquals(1, changes.size());
        assertEquals("ROW_UPDATE", changes.get(0).getChangeType());
        assertEquals("corr-1", changes.get(0).getRowIdentifier());
        assertEquals(Map.of("assignee", "user-a"), changes.get(0).getOldValues());
        assertEquals(Map.of("assignee", "user-b"), changes.get(0).getNewValues());
    }

    @Test
    @DisplayName("same designer PK with a new row_id is the same row, not add/delete")
    void samePrimaryKeyWithNewRowIdIsNotAddDelete() {
        List<SubTableChange> changes = TaskApprovalCompletionComponent.computeSubTableRowChanges(
                List.of(Map.of(
                        "row_id", "uuid-A",
                        "correspondence_id", "Corr-000092",
                        "channel", "Email")),
                List.of(Map.of(
                        "row_id", "uuid-B",
                        "correspondence_id", "Corr-000092",
                        "channel", "Email")),
                List.of("correspondence_id"));
        assertEquals(List.of(), changes);
    }

    @Test
    @DisplayName("row_id churn without a designer PK is a real delete plus add")
    void rowIdChurnWithoutPrimaryKeyIsDeleteAndAdd() {
        List<SubTableChange> changes = TaskApprovalCompletionComponent.computeSubTableRowChanges(
                List.of(Map.of("row_id", "uuid-A", "channel", "Email")),
                List.of(Map.of("row_id", "uuid-B", "channel", "Email")));
        assertEquals(1, changes.stream().filter(c -> "ROW_DELETE".equals(c.getChangeType())).count());
        assertEquals(1, changes.stream().filter(c -> "ROW_ADD".equals(c.getChangeType())).count());
    }

    @Test
    @DisplayName("system-filled assignee_id is not a user edit")
    void assigneeAutofillIsNotAUserOperation() {
        List<SubTableChange> changes = TaskApprovalCompletionComponent.computeSubTableRowChanges(
                List.of(Map.of("row_id", "transaction-1", "amount", 100)),
                List.of(Map.of("row_id", "transaction-1", "amount", 100, "assignee_id", "user-1")));
        assertEquals(List.of(), changes);
    }

    @Test
    @DisplayName("replacing a row's business values on the same designer PK is an update")
    void singletonReplacementWithFieldChangeIsUpdate() {
        List<SubTableChange> changes = TaskApprovalCompletionComponent.computeSubTableRowChanges(
                List.of(Map.of(
                        "row_id", "uuid-A",
                        "correspondence_id", "Corr-1",
                        "channel", "Email")),
                List.of(Map.of(
                        "row_id", "uuid-B",
                        "correspondence_id", "Corr-1",
                        "channel", "SMS")),
                List.of("correspondence_id"));
        assertEquals(1, changes.size());
        assertEquals("ROW_UPDATE", changes.get(0).getChangeType());
        assertEquals("Corr-1", changes.get(0).getRowIdentifier());
        assertEquals(Map.of("channel", "Email"), changes.get(0).getOldValues());
        assertEquals(Map.of("channel", "SMS"), changes.get(0).getNewValues());
    }

    @Test
    @DisplayName("a truly new row is still recorded as add")
    void unmatchedNewRowIsAdd() {
        List<SubTableChange> changes = TaskApprovalCompletionComponent.computeSubTableRowChanges(
                List.of(),
                List.of(Map.of("row_id", "uuid-new", "channel", "Email")));
        assertEquals(1, changes.size());
        assertEquals("ROW_ADD", changes.get(0).getChangeType());
    }

    @Test
    @DisplayName("filling blank fields without changing existing values is a row add")
    void fillingBlankFieldsWithoutChangingExistingValuesIsAdd() {
        List<SubTableChange> changes = TaskApprovalCompletionComponent.computeSubTableRowChanges(
                List.of(Map.of(
                        "row_id", "5f9268dc",
                        "correspondence_type", "Customer Notification")),
                List.of(Map.of(
                        "row_id", "5f9268dc",
                        "correspondence_type", "Customer Notification",
                        "correspondence_channel", "Email",
                        "correspondence_mode", "Outbound")));
        assertEquals(1, changes.size());
        assertEquals("ROW_ADD", changes.get(0).getChangeType());
        assertEquals("5f9268dc", changes.get(0).getRowIdentifier());
        assertEquals(Map.of(
                "correspondence_channel", "Email",
                "correspondence_mode", "Outbound"), changes.get(0).getNewValues());
        assertEquals(null, changes.get(0).getOldValues());
    }

    @Test
    @DisplayName("filling blanks on the same row_id after PK allocation keeps the PK as the anchor")
    void fillingBlanksAfterPrimaryKeyAllocationKeepsPkAnchor() {
        Map<String, Object> emptyOld = new LinkedHashMap<>();
        emptyOld.put("row_id", "uuid-old");
        emptyOld.put("correspondence_channel", "");
        List<SubTableChange> changes = TaskApprovalCompletionComponent.computeSubTableRowChanges(
                List.of(emptyOld),
                List.of(Map.of(
                        "row_id", "uuid-old",
                        "correspondence_id", "Corr-000095",
                        "correspondence_channel", "Email",
                        "correspondence_mode", "Outbound")),
                List.of("correspondence_id"));
        assertEquals(1, changes.size());
        assertEquals("ROW_ADD", changes.get(0).getChangeType());
        assertEquals("Corr-000095", changes.get(0).getRowIdentifier());
        assertEquals(0, changes.stream().filter(c -> "ROW_DELETE".equals(c.getChangeType())).count());
    }

    @Test
    @DisplayName("filling blanks while changing an existing value remains a row update")
    void fillingBlanksWhileChangingExistingValueRemainsUpdate() {
        List<SubTableChange> changes = TaskApprovalCompletionComponent.computeSubTableRowChanges(
                List.of(Map.of(
                        "row_id", "corr-1",
                        "correspondence_channel", "Email")),
                List.of(Map.of(
                        "row_id", "corr-1",
                        "correspondence_channel", "Letter",
                        "mdc_status", "Sent")));
        assertEquals(1, changes.size());
        assertEquals("ROW_UPDATE", changes.get(0).getChangeType());
        Map<String, Object> expectedOld = new LinkedHashMap<>();
        expectedOld.put("correspondence_channel", "Email");
        expectedOld.put("mdc_status", null);
        assertEquals(expectedOld, changes.get(0).getOldValues());
        assertEquals(Map.of(
                "correspondence_channel", "Letter",
                "mdc_status", "Sent"), changes.get(0).getNewValues());
    }

    @Test
    @DisplayName("two complete rows with distinct PKs stay two rows at the diff layer")
    void twoCompleteIdenticalPayloadsRemainTwoRows() {
        List<SubTableChange> changes = TaskApprovalCompletionComponent.computeSubTableRowChanges(
                List.of(Map.of(
                        "row_id", "uuid-old",
                        "correspondence_id", "Corr-1",
                        "correspondence_channel", "Email",
                        "correspondence_type", "Customer Notification",
                        "mdc_status", "Draft",
                        "processed_date", "2026-09-05")),
                List.of(
                        Map.of(
                                "row_id", "uuid-new-1",
                                "correspondence_id", "Corr-1",
                                "correspondence_channel", "Letter",
                                "correspondence_type", "Customer Notification",
                                "mdc_status", "Draft",
                                "processed_date", "2026-09-05"),
                        Map.of(
                                "row_id", "uuid-new-2",
                                "correspondence_id", "Corr-2",
                                "correspondence_channel", "Letter",
                                "correspondence_type", "Customer Notification",
                                "mdc_status", "Draft",
                                "processed_date", "2026-09-05")),
                List.of("correspondence_id"));
        assertEquals(2, changes.size());
        assertEquals(1, changes.stream().filter(c -> "ROW_UPDATE".equals(c.getChangeType())).count());
        assertEquals(1, changes.stream().filter(c -> "ROW_ADD".equals(c.getChangeType())).count());
        assertEquals(0, changes.stream().filter(c -> "ROW_DELETE".equals(c.getChangeType())).count());
    }

    @Test
    @DisplayName("editing one PK row and adding a distinct second PK is update+add, not a phantom delete")
    void extraDistinctRowIsUpdateAndAddNotDelete() {
        List<SubTableChange> changes = TaskApprovalCompletionComponent.computeSubTableRowChanges(
                List.of(Map.of(
                        "row_id", "uuid-old",
                        "correspondence_id", "Corr-1",
                        "correspondence_type", "Customer Notification",
                        "mdc_status", "Draft",
                        "processed_date", "2026-09-05")),
                List.of(
                        Map.of(
                                "row_id", "uuid-new-1",
                                "correspondence_id", "Corr-1",
                                "correspondence_type", "Customer Notification",
                                "mdc_status", "Acknowledged",
                                "processed_date", "2026-09-26"),
                        Map.of(
                                "row_id", "uuid-new-2",
                                "correspondence_id", "Corr-2",
                                "correspondence_type", "Complaint",
                                "mdc_status", "Closed",
                                "processed_date", "2026-09-10")),
                List.of("correspondence_id"));
        assertEquals(2, changes.size());
        assertEquals(1, changes.stream().filter(c -> "ROW_UPDATE".equals(c.getChangeType())).count());
        assertEquals(1, changes.stream().filter(c -> "ROW_ADD".equals(c.getChangeType())).count());
        assertEquals(0, changes.stream().filter(c -> "ROW_DELETE".equals(c.getChangeType())).count());
    }

    @Test
    @DisplayName("an identity-only same-id overlay does not record clearing every business field")
    void identityOnlyOverlayDoesNotRecordFieldClears() {
        List<SubTableChange> changes = TaskApprovalCompletionComponent.computeSubTableRowChanges(
                List.of(Map.of(
                        "row_id", "uuid-same",
                        "correspondence_channel", "Email",
                        "correspondence_mode", "Outbound",
                        "mdc_status", "Draft")),
                List.of(Map.of("row_id", "uuid-same")));
        assertEquals(List.of(), changes);
    }

    @Test
    @DisplayName("clearing every business field on the same row is recorded as an update")
    void clearingEveryBusinessFieldIsRecorded() {
        List<SubTableChange> changes = TaskApprovalCompletionComponent.computeSubTableRowChanges(
                List.of(Map.of(
                        "row_id", "uuid-same",
                        "correspondence_channel", "Email",
                        "correspondence_mode", "Outbound",
                        "mdc_status", "Draft")),
                List.of(Map.of(
                        "row_id", "uuid-same",
                        "correspondence_channel", "",
                        "correspondence_mode", "",
                        "mdc_status", "")));
        assertEquals(1, changes.size());
        assertEquals("ROW_UPDATE", changes.get(0).getChangeType());
        assertEquals(Map.of(
                "correspondence_channel", "Email",
                "correspondence_mode", "Outbound",
                "mdc_status", "Draft"), changes.get(0).getOldValues());
        assertEquals(Map.of(
                "correspondence_channel", "",
                "correspondence_mode", "",
                "mdc_status", ""), changes.get(0).getNewValues());
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

    @Test
    @DisplayName("nested row_id churn of one PK does not delete a sibling PK")
    void nestedUuidChurnDoesNotDeleteSiblingRow() {
        List<SubTableChange> changes = TaskApprovalCompletionComponent.computeSubTableRowChanges(
                List.of(
                        Map.of("row_id", "c6ecc302",
                                "correspondence_id", "Corr-1",
                                "correspondence_channel", "Phone",
                                "correspondence_mode", "Outbound",
                                "correspondence_type", "Customer Notification"),
                        Map.of("row_id", "95548a8e",
                                "correspondence_id", "Corr-2",
                                "correspondence_date_to_cardholder", "2026-09-25",
                                "mdc_status", "Acknowledged",
                                "processed_date", "2026-09-17"),
                        Map.of("row_id", "bbf2a694",
                                "correspondence_id", "Corr-3",
                                "correspondence_channel", "Letter",
                                "correspondence_mode", "Outbound",
                                "correspondence_type", "Customer Notification")),
                List.of(
                        Map.of("row_id", "0b36d851",
                                "correspondence_id", "Corr-2",
                                "correspondence_channel", "Phone",
                                "correspondence_date_to_cardholder", "2026-09-25",
                                "mdc_status", "Acknowledged",
                                "processed_date", "2026-09-17"),
                        Map.of("row_id", "bbf2a694",
                                "correspondence_id", "Corr-3",
                                "correspondence_channel", "Letter",
                                "correspondence_mode", "Outbound",
                                "correspondence_type", "Customer Notification")),
                List.of("correspondence_id"));
        assertEquals(1, changes.stream().filter(c -> "ROW_DELETE".equals(c.getChangeType())).count());
        assertEquals("Corr-1", changes.stream()
                .filter(c -> "ROW_DELETE".equals(c.getChangeType()))
                .findFirst().orElseThrow().getRowIdentifier());
        assertEquals(1, changes.stream().filter(c -> "ROW_ADD".equals(c.getChangeType())).count());
        assertEquals("Corr-2", changes.stream()
                .filter(c -> "ROW_ADD".equals(c.getChangeType()))
                .findFirst().orElseThrow().getRowIdentifier());
    }

    @Test
    @DisplayName("thin nested copy of the same PK does not clear Email")
    void thinNestedCopyDoesNotClearEmail() {
        Map<String, Object> thick = new LinkedHashMap<>();
        thick.put("row_id", "f1109821");
        thick.put("correspondence_id", "Corr-000093");
        thick.put("correspondence_channel", "Email");
        thick.put("correspondence_mode", "Outbound");
        thick.put("processed_date", "2026-09-09");
        Map<String, Object> thin = new LinkedHashMap<>();
        thin.put("row_id", "f1109821");
        thin.put("correspondence_id", "Corr-000093");
        thin.put("correspondence_channel", null);
        thin.put("correspondence_mode", "Outbound");
        thin.put("processed_date", null);
        List<SubTableChange> changes = TaskApprovalCompletionComponent.computeSubTableRowChanges(
                List.of(thick),
                List.of(thick, thin),
                List.of("correspondence_id"));
        assertEquals(List.of(), changes);
        assertEquals("Corr-000093", ChangeHistoryAuditRowKey.derive(thick, List.of("correspondence_id")));
    }

    @Test
    @DisplayName("a stamped designer PK survives diff when pkFields are not passed again")
    void stampedPrimaryKeySurvivesDiffWithoutPkFields() {
        Map<String, Object> oldRow = new LinkedHashMap<>();
        oldRow.put("row_id", "e2e-corr-identity-1");
        oldRow.put("correspondence_id", "Corr-000096");
        oldRow.put("correspondence_channel", "Email");
        ChangeHistoryAuditRowKey.stamp(oldRow, List.of("correspondence_id"));
        Map<String, Object> newRow = new LinkedHashMap<>(oldRow);
        newRow.put("mdc_status", "Draft");
        List<SubTableChange> changes = TaskApprovalCompletionComponent.computeSubTableRowChanges(
                List.of(oldRow), List.of(newRow));
        assertEquals(1, changes.size());
        assertEquals("ROW_ADD", changes.get(0).getChangeType());
        assertEquals("Corr-000096", changes.get(0).getRowIdentifier());
        assertEquals(Map.of("mdc_status", "Draft"), changes.get(0).getNewValues());
    }

    @Test
    @DisplayName("a row identified only by designer PK still records ADD with that identifier")
    void pkOnlyRowHasRowIdentifier() {
        List<SubTableChange> changes = TaskApprovalCompletionComponent.computeSubTableRowChanges(
                List.of(),
                List.of(Map.of(
                        "correspondence_id", "Corr-000095",
                        "correspondence_mode", "Outbound")),
                List.of("correspondence_id"));
        assertEquals(1, changes.size());
        assertEquals("ROW_ADD", changes.get(0).getChangeType());
        assertEquals("Corr-000095", changes.get(0).getRowIdentifier());
    }

    @Test
    @DisplayName("removing a row without uuid churn is still recorded as delete")
    void removingARowWithoutUuidChurnIsDelete() {
        List<SubTableChange> changes = TaskApprovalCompletionComponent.computeSubTableRowChanges(
                List.of(
                        Map.of("row_id", "keep", "channel", "Email"),
                        Map.of("row_id", "gone", "channel", "Letter")),
                List.of(Map.of("row_id", "keep", "channel", "Email")));
        assertEquals(1, changes.size());
        assertEquals("ROW_DELETE", changes.get(0).getChangeType());
        assertEquals("gone", changes.get(0).getRowIdentifier());
    }
}