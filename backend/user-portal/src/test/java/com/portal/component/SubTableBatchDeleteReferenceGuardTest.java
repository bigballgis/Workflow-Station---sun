package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.dto.SubTableBatchChange;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubTableBatchDeleteReferenceGuardTest {
    @Test
    void refusesDeletingAParentWithConfiguredChildReferences() {
        assertThatThrownBy(() -> validate(Map.of("case_key", "C1"), List.of("case_key"), "C1"))
                .hasMessageContaining("related child rows");
    }

    @Test
    void permitsDeletingAnUnreferencedParentWithoutTouchingSiblingChildren() {
        assertThatCode(() -> validate(Map.of("case_key", "C1"), List.of("case_key"), "C2"))
                .doesNotThrowAnyException();
    }

    @Test
    void usesConfiguredCompositeReferenceEncodingNotConventionalColumnNames() {
        assertThatThrownBy(() -> validate(Map.of("region", "R1", "sequence", "7"),
                List.of("sequence", "region"), "region=R1\u001fsequence=7"))
                .hasMessageContaining("related child rows");
    }

    private void validate(Map<String, Object> key, List<String> fields, String childReference) {
        Map<String, Object> dependency = Map.of("storeKey", "dw:attachments", "fieldName", "owner_ref", "refPrimaryKeyFields", fields);
        Map<String, Object> snapshot = Map.of("schemaVersion", 1, "forms", Map.of("form-1", Map.of("controls",
                List.of(Map.of("batchControlId", "grid", "rowDesign", Map.of("dependencies", List.of(dependency)))))));
        SubTableBatchChange.RowPatch patch = new SubTableBatchChange.RowPatch();
        patch.setRowKey(key);
        SubTableBatchChange change = new SubTableBatchChange();
        change.setOperation("DELETE");
        change.setBatchControlId("grid");
        change.setRowPatches(List.of(patch));
        SubTableBatchDeleteReferenceGuard.validate(Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot,
                "__subTables__", Map.of("dw:attachments", List.of(Map.of("owner_ref", childReference)))),
                Map.of("sourceId", "form-1"), List.of(change), new ObjectMapper());
    }
}
