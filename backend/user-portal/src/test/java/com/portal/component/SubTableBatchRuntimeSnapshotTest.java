package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubTableBatchRuntimeSnapshotTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProcessStartComponent.ActiveCatalogPin pin =
            new ProcessStartComponent.ActiveCatalogPin("catalog-1", "expense", "2026.10");

    @Test
    void returnsEmptyWhenPublishedCatalogHasNoEnabledBatchControl() {
        assertThat(SubTableBatchRuntimeSnapshot.capture(content(config(false, "ctl-1")), pin, objectMapper))
                .isEmpty();
    }

    @Test
    void ignoresLegacyNonBatchFormWithoutSourceId() {
        Map<String, Object> legacy = config(false, null);
        String data;
        try {
            data = objectMapper.writeValueAsString(legacy);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }

        assertThat(SubTableBatchRuntimeSnapshot.capture(
                Map.of("forms", List.of(Map.of("data", data))), pin, objectMapper)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void capturesOnlyStrictlyEnabledControlsAndTheirBatchDefinition() {
        Map<String, Object> snapshot = SubTableBatchRuntimeSnapshot.capture(content(config(true, "ctl-1")), pin, objectMapper)
                .orElseThrow();

        assertThat(snapshot).containsEntry("schemaVersion", 1)
                .containsEntry("catalogId", "catalog-1")
                .containsEntry("functionUnitCode", "expense");
        Map<String, Object> form = (Map<String, Object>) ((Map<String, Object>) snapshot.get("forms")).get("form-1");
        Map<String, Object> control = (Map<String, Object>) ((List<?>) form.get("controls")).get(0);
        assertThat(control).containsEntry("batchControlId", "ctl-1").containsEntry("bindingId", "101");
        assertThat((Map<String, Object>) control.get("capabilities"))
                .containsEntry("allowBatchEdit", true).containsEntry("allowBatchAdd", false);
    }

    @Test
    void rejectsEnabledControlWithoutStableIdentityOrBatchForm() {
        assertThatThrownBy(() -> SubTableBatchRuntimeSnapshot.capture(content(config(true, null)), pin, objectMapper))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("batchControlId");

        Map<String, Object> badConfig = config(true, "ctl-1");
        badConfig.remove("batchForms");
        assertThatThrownBy(() -> SubTableBatchRuntimeSnapshot.capture(content(badConfig), pin, objectMapper))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("has no batchForms");
    }

    @Test
    void rejectsDuplicateEnabledControlIdentityAcrossForms() {
        assertThatThrownBy(() -> SubTableBatchRuntimeSnapshot.capture(
                Map.of("forms", List.of(form("form-1", config(true, "duplicate")), form("form-2", config(true, "duplicate")))),
                pin, objectMapper))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate batchControlId");
    }

    private Map<String, Object> content(Map<String, Object> config) {
        return Map.of("forms", List.of(form("form-1", config)));
    }

    private Map<String, Object> form(String sourceId, Map<String, Object> config) {
        try {
            return Map.of("sourceId", sourceId, "data", objectMapper.writeValueAsString(config), "formType", "TASK");
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Map<String, Object> config(boolean enabled, String controlId) {
        Map<String, Object> rule = new java.util.LinkedHashMap<>();
        rule.put("type", "subTable");
        rule.put("batchControlId", controlId);
        rule.put("props", Map.of("_bindingId", 101, "allowBatchEdit", enabled, "allowBatchAdd", "true"));
        return new java.util.LinkedHashMap<>(Map.of(
                "rule", List.of(rule),
                "batchForms", Map.of("101", Map.of("schemaVersion", 1, "rule", List.of(Map.of("type", "input", "field", "name"))))));
    }
}
