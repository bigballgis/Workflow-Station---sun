package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.dto.SubTableBatchChange;
import com.portal.dto.SubTableBindingScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubTableBatchAddedRowGuardTest {
    @Test
    void validatesCompleteNewRowsAgainstFrozenTypesAndRequiredFields() {
        assertThatCode(() -> validate(Map.of("item_key", "L1", "quantity", 0, "enabled", false)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNumericStringsAndBlankRequiredValues() {
        assertThatThrownBy(() -> validate(Map.of("item_key", "L1", "quantity", "2", "enabled", false)))
                .hasMessageContaining("numeric type");
        assertThatThrownBy(() -> validate(Map.of("item_key", "L1", "quantity", 2)))
                .hasMessageContaining("requires field enabled");
    }

    @Test
    void rejectsOutOfRangeIntegersBeforePersistence() {
        assertThatThrownBy(() -> validate(Map.of("item_key", "L1", "quantity", 2147483648L, "enabled", false)))
                .hasMessageContaining("in-range integer");
    }

    @Test
    void refusesFieldsNotRepresentedByTheBatchFormOrGeneratedIdentity() {
        assertThatThrownBy(() -> validate(Map.of("item_key", "L1", "quantity", 2, "enabled", false, "hidden_admin_value", "forged")))
                .hasMessageContaining("outside the frozen batch form");
    }

    @Test
    void validatesTimestampValuesUsingTheExistingDateTimeControlFormat() {
        assertThatCode(() -> validate(Map.of("item_key", "L1", "quantity", 2, "enabled", false,
                "recorded_at", "2026-10-13 17:00:00"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> validate(Map.of("item_key", "L1", "quantity", 2, "enabled", false,
                "recorded_at", "invalid"))).hasMessageContaining("invalid timestamp");
    }

    private void validate(Map<String, Object> row) {
        Map<String, Object> design = Map.of("binding", Map.of("bindingType", "SUB", "bindingMode", "EDITABLE", "bindingLinkMode", "structuralFk"),
                "fields", List.of(Map.of("fieldName", "quantity", "dataType", "INTEGER", "nullable", false),
                        Map.of("fieldName", "enabled", "dataType", "BOOLEAN", "nullable", false),
                        Map.of("fieldName", "recorded_at", "dataType", "TIMESTAMP", "nullable", true)));
        Map<String, Object> snapshot = Map.of("schemaVersion", 1, "forms", Map.of("form-1", Map.of("controls",
                List.of(Map.of("batchControlId", "grid", "rowDesign", design, "batchForm", Map.of("rule", List.of(
                        Map.of("type", "inputNumber", "field", "quantity"), Map.of("type", "switch", "field", "enabled"),
                        Map.of("type", "datetime", "field", "recorded_at"))))))));
        SubTableBatchChange.RowPatch patch = new SubTableBatchChange.RowPatch();
        patch.setRowKey(Map.of("item_key", "L1"));
        SubTableBatchChange change = new SubTableBatchChange();
        change.setBindingId("101");
        change.setBatchControlId("grid");
        change.setOperation("ADD");
        change.setRowPatches(List.of(patch));
        SubTableBatchAddedRowGuard.validate(new SubTableBatchAddedRowGuard.Request(
                Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot), Map.of("sourceId", "form-1"),
                List.of(change), List.of(new SubTableBindingScope("101", "dw:lines", List.of(), false)),
                Map.of("__subTables__", Map.of("dw:lines", List.of(row))), new ObjectMapper()));
    }
}
