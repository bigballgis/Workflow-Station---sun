package com.portal.component;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubTableBatchFormRulesTest {
    @Test
    void ignoresOnlyEmptyEventsAndTheExactLegacyDesignerAdapter() {
        String adapter = "$FNX:\nfor(var s=a.getInjectData(n,r),u=arguments.length,c=new Array(u),d=0;d<u;d++)"
                + "c[d]=arguments[d];return s.args=[].concat(c),c.unshift(s),o.apply(this,c)";
        for (String event : List.of("$FNX:", " $FNX:\n ", adapter)) {
            Map<String, Object> form = Map.of("rule", List.of(Map.of("type", "input", "field", "caption",
                    "on", Map.of("change", event))));
            assertThatCode(() -> SubTableBatchFormRules.fields(form)).doesNotThrowAnyException();
        }
        for (String event : List.of("sideEffect()", "not-a-script")) {
            assertThatThrownBy(() -> SubTableBatchFormRules.fields(Map.of("rule", List.of(Map.of(
                    "type", "input", "field", "caption", "on", Map.of("change", event))))))
                    .hasMessageContaining("caption").hasMessageContaining("change");
        }
    }

    @Test
    void ignoresNativeControlTypeWhenValidatingBounds() {
        for (String type : List.of("textarea", "password", "email")) {
            Map<String, Object> form = Map.of("rule", List.of(Map.of("type", "input", "field", "notes",
                    "props", Map.of("type", type, "maxlength", 20))));
            assertThatCode(() -> SubTableBatchFormRules.validateRow(form, Map.of("notes", "synthetic")))
                    .doesNotThrowAnyException();
        }
        Map<String, Object> date = Map.of("rule", List.of(Map.of("type", "datePicker", "field", "when",
                "props", Map.of("type", "datetime"))));
        assertThatCode(() -> SubTableBatchFormRules.validateRow(date, Map.of("when", "2026-11-03 12:00:00")))
                .doesNotThrowAnyException();
    }

    @Test
    void enforcesDeclaredValidatorTypes() {
        Map<String, Object> form = Map.of("rule", List.of(Map.of("type", "input", "field", "notes",
                "validate", List.of(Map.of("type", "string")))));
        assertThatThrownBy(() -> SubTableBatchFormRules.validateRow(form, Map.of("notes", 42)))
                .hasMessageContaining("validator type is invalid");
    }
    @Test
    void preservesZeroAndFalseAsRequiredValues() {
        Map<String, Object> form = Map.of("rule", List.of(
                Map.of("type", "inputNumber", "field", "amount", "required", true),
                Map.of("type", "switch", "field", "active", "required", true)));
        assertThatCode(() -> SubTableBatchFormRules.validateRow(form, Map.of("amount", 0, "active", false)))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsMarkedHooksAndRejectsUnmarkedScriptsAndCustomValidators() {
        for (String slot : List.of("hook", "_hook")) {
            assertThatCode(() -> SubTableBatchFormRules.fields(Map.of("rule", List.of(
                    Map.of("type", "input", "field", "notes", slot, Map.of("value", "$FNX:api.setValue('notes', 'ok')"))))))
                    .doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> SubTableBatchFormRules.fields(Map.of("rule", List.of(
                Map.of("type", "input", "field", "notes", "on", Map.of("change", "sideEffect()"))))))
                .hasMessageContaining("Invalid Batch event script");
        assertThatThrownBy(() -> SubTableBatchFormRules.fields(Map.of("rule", List.of(
                Map.of("type", "input", "field", "notes", "validate", List.of(Map.of("validator", "custom()")))))))
                .hasMessageContaining("validator is unsupported");
    }

    @Test
    void rejectsOutOfBoundsAndInvalidOptions() {
        Map<String, Object> form = Map.of("rule", List.of(
                Map.of("type", "inputNumber", "field", "quantity", "props", Map.of("min", 1, "max", 5)),
                Map.of("type", "select", "field", "category", "options", List.of(Map.of("value", "approved")))));
        assertThatThrownBy(() -> SubTableBatchFormRules.validateRow(form, Map.of("quantity", 6, "category", "approved")))
                .hasMessageContaining("maximum");
        assertThatThrownBy(() -> SubTableBatchFormRules.validateRow(form, Map.of("quantity", 2, "category", "other")))
                .hasMessageContaining("option is invalid");
    }

    @Test
    void acceptsLookupAndChangeButRejectsMalformedLookupValues() {
        Map<String, Object> rule = Map.of("type", "lookup", "field", "person",
                "props", Map.of("lookupConfig", Map.of("tableId", -1000000001)),
                "on", Map.of("change", "$FNX:api.setValue('email', value.email)"));
        Map<String, Object> form = Map.of("rule", List.of(rule));
        assertThatCode(() -> SubTableBatchFormRules.validateRow(form, Map.of("person", Map.of("id", "user-1"))))
                .doesNotThrowAnyException();
        for (Object value : List.of(Map.of("email", "test@example.invalid"), List.of(Map.of("id", "user-1")), "user-1")) {
            assertThatThrownBy(() -> SubTableBatchFormRules.validateRow(form, Map.of("person", value)))
                    .hasMessageContaining("record identity");
        }
    }
}
