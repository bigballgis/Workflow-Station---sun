package com.portal.component;

import com.portal.dto.SubTableBatchChange;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class SubTableBatchScopeDesignTest {
    @Test
    void usesFrozenMembershipKeysWhenTheCurrentCatalogBindingWasOverwritten() {
        Map<String, Object> design = Map.of("binding", Map.of("tableName", "lines", "foreignKeyField", "case_ref", "primaryKeyFields", List.of("line_key"),
                        "bindingLinkMode", "structuralFk", "fieldDefinitions", List.of(Map.of("fieldName", "case_ref", "isForeignKey", true, "refTableId", 1,
                                "refPrimaryKeyFields", List.of("case_key")))),
                "parentTablesById", Map.of("1", Map.of("tableName", "cases", "tableType", "MAIN")));
        Map<String, Object> snapshot = Map.of("forms", Map.of("form-1", Map.of("controls", List.of(
                Map.of("batchControlId", "grid", "rowDesign", design)))));
        SubTableBatchChange change = new SubTableBatchChange();
        change.setBatchControlId("grid");
        change.setBindingId("101");
        change.setOperation("ADD");
        SubTableWriteDesign.Binding changed = new SubTableWriteDesign.Binding("101", "dw:lines", "other_ref", "MAIN", "other_cases", List.of("other_key"), List.of("other_id"), "structuralFk");
        SubTableWriteDesign.Binding resolved = SubTableBatchScopeDesign.overlay(Map.of("101", changed),
                Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot), List.of(change)).get("101");
        assertThat(resolved.pk()).containsExactly("line_key");
        assertThat(resolved.filterField()).isEqualTo("case_ref");
        assertThat(resolved.refTableName()).isEqualTo("cases");
    }
}
