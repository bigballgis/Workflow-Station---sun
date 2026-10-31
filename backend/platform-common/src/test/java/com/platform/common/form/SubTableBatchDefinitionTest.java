package com.platform.common.form;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubTableBatchDefinitionTest {
    @Test
    void rejectsUnsupportedBatchControlsAndHooksBeforePublicationOrImport() {
        assertThatThrownBy(() -> SubTableBatchDefinition.validateForm(Map.of("schemaVersion", 1,
                "rule", List.of(Map.of("type", "upload", "field", "documents"))), false))
                .hasMessageContaining("documents");
        assertThatThrownBy(() -> SubTableBatchDefinition.validateForm(Map.of("schemaVersion", 1,
                "rule", List.of(Map.of("type", "input", "field", "email", "hook", Map.of("mounted", "$FNX:bad()")))), false))
                .hasMessageContaining("email").hasMessageContaining("mounted");
        assertThatCode(() -> SubTableBatchDefinition.validateForm(Map.of("schemaVersion", 1,
                "rule", List.of(Map.of("type", "lookup", "field", "person", "on", Map.of("change", "$FNX:api.setValue('email', value.email)")))), false))
                .doesNotThrowAnyException();
    }
    @Test
    void acceptsDesignerRootBindingAndRejectsConflictingBindingSlots() {
        Map<String, Object> form = Map.of("schemaVersion", 1, "rule", List.of(Map.of("type", "input", "field", "value")));
        Map<String, Object> designer = Map.of("type", "subTable", "title", "New lines", "_bindingId", 101,
                "batchControlId", "designer-grid", "props", Map.of("allowBatchEdit", true));
        assertThatCode(() -> SubTableBatchDefinition.validate(Map.of("rule", List.of(designer), "batchForms", Map.of("101", form))))
                .doesNotThrowAnyException();
        Map<String, Object> conflict = new java.util.HashMap<>(designer);
        conflict.put("props", Map.of("allowBatchEdit", true, "_bindingId", 102));
        assertThatThrownBy(() -> SubTableBatchDefinition.validate(Map.of("rule", List.of(conflict), "batchForms", Map.of("101", form))))
                .hasMessageContaining("New lines").hasMessageContaining("conflicting");
    }

    @Test
    void legacyAndStrictBooleanDisabledFormsRemainUnchanged() {
        assertThatCode(() -> SubTableBatchDefinition.validate(Map.of("rule", List.of()))).doesNotThrowAnyException();
        assertThatCode(() -> SubTableBatchDefinition.validate(Map.of("batchForms", Map.of("101",
                Map.of("schemaVersion", 1, "rule", List.of()))))).doesNotThrowAnyException();
        assertThatCode(() -> SubTableBatchDefinition.validate(Map.of("rule", List.of(
                Map.of("type", "subTable", "props", Map.of("allowBatchEdit", "true")))))).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownFractionalAndStringVersionsEvenForDormantDefinitions() {
        for (Object version : List.of(2, 1.5, "1")) {
            assertThatThrownBy(() -> SubTableBatchDefinition.validate(Map.of("batchForms", Map.of("101",
                    Map.of("schemaVersion", version, "rule", List.of(Map.of("type", "input", "field", "value")))))))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("schemaVersion");
        }
    }

    @Test
    void enabledControlsRequireUniqueIdentityAnIntegralBindingAndTheirOwnDefinition() {
        Map<String, Object> form = Map.of("schemaVersion", 1, "rule", List.of(Map.of("type", "input", "field", "value")));
        Map<String, Object> control = control("grid", 101);
        assertThatCode(() -> SubTableBatchDefinition.validate(Map.of("rule", List.of(control), "batchForms", Map.of("101", form))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> SubTableBatchDefinition.validate(Map.of("rule", List.of(control), "batchForms", Map.of())))
                .hasMessageContaining("batchForms");
        assertThatThrownBy(() -> SubTableBatchDefinition.validate(Map.of("rule", List.of(control, control), "batchForms", Map.of("101", form))))
                .hasMessageContaining("Duplicate batchControlId");
        assertThatThrownBy(() -> SubTableBatchDefinition.validate(Map.of("rule", List.of(control("grid", 101.5)), "batchForms", Map.of("101", form))))
                .hasMessageContaining("binding ID");
    }

    @Test
    void assignmentNeedsItsDedicatedControlAndOptionsMustBeStructured() {
        assertThatThrownBy(() -> SubTableBatchDefinition.validateForm(Map.of("schemaVersion", 1,
                "rule", List.of(Map.of("type", "input", "field", "value"))), true)).hasMessageContaining("miAssignment");
        assertThatThrownBy(() -> SubTableBatchDefinition.validateForm(Map.of("schemaVersion", 1,
                "rule", List.of(Map.of("type", "miAssignment")), "options", "script"), true)).hasMessageContaining("options");
        assertThatCode(() -> SubTableBatchDefinition.validateForm(Map.of("schemaVersion", 1,
                "rule", List.of(Map.of("type", "miAssignment")), "options", Map.of()), true)).doesNotThrowAnyException();
    }

    private static Map<String, Object> control(String id, Object binding) {
        return Map.of("type", "subTable", "batchControlId", id,
                "props", Map.of("_bindingId", binding, "allowBatchEdit", true));
    }
}
