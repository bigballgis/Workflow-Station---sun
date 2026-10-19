package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.dto.SubTableBatchChange;
import com.portal.dto.SubTableBindingScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class SubTableBatchEditedRowValidationTest {
    @Test
    void editingAlternateBindingRowsDoesNotRequireTheOtherForeignKey() {
        List<?> fields = List.of(
                Map.of("fieldName", "primary_ref", "isForeignKey", true, "dataType", "VARCHAR", "nullable", false),
                Map.of("fieldName", "secondary_ref", "isForeignKey", true, "dataType", "VARCHAR", "nullable", false),
                Map.of("fieldName", "quantity", "dataType", "INTEGER", "nullable", false));
        assertThatCode(() -> SubTableBatchEditedRowGuard.validateMetadata(fields, Map.of("secondary_ref", "P1", "quantity", 8)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> SubTableBatchEditedRowGuard.validateMetadata(fields, Map.of("secondary_ref", "P1")))
                .hasMessageContaining("requires field quantity");
    }
    static List<Map<String, Object>> invalidRows() {
        return List.of(row("2", false, "2026-10-18", "ok"), row(2147483648L, false, "2026-10-18", "ok"),
                row(1, "false", "2026-10-18", "ok"), row(1, false, "2026-02-30", "ok"),
                row(-1, false, "2026-10-18", "ok"), row(1, false, "2026-10-18", "toolong"),
                row(1, null, "2026-10-18", "ok"));
    }

    @ParameterizedTest
    @MethodSource("invalidRows")
    void rejectsInvalidCompleteRowsBeforeCanonicalWrite(Map<String, Object> edited) {
        var submitted = Map.<String, Object>of("__subTables__", Map.of("dw:lines", List.of(edited)));
        var copy = new ObjectMapper().convertValue(submitted, Map.class);
        assertThatThrownBy(() -> validate(submitted, List.of(change("L1")))).isInstanceOf(RuntimeException.class);
        assertThat(submitted).isEqualTo(copy);
    }

    @Test
    void permitsZeroFalseAndUntouchedFieldsOutsideBatchSurface() {
        Map<String, Object> edited = row(0, false, "2026-10-18", "ok");
        edited.put("unknown_legacy_payload", Map.of("keep", "unchanged"));
        assertThatCode(() -> validate(Map.of("__subTables__", Map.of("dw:lines", List.of(edited))),
                List.of(change("L1")))).doesNotThrowAnyException();
    }

    @Test
    void secondRowFailureDoesNotMutateFirstRowOrOtherStoredFields() {
        Map<String, Object> first = row(0, false, "2026-10-18", "ok");
        Map<String, Object> second = row(1, false, "2026-10-18", "toolong");
        second.put("item_token", "L2");
        var submitted = Map.<String, Object>of("__subTables__", Map.of("dw:lines", List.of(first, second)));
        var copy = new ObjectMapper().convertValue(submitted, Map.class);
        assertThatThrownBy(() -> validate(submitted, List.of(change("L1"), change("L2")))).isInstanceOf(RuntimeException.class);
        assertThat(submitted).isEqualTo(copy);
    }

    @Test
    void legacyOrOrdinarySaveWithNoBatchIntentKeepsOriginalPath() {
        assertThatCode(() -> SubTableBatchPayloadGuard.validate(new SubTableBatchAddedRowGuard.Request(
                Map.of(), Map.of(), List.of(), List.of(), Map.of("__subTables__", Map.of("dw:lines", List.of(row("bad", "bad", "bad", "bad")))),
                new ObjectMapper()))).doesNotThrowAnyException();
    }

    private static Map<String, Object> row(Object quantity, Object enabled, Object date, Object note) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("item_token", "L1"); row.put("quantity", quantity); row.put("enabled", enabled);
        row.put("due_on", date); row.put("hidden_note", note);
        return row;
    }

    private static SubTableBatchChange change(String id) {
        var patch = new SubTableBatchChange.RowPatch();
        patch.setRowId(id); patch.setRowKey(Map.of("item_token", id)); patch.setFields(List.of("quantity"));
        var change = new SubTableBatchChange();
        change.setBindingId("101"); change.setBatchControlId("grid"); change.setOperation("EDIT");
        change.setRowPatches(List.of(patch));
        return change;
    }

    private static void validate(Map<String, Object> submitted, List<SubTableBatchChange> changes) {
        var fields = List.of(Map.of("fieldName", "quantity", "dataType", "INTEGER", "nullable", false),
                Map.of("fieldName", "enabled", "dataType", "BOOLEAN", "nullable", false),
                Map.of("fieldName", "due_on", "dataType", "DATE", "nullable", false),
                Map.of("fieldName", "hidden_note", "dataType", "VARCHAR", "length", 4, "nullable", true));
        Map<String, Object> control = Map.of("batchControlId", "grid", "bindingId", "101", "rowDesign", Map.of("fields", fields),
                "batchForm", Map.of("rule", List.of(Map.of("type", "inputNumber", "field", "quantity", "props", Map.of("min", 0)))));
        var snapshot = Map.of("schemaVersion", 1, "forms", Map.of("form", Map.of("controls", List.of(control))));
        SubTableBatchPayloadGuard.validate(new SubTableBatchAddedRowGuard.Request(
                Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot), Map.of("sourceId", "form"), changes,
                List.of(new SubTableBindingScope("101", "dw:lines", List.of(), false)), submitted, new ObjectMapper()));
    }
}
