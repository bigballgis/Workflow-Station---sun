package com.portal.component;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Change-history sub-table slice merger")
class ChangeHistorySubTableSliceMergerTest {

    @Test
    @DisplayName("later thin copy of the same identity cannot clear Email")
    void laterThinCopyDoesNotClearEmail() {
        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        ChangeHistorySubTableSliceMerger.mergeSliceRows(rows, List.of(row(
                "f1109821",
                "correspondence_id", "Corr-000093",
                "correspondence_channel", "Email",
                "correspondence_mode", "Outbound",
                "processed_date", "2026-09-09")), List.of("correspondence_id"));
        Map<String, Object> thin = new LinkedHashMap<>();
        thin.put("row_id", "f1109821");
        thin.put("correspondence_id", "Corr-000093");
        thin.put("correspondence_channel", null);
        thin.put("correspondence_mode", "Outbound");
        thin.put("processed_date", null);
        ChangeHistorySubTableSliceMerger.mergeSliceRows(rows, List.of(thin), List.of("correspondence_id"));
        assertThat(rows).hasSize(1);
        assertThat(rows.get("Corr-000093"))
                .containsEntry("correspondence_channel", "Email")
                .containsEntry("processed_date", "2026-09-09")
                .containsEntry("correspondence_mode", "Outbound");
    }

    @Test
    @DisplayName("later slice with a new PK is kept as its own row")
    void laterDistinctPrimaryKeyIsKept() {
        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        ChangeHistorySubTableSliceMerger.mergeSliceRows(rows, List.of(row(
                "uuid-1",
                "correspondence_id", "Corr-000092",
                "correspondence_channel", "Email")), List.of("correspondence_id"));
        ChangeHistorySubTableSliceMerger.mergeSliceRows(rows, List.of(row(
                "uuid-2",
                "correspondence_id", "Corr-000094",
                "correspondence_channel", "Email")), List.of("correspondence_id"));
        assertThat(rows.keySet()).containsExactly("Corr-000092", "Corr-000094");
    }

    @Test
    @DisplayName("persist overlay keeps filled fields when the submitted copy is thin")
    void persistOverlayKeepsFilledFieldsFromBaseline() {
        Map<String, Object> baseline = Map.of("392", List.of(row(
                "row-105",
                "correspondence_id", "Corr-000105",
                "mdc_status", "Draft",
                "date_to_cardholder", "2026-09-11")));
        Map<String, Object> thin = new LinkedHashMap<>();
        thin.put(com.platform.common.jdbc.SubTableRowIdentity.CANONICAL_FIELD, "row-105");
        thin.put("row_id", "row-105");
        thin.put("correspondence_id", "Corr-000105");
        thin.put("mdc_status", null);
        thin.put("date_to_cardholder", null);
        Map<String, Object> edited = row(
                "row-106",
                "correspondence_id", "Corr-000106",
                "mdc_status", "Sent",
                "date_to_cardholder", "2026-10-02");
        Map<String, Object> submitted = Map.of("dw:atm_correspondence", List.of(thin, edited));

        @SuppressWarnings("unchecked")
        Map<String, Object> merged = (Map<String, Object>) ChangeHistorySubTableSliceMerger
                .overlaySubmittedOnBaseline(baseline, submitted);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) merged.get("dw:atm_correspondence");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0))
                .containsEntry("correspondence_id", "Corr-000105")
                .containsEntry("mdc_status", "Draft")
                .containsEntry("date_to_cardholder", "2026-09-11");
        assertThat(rows.get(1))
                .containsEntry("mdc_status", "Sent")
                .containsEntry("date_to_cardholder", "2026-10-02");
    }

    @Test
    @DisplayName("persist overlay lets a filled submitted value replace Draft")
    void persistOverlayFilledSubmittedValueWins() {
        Map<String, Object> baseline = Map.of("dw:atm_correspondence", List.of(row(
                "row-106",
                "correspondence_id", "Corr-000106",
                "mdc_status", "Draft")));
        Map<String, Object> submitted = Map.of("dw:atm_correspondence", List.of(row(
                "row-106",
                "correspondence_id", "Corr-000106",
                "mdc_status", "Sent")));
        @SuppressWarnings("unchecked")
        Map<String, Object> merged = (Map<String, Object>) ChangeHistorySubTableSliceMerger
                .overlaySubmittedOnBaseline(baseline, submitted);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) merged.get("dw:atm_correspondence");
        assertThat(rows.get(0)).containsEntry("mdc_status", "Sent");
    }

    @Test
    @DisplayName("persist overlay does not mix another table's row that only shares id")
    void persistOverlayDoesNotMixAnotherTableOnSharedId() {
        Map<String, Object> items = new LinkedHashMap<>();
        items.put(com.platform.common.jdbc.SubTableRowIdentity.CANONICAL_FIELD, "item-1");
        items.put("id", "1");
        items.put("sku", "X");
        Map<String, Object> people = new LinkedHashMap<>();
        people.put(com.platform.common.jdbc.SubTableRowIdentity.CANONICAL_FIELD, "people-1");
        people.put("id", "1");
        people.put("name", "Alice");
        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("200", List.of(items));
        baseline.put("100", List.of(people));

        Map<String, Object> submittedPeople = new LinkedHashMap<>();
        submittedPeople.put(com.platform.common.jdbc.SubTableRowIdentity.CANONICAL_FIELD, "people-1");
        submittedPeople.put("id", "1");
        submittedPeople.put("name", null);
        Map<String, Object> submitted = new LinkedHashMap<>();
        submitted.put("dw:people", List.of(submittedPeople));

        @SuppressWarnings("unchecked")
        Map<String, Object> merged = (Map<String, Object>) ChangeHistorySubTableSliceMerger
                .overlaySubmittedOnBaseline(baseline, submitted);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) merged.get("dw:people");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
                .containsEntry("name", "Alice")
                .doesNotContainKey("sku");
    }

    private static Map<String, Object> row(String rowId, Object... fields) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(com.platform.common.jdbc.SubTableRowIdentity.CANONICAL_FIELD, rowId);
        row.put("row_id", rowId);
        for (int i = 0; i < fields.length; i += 2) {
            row.put(String.valueOf(fields[i]), fields[i + 1]);
        }
        return row;
    }
}
