package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.dto.SubTableBatchChange;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubTableBatchCapacityTest {
    @Test
    void acceptsExactlyTwoHundredAffectedRows() {
        assertThatCode(() -> validate(List.of(edit(0, 200)))).doesNotThrowAnyException();
    }

    @Test
    void rejectsTwoHundredAndOneRowsInOneDeclaration() {
        assertThatThrownBy(() -> validate(List.of(edit(0, 201))))
                .isInstanceOf(com.platform.common.exception.BusinessException.class).hasMessageContaining("200 row patches");
    }

    @Test
    void aggregatesUniqueRowsAcrossSeparateDeclarations() {
        assertThatThrownBy(() -> validate(List.of(edit(0, 100), edit(100, 101))))
                .isInstanceOf(com.platform.common.exception.BusinessException.class).hasMessageContaining("200 affected rows");
    }

    @Test
    void overlappingDeclarationsDoNotCountTheSameBindingRowTwice() {
        assertThatCode(() -> validate(List.of(edit(0, 100), edit(0, 100)))).doesNotThrowAnyException();
    }

    private static SubTableBatchChange edit(int start, int count) {
        var change = new SubTableBatchChange();
        change.setBatchControlId("grid"); change.setBindingId("101"); change.setOperation("EDIT");
        change.setRowPatches(IntStream.range(start, start + count).mapToObj(index -> {
            var patch = new SubTableBatchChange.RowPatch();
            patch.setRowId("ROW-" + index); patch.setRowKey(Map.of("token", "ROW-" + index));
            patch.setFields(List.of("quantity")); return patch;
        }).toList());
        return change;
    }

    private static void validate(List<SubTableBatchChange> changes) {
        var control = Map.of("batchControlId", "grid", "bindingId", "101", "capabilities", Map.of("allowBatchEdit", true),
                "originalCrud", Map.of("allowEdit", true),
                "batchForm", Map.of("schemaVersion", 1, "rule", List.of(Map.of("type", "inputNumber", "field", "quantity"))),
                "rowDesign", Map.of("fields", List.of(Map.of("fieldName", "quantity")), "fieldWriteModes", Map.of("quantity", "EDITABLE")));
        var snapshot = Map.of("schemaVersion", 1, "forms", Map.of("form", Map.of("controls", List.of(control))));
        SubTableBatchChangeGuard.validate(Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot),
                Map.of("sourceId", "form"), changes, new ObjectMapper());
    }
}
