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
        for (String event : List.of(adapter + ";sideEffect()", "$FNX:\nsideEffect()", "$FNX:\nreturn o.apply(this,c)")) {
            assertThatThrownBy(() -> SubTableBatchFormRules.fields(Map.of("rule", List.of(Map.of(
                    "type", "input", "field", "caption", "on", Map.of("change", event))))))
                    .hasMessageContaining("caption").hasMessageContaining("change");
        }
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
    void rejectsUnsupportedScriptsInsteadOfSilentlySkippingValidation() {
        for (String slot : List.of("_on", "hook", "_hook")) {
            assertThatThrownBy(() -> SubTableBatchFormRules.fields(Map.of("rule", List.of(
                    Map.of("type", "input", "field", "notes", slot, Map.of("change", "$FNX:\nsideEffect()"))))))
                    .hasMessageContaining("event scripts").hasMessageContaining("notes");
        }
        assertThatThrownBy(() -> SubTableBatchFormRules.fields(Map.of("rule", List.of(
                Map.of("type", "input", "field", "notes", "on", Map.of("change", "sideEffect()"))))))
                .hasMessageContaining("event scripts");
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
}
