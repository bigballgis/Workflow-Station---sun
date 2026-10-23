package com.portal.component;

import com.portal.dto.SubTableBatchChange;
import com.portal.dto.SubTableBindingScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubTableBatchScopeDesignTest {
    @Test
    void ordinarySavesDoNotRequireOrOverlayBatchMetadata() {
        Map<String, SubTableWriteDesign.Binding> current = Map.of();
        assertThat(SubTableBatchScopeDesign.overlay(current, null, null)).isSameAs(current);
        assertThat(SubTableBatchScopeDesign.overlay(current, Map.of(), List.of())).isSameAs(current);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDIT", "ASSIGN"})
    void missingFrozenMetadataCannotFallBackToCurrentBinding(String operation) {
        SubTableBatchChange change = new SubTableBatchChange();
        change.setOperation(operation);
        change.setBatchControlId("grid");
        change.setBindingId("101");
        assertThatThrownBy(() -> SubTableBatchScopeDesign.overlay(Map.of(), Map.of(), List.of(change)))
                .hasMessageContaining("Frozen batch scope control is missing");
    }

    @ParameterizedTest
    @CsvSource({"ADD,structuralFk", "DELETE,structuralFk", "EDIT,structuralFk", "ASSIGN,structuralFk",
            "EDIT,miParticipantRow", "ASSIGN,miParticipantRow"})
    void usesFrozenKeysForEveryBatchOperationWhenTheCurrentCatalogBindingWasOverwritten(String operation, String mode) {
        Map<String, Object> design = Map.of("binding", Map.of("tableName", "lines",
                        "foreignKeyField", "miParticipantRow".equals(mode) ? "line_key" : "case_ref",
                        "filterFkFieldName", "case_ref", "primaryKeyFields", List.of("line_key"),
                        "bindingLinkMode", mode, "fieldDefinitions", List.of(Map.of("fieldName", "case_ref", "isForeignKey", true, "refTableId", 1,
                                "refPrimaryKeyFields", List.of("case_key")))),
                "parentTablesById", Map.of("1", Map.of("tableName", "cases", "tableType", "MAIN")));
        Map<String, Object> snapshot = Map.of("forms", Map.of("form-1", Map.of("controls", List.of(
                Map.of("batchControlId", "grid", "bindingId", "101", "rowDesign", design))),
                "other-form", Map.of("controls", List.of(Map.of("batchControlId", "grid", "bindingId", "999", "rowDesign", Map.of())))));
        SubTableBatchChange change = new SubTableBatchChange();
        change.setBatchControlId("grid");
        change.setBindingId("101");
        change.setOperation(operation);
        SubTableWriteDesign.Binding changed = new SubTableWriteDesign.Binding("101", "dw:lines", "other_ref", "MAIN", "other_cases", List.of("other_key"), List.of("other_id"), "structuralFk");
        SubTableWriteDesign.Binding resolved = SubTableBatchScopeDesign.overlay(Map.of("101", changed),
                Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot), List.of(change)).get("101");
        assertThat(resolved.pk()).containsExactly("line_key");
        assertThat(resolved.filterField()).isEqualTo("case_ref");
        assertThat(resolved.refTableName()).isEqualTo("cases");
        assertThat(resolved.linkMode()).isEqualTo(mode);
        Map<String, Object> original = Map.of("line_key", "L1", "case_ref", "C1", "note", "before", "_wsRowVersion", 1);
        Map<String, Object> row = new java.util.LinkedHashMap<>(original);
        row.put("note", "after");
        Map<String, Object> submitted = new java.util.LinkedHashMap<>(Map.of("dw:lines", List.of(row)));
        SubTableBindingScope scope = new SubTableBindingScope("101", "dw:lines", List.of(Map.of("line_key", "L1")), false);
        var guard = new SubTableBindingScopeGuard();
        guard.assertAndApply(List.of(scope), "synthetic-fu", Map.of("case_key", "C1"), submitted,
                Map.of("dw:lines", List.of(original)), Map.of("101", resolved));
        assertThat(((List<?>) submitted.get("dw:lines")).get(0)).isEqualTo(Map.of(
                "line_key", "L1", "case_ref", "C1", "note", "after", "_wsRowVersion", 2));
        assertThatThrownBy(() -> guard.assertAndApply(List.of(scope), "synthetic-fu", Map.of("case_key", "C2"),
                new java.util.LinkedHashMap<>(Map.of("dw:lines", List.of(row))), Map.of("dw:lines", List.of(original)), Map.of("101", resolved)))
                .hasMessageContaining("not allowed to write");
        Map<String, Object> ambiguous = Map.of("forms", Map.of("first", Map.of("controls", List.of(
                Map.of("batchControlId", "grid", "bindingId", "101", "rowDesign", design))), "second", Map.of("controls", List.of(
                Map.of("batchControlId", "grid", "bindingId", "101", "rowDesign", design)))));
        assertThatThrownBy(() -> SubTableBatchScopeDesign.overlay(Map.of("101", changed),
                Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, ambiguous), List.of(change)))
                .hasMessageContaining("ambiguous");
    }
}
