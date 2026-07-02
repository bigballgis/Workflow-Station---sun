package com.developer.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormConfigJsonOrphanBindingRepairTest {

    @Test
    @DisplayName("remaps stale subForms keys to current SUB binding ids by sort order")
    void remapsStaleSubFormKeys() {
        Map<String, Object> configJson = new LinkedHashMap<>();
        Map<String, Object> subForms = new LinkedHashMap<>();
        subForms.put("302", Map.of("rule", List.of(Map.of("field", "name", "type", "input"))));
        subForms.put("303", Map.of("rule", List.of()));
        configJson.put("subForms", subForms);

        var bindings = List.of(
                binding(50064L, "SUB", 4),
                binding(50105L, "SUB", 4));

        assertTrue(FormConfigJsonOrphanBindingRepair.repairOrphanedBindingKeys(configJson, bindings));

        @SuppressWarnings("unchecked")
        Map<String, Object> repaired = (Map<String, Object>) configJson.get("subForms");
        assertTrue(repaired.containsKey("50064"));
        assertFalse(repaired.containsKey("302"));
        @SuppressWarnings("unchecked")
        Map<String, Object> subtableForm = (Map<String, Object>) repaired.get("50064");
        assertEquals(1, ((List<?>) subtableForm.get("rule")).size());
    }

    @Test
    @DisplayName("remaps stale relationViews keys to current RELATED binding ids")
    void remapsStaleRelationViewKeys() {
        Map<String, Object> configJson = new LinkedHashMap<>();
        Map<String, Object> relationViews = new LinkedHashMap<>();
        relationViews.put("300", Map.of(
                "viewFields", List.of(Map.of("fieldName", "username", "displayName", "Username")),
                "allFields", List.of(Map.of("fieldName", "username", "displayName", "Username"))));
        relationViews.put("301", Map.of("viewFields", List.of()));
        configJson.put("relationViews", relationViews);

        var bindings = List.of(
                binding(50035L, "RELATED", 2),
                binding(50037L, "RELATED", 3));

        assertTrue(FormConfigJsonOrphanBindingRepair.repairOrphanedBindingKeys(configJson, bindings));

        @SuppressWarnings("unchecked")
        Map<String, Object> repaired = (Map<String, Object>) configJson.get("relationViews");
        assertTrue(repaired.containsKey("50035"));
        assertFalse(repaired.containsKey("300"));
    }

    @Test
    void noOpWhenKeysMatch() {
        Map<String, Object> configJson = new LinkedHashMap<>();
        configJson.put("subForms", new LinkedHashMap<>(Map.of(
                "50064", Map.of("rule", List.of(Map.of("field", "name", "type", "input"))))));

        var bindings = List.of(binding(50064L, "SUB", 4));

        assertFalse(FormConfigJsonOrphanBindingRepair.repairOrphanedBindingKeys(configJson, bindings));
    }

    private static com.developer.entity.FormTableBinding binding(long id, String type, int sortOrder) {
        return com.developer.entity.FormTableBinding.builder()
                .id(id)
                .bindingType(com.developer.enums.BindingType.valueOf(type))
                .sortOrder(sortOrder)
                .build();
    }
}
