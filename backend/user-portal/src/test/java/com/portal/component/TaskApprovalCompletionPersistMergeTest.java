package com.portal.component;

import com.portal.dto.SubTableChange;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mark Completed (parent approval) must not persist a thin copy of an untouched
 * Correspondence row. The user deleting a different row is still a real DELETE.
 */
@DisplayName("Approval persist merge of __subTables__")
class TaskApprovalCompletionPersistMergeTest {

    @Test
    @DisplayName("thin submitted copy cannot clear Draft/date on an untouched identity")
    void thinSubmittedCopyDoesNotWipeUntouchedRow() {
        Map<String, Object> persisted105 = correspondence(
                "row-105",
                "Corr-000105",
                "Letter",
                "Draft",
                "2026-09-11");
        Map<String, Object> persisted106 = correspondence(
                "row-106",
                "Corr-000106",
                "Email",
                null,
                null);

        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("dw:atm_correspondence", List.of(copy(persisted105), copy(persisted106)));

        Map<String, Object> submitted105 = correspondence(
                "row-105",
                "Corr-000105",
                "Letter",
                null,
                null);
        Map<String, Object> submitted106 = correspondence(
                "row-106",
                "Corr-000106",
                "Email",
                "Sent",
                "2026-10-02");
        Map<String, Object> submission = new HashMap<>();
        Map<String, Object> submittedTables = new LinkedHashMap<>();
        submittedTables.put("dw:atm_correspondence", List.of(submitted105, submitted106));
        submission.put("__subTables__", submittedTables);

        Map<String, Object> merged = mergeApproval(baseline, submission);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows =
                (List<Map<String, Object>>) ((Map<String, Object>) merged.get("__subTables__"))
                        .get("dw:atm_correspondence");
        Map<String, Object> kept105 = rowByPk(rows, "Corr-000105");
        Map<String, Object> edited106 = rowByPk(rows, "Corr-000106");
        assertThat(kept105)
                .containsEntry("mdc_status", "Draft")
                .containsEntry("date_to_cardholder", "2026-09-11");
        assertThat(edited106)
                .containsEntry("mdc_status", "Sent")
                .containsEntry("date_to_cardholder", "2026-10-02");
        List<SubTableChange> changes = TaskApprovalCompletionComponent.computeSubTableRowChanges(
                List.of(copy(persisted105), copy(persisted106)),
                rows);
        assertThat(changes).extracting(SubTableChange::getRowIdentifier).containsExactly("row-106");
        assertThat(changes.get(0).getNewValues())
                .containsEntry("mdc_status", "Sent")
                .containsEntry("date_to_cardholder", "2026-10-02");
    }

    @Test
    @DisplayName("a row absent from the submitted slice is still deleted")
    void submittedSliceCanDeleteARow() {
        Map<String, Object> persisted104 = correspondence(
                "row-104",
                "Corr-000104",
                "Letter",
                "Draft",
                "2026-09-10");
        Map<String, Object> persisted105 = correspondence(
                "row-105",
                "Corr-000105",
                "Letter",
                "Draft",
                "2026-09-11");
        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("dw:atm_correspondence", List.of(persisted104, persisted105));

        Map<String, Object> submission = new HashMap<>();
        Map<String, Object> submittedTables = new LinkedHashMap<>();
        submittedTables.put("dw:atm_correspondence", List.of(copy(persisted105)));
        submission.put("__subTables__", submittedTables);

        Map<String, Object> merged = mergeApproval(baseline, submission);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows =
                (List<Map<String, Object>>) ((Map<String, Object>) merged.get("__subTables__"))
                        .get("dw:atm_correspondence");
        assertThat(rows).extracting(row -> row.get("correspondence_id"))
                .containsExactly("Corr-000105");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mergeApproval(
            Map<String, Object> baselineSubTables, Map<String, Object> submission) {
        ProcessInstanceRepository repository = mock(ProcessInstanceRepository.class);
        when(repository.findById("pi-1")).thenReturn(Optional.of(ProcessInstance.builder()
                .id("pi-1")
                .variables(new HashMap<>(Map.of("__subTables__", baselineSubTables)))
                .build()));
        TaskApprovalCompletionComponent approval = new TaskApprovalCompletionComponent(
                null, null, repository, null, null, null, null, null);
        return ReflectionTestUtils.invokeMethod(approval, "mergeApprovalVariables", "pi-1", submission);
    }

    private static Map<String, Object> correspondence(
            String rowId, String correspondenceId, String channel, String status, String dateToCh) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("row_id", rowId);
        row.put("correspondence_id", correspondenceId);
        row.put("correspondence_channel", channel);
        row.put("mdc_status", status);
        row.put("date_to_cardholder", dateToCh);
        return row;
    }

    private static Map<String, Object> copy(Map<String, Object> row) {
        return new LinkedHashMap<>(row);
    }

    private static Map<String, Object> rowByPk(List<Map<String, Object>> rows, String correspondenceId) {
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (correspondenceId.equals(row.get("correspondence_id"))) {
                matches.add(row);
            }
        }
        assertThat(matches).hasSize(1);
        return matches.get(0);
    }
}
