package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ComputedFieldBatchValidationTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Map<String, Object>> fields = List.of(
            Map.of("fieldName", "copied_reference", "dataType", "VARCHAR", "isComputed", true, "nullable", true),
            Map.of("fieldName", "caption", "dataType", "VARCHAR", "nullable", true));

    @Test void validatesServerRecomputedLegacyTextWithoutChangingPayloadOrUnrelatedColumns() {
        Map<String, Object> submitted = Map.of("__subTables__", Map.of("dw:lines", List.of(
                Map.of("line_key", "R1", "copied_reference", new BigDecimal("1234"), "caption", "updated"))));
        Map<String, Object> row = first(submitted);
        assertThatThrownBy(() -> SubTableBatchEditedRowGuard.validateMetadata(fields, row)).hasMessageContaining("invalid text type");
        Map<String, Object> checked = ComputedFieldBatchValidation.prepare(variables(), Map.of("sourceId", "form-1"), submitted, mapper,
                combined -> {
                    assertThat(((Map<?, ?>) combined.get("__subTables__")).containsKey("dw:untouched")).isTrue();
                    first(combined).put("copied_reference", combined.get("source_text"));
                });
        assertThatCode(() -> SubTableBatchEditedRowGuard.validateMetadata(fields, first(checked))).doesNotThrowAnyException();
        assertThat(first(checked)).containsEntry("copied_reference", "001234").containsEntry("caption", "updated");
        assertThat(first(submitted).get("copied_reference")).isInstanceOf(BigDecimal.class);
    }

    @Test void neverCoercesMalformedOrdinaryTextOrUsesARepairToBypassValidation() {
        Map<String, Object> submitted = Map.of("__subTables__", Map.of("dw:lines", List.of(
                Map.of("line_key", "R1", "copied_reference", 1234, "caption", 99))));
        Map<String, Object> checked = ComputedFieldBatchValidation.prepare(variables(), Map.of("sourceId", "form-1"), submitted, mapper,
                combined -> first(combined).put("copied_reference", combined.get("source_text")));
        assertThatThrownBy(() -> SubTableBatchEditedRowGuard.validateMetadata(fields, first(checked))).hasMessageContaining("caption");
    }

    @Test void avoidsRecalculationWhenNoLegacyComputedTextIsSubmitted() {
        Map<String, Object> submitted = Map.of("__subTables__", Map.of("dw:lines", List.of(
                Map.of("line_key", "R1", "copied_reference", "001234", "caption", "text"))));
        assertThat(ComputedFieldBatchValidation.prepare(variables(), Map.of("sourceId", "form-1"), submitted, mapper,
                combined -> { throw new AssertionError("unnecessary recalculation"); })).isSameAs(submitted);
    }

    private Map<String, Object> variables() {
        Map<String, Object> control = Map.of("batchControlId", "grid", "rowDesign", Map.of("fields", fields,
                "binding", Map.of("tableName", "lines", "primaryKeyFields", List.of("line_key"))));
        return Map.of("source_text", "001234", "__subTables__", Map.of("dw:untouched", List.of(Map.of("other_key", "other"))), SubTableWriteIsolation.BATCH_RUNTIME_FIELD,
                Map.of("schemaVersion", 1, "forms", Map.of("form-1", Map.of("controls", List.of(control)))));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> first(Map<String, Object> data) {
        return (Map<String, Object>) ((List<?>) ((Map<?, ?>) data.get("__subTables__")).get("dw:lines")).get(0);
    }
}
