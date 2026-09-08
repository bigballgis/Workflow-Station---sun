package com.portal.component;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeHistoryAuditRowKeyTest {

    @Test
    void designerPrimaryKeyWinsOverRowId() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("row_id", "f1109821");
        row.put("correspondence_id", "Corr-000093");
        assertThat(ChangeHistoryAuditRowKey.derive(row, List.of("correspondence_id")))
                .isEqualTo("Corr-000093");
    }

    @Test
    void missingPrimaryKeyFallsBackToRowId() {
        Map<String, Object> row = Map.of("row_id", "f1109821");
        assertThat(ChangeHistoryAuditRowKey.derive(row, List.of("correspondence_id")))
                .isEqualTo("f1109821");
    }

    @Test
    void sameLogicalRowMatchesOnPrimaryKeyDespiteRowIdChurn() {
        Map<String, Object> before = Map.of(
                "row_id", "uuid-A",
                "correspondence_id", "Corr-000092");
        Map<String, Object> after = Map.of(
                "row_id", "uuid-B",
                "correspondence_id", "Corr-000092");
        assertThat(ChangeHistoryAuditRowKey.sameLogicalRow(
                before, after, List.of("correspondence_id"))).isTrue();
    }

    @Test
    void stampDoesNotDowngradePrimaryKeyWhenPkFieldsAreUnknown() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("row_id", "e2e-corr-identity-1");
        row.put("correspondence_id", "Corr-000096");
        ChangeHistoryAuditRowKey.stamp(row, List.of("correspondence_id"));
        assertThat(ChangeHistoryAuditRowKey.resolve(row)).isEqualTo("Corr-000096");
        ChangeHistoryAuditRowKey.stamp(row, List.of());
        assertThat(ChangeHistoryAuditRowKey.resolve(row)).isEqualTo("Corr-000096");
    }

    @Test
    void rowWithOnlyPrimaryKeyIsTrackable() {
        Map<String, Object> row = Map.of("correspondence_id", "Corr-000095");
        assertThat(ChangeHistoryAuditRowKey.derive(row, List.of("correspondence_id")))
                .isEqualTo("Corr-000095");
        assertThat(ChangeHistoryComponent.resolveRowIdentifier(new LinkedHashMap<>(row)))
                .isNull();
        Map<String, Object> stamped = new LinkedHashMap<>(row);
        ChangeHistoryAuditRowKey.stamp(stamped, List.of("correspondence_id"));
        assertThat(ChangeHistoryComponent.resolveRowIdentifier(stamped))
                .isEqualTo("Corr-000095");
    }
}
