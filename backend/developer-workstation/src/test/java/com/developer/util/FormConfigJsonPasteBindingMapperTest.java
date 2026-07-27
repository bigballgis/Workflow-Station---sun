package com.developer.util;

import com.developer.entity.FormTableBinding;
import com.developer.entity.TableDefinition;
import com.developer.enums.BindingType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormConfigJsonPasteBindingMapperTest {

    @Test
    @DisplayName("maps stale SUB binding by field overlap and remaps lookup binding to RELATED")
    void mapsSubAndLookup() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("rule", List.of(
                Map.of("type", "subTable", "_bindingId", 302),
                Map.of("type", "lookup", "field", "u", "props",
                        Map.of("lookupConfig", "{\"bindingId\":88,\"tableId\":9001}"))));
        Map<String, Object> subForms = new LinkedHashMap<>();
        subForms.put("302", Map.of("rule", List.of(
                Map.of("field", "card_number", "type", "input"),
                Map.of("field", "amount", "type", "inputNumber"))));
        config.put("subForms", subForms);

        TableDefinition txn = TableDefinition.builder().id(10L).build();
        FormTableBinding targetSub = binding(50064L, BindingType.SUB, 1, txn);
        FormTableBinding targetRelated = binding(50100L, BindingType.RELATED, 2, null);
        targetRelated.setRelationTableId(42L);

        Map<Long, Set<String>> fields = Map.of(10L, Set.of("card_number", "amount", "arn"));

        var result = FormConfigJsonPasteBindingMapper.buildMapping(
                config, List.of(targetSub, targetRelated), fields);

        assertEquals(50064L, result.bindingIdMapping().get(302L));
        assertEquals(50100L, result.bindingIdMapping().get(88L));
        assertEquals(42L, result.relationTableIdMapping().get(9001L));
        assertTrue(result.unmappedStaleBindingIds().isEmpty());
        assertFalse(result.mixedSource());
    }

    @Test
    @DisplayName("mixed-source: native target ids stay, foreign ids remapped")
    void mixedSourceKeepsNativeIds() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("rule", List.of(
                Map.of("type", "subTable", "_bindingId", 50064),
                Map.of("type", "subTable", "_bindingId", 302)));
        Map<String, Object> subForms = new LinkedHashMap<>();
        subForms.put("302", Map.of("rule", List.of(Map.of("field", "name", "type", "input"))));
        subForms.put("50064", Map.of("rule", List.of(Map.of("field", "other", "type", "input"))));
        config.put("subForms", subForms);

        TableDefinition t1 = TableDefinition.builder().id(1L).build();
        TableDefinition t2 = TableDefinition.builder().id(2L).build();
        FormTableBinding nativeSub = binding(50064L, BindingType.SUB, 1, t1);
        FormTableBinding otherSub = binding(50065L, BindingType.SUB, 2, t2);

        var result = FormConfigJsonPasteBindingMapper.buildMapping(
                config,
                List.of(nativeSub, otherSub),
                Map.of(1L, Set.of("other"), 2L, Set.of("name")));

        assertTrue(result.mixedSource());
        assertFalse(result.bindingIdMapping().containsKey(50064L));
        assertEquals(50065L, result.bindingIdMapping().get(302L));
    }

    private static FormTableBinding binding(Long id, BindingType type, int sort, TableDefinition table) {
        return FormTableBinding.builder()
                .id(id)
                .bindingType(type)
                .sortOrder(sort)
                .table(table)
                .build();
    }
}
