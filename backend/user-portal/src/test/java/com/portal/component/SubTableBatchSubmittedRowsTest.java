package com.portal.component;

import com.portal.dto.SubTableBatchChange;
import com.portal.dto.SubTableBindingScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubTableBatchSubmittedRowsTest {
    @Test
    void deletedRowMustBeAbsentAndExplicitlyClaimed() {
        SubTableBatchChange change = change("DELETE", null);
        SubTableBindingScope scope = scope();
        scope.setDeletedRows(List.of(Map.of("line_key", "L1", "_wsRowVersion", 4)));
        assertThatCode(() -> SubTableBatchChangeGuard.validateSubmittedRows(
                List.of(change), List.of(scope), tables(List.of()))).doesNotThrowAnyException();
        assertThatThrownBy(() -> SubTableBatchChangeGuard.validateSubmittedRows(
                List.of(change), List.of(scope), tables(List.of(Map.of("line_key", "L1")))))
                .hasMessageContaining("still exists");
    }

    @Test
    void deletionWithoutVersionedScopeIsRejected() {
        assertThatThrownBy(() -> SubTableBatchChangeGuard.validateSubmittedRows(
                List.of(change("DELETE", null)), List.of(scope()), tables(List.of())))
                .hasMessageContaining("versioned deletion");
    }

    @Test
    void assignmentMayOmitFieldListWithoutCrashing() {
        assertThatCode(() -> SubTableBatchChangeGuard.validateSubmittedRows(
                List.of(change("ASSIGN", null)), List.of(scope()),
                tables(List.of(Map.of("line_key", "L1"))))).doesNotThrowAnyException();
    }

    private static SubTableBatchChange change(String operation, List<String> fields) {
        SubTableBatchChange.RowPatch patch = new SubTableBatchChange.RowPatch();
        patch.setRowId("line_key=L1");
        patch.setRowKey(Map.of("line_key", "L1"));
        patch.setFields(fields);
        SubTableBatchChange change = new SubTableBatchChange();
        change.setBatchControlId("lines");
        change.setBindingId("101");
        change.setOperation(operation);
        change.setRowPatches(List.of(patch));
        return change;
    }

    private static SubTableBindingScope scope() {
        return new SubTableBindingScope("101", "dw:lines", List.of(), true);
    }

    private static Map<String, Object> tables(List<Map<String, Object>> rows) {
        return Map.of("__subTables__", Map.of("dw:lines", rows));
    }
}
