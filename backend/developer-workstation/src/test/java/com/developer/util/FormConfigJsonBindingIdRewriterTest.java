package com.developer.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FormConfigJsonBindingIdRewriterTest {

    @Test
    void remapBindingIds_remapsSubTableBindingIdInMainRule() {
        Map<String, Object> configJson = new HashMap<>();
        List<Map<String, Object>> rule = new ArrayList<>();
        rule.add(new LinkedHashMap<>(Map.of(
                "type", "subTable",
                "_bindingId", 101,
                "title", "Sub Table",
                "props", new LinkedHashMap<>(Map.of("_bindingId", 101))
        )));
        configJson.put("rule", rule);

        FormConfigJsonBindingIdRewriter.remapBindingIds(configJson, Map.of(101L, 501L));

        @SuppressWarnings("unchecked")
        Map<String, Object> subTable = (Map<String, Object>) ((List<?>) configJson.get("rule")).get(0);
        assertEquals(501L, ((Number) subTable.get("_bindingId")).longValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) subTable.get("props");
        assertEquals(501L, ((Number) props.get("_bindingId")).longValue());
    }

    @Test
    void remapBindingIds_remapsNestedSubTableInChildren() {
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("type", "subTable");
        child.put("_bindingId", 102);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "fcRow");
        row.put("children", List.of(child));

        Map<String, Object> configJson = new HashMap<>();
        configJson.put("rule", new ArrayList<>(List.of(row)));

        FormConfigJsonBindingIdRewriter.remapBindingIds(configJson, Map.of(102L, 502L));

        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) ((List<?>) ((Map<?, ?>) ((List<?>) configJson.get("rule")).get(0))
                .get("children")).get(0);
        assertEquals(502L, ((Number) nested.get("_bindingId")).longValue());
    }

    @Test
    void remapBindingIds_remapsSubFormKeysAndNestedRule() {
        List<Map<String, Object>> subFormRule = new ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of("type", "subTable", "_bindingId", 101))
        ));
        Map<String, Object> subForms = new LinkedHashMap<>();
        subForms.put("101", Map.of("rule", subFormRule));

        Map<String, Object> configJson = new HashMap<>();
        configJson.put("subForms", subForms);

        FormConfigJsonBindingIdRewriter.remapBindingIds(configJson, Map.of(101L, 501L));

        @SuppressWarnings("unchecked")
        Map<String, Object> remappedSubForms = (Map<String, Object>) configJson.get("subForms");
        assertTrue(remappedSubForms.containsKey("501"));
        assertFalse(remappedSubForms.containsKey("101"));

        @SuppressWarnings("unchecked")
        Map<String, Object> subForm = (Map<String, Object>) remappedSubForms.get("501");
        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) ((List<?>) subForm.get("rule")).get(0);
        assertEquals(501L, ((Number) node.get("_bindingId")).longValue());
    }

    @Test
    void remapBindingIds_remapsLookupConfigBindingIdOnLookupNode() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("lookupConfig",
                "{\"bindingId\":35,\"tableId\":1,\"tableName\":\"test\",\"searchFields\":[\"id\"]}");
        Map<String, Object> lookupNode = new LinkedHashMap<>();
        lookupNode.put("type", "lookup");
        lookupNode.put("field", "lookup");
        lookupNode.put("props", props);

        Map<String, Object> configJson = new HashMap<>();
        configJson.put("rule", new ArrayList<>(List.of(lookupNode)));

        FormConfigJsonBindingIdRewriter.remapBindingIds(configJson, Map.of(35L, 333L));

        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) ((List<?>) configJson.get("rule")).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> remappedProps = (Map<String, Object>) node.get("props");
        String cfg = (String) remappedProps.get("lookupConfig");
        assertTrue(cfg.contains("\"bindingId\":333"), cfg);
        assertFalse(cfg.contains("\"bindingId\":35"), cfg);
        // unrelated fields untouched
        assertTrue(cfg.contains("\"tableId\":1"));
        assertTrue(cfg.contains("\"tableName\":\"test\""));
    }

    @Test
    void remapBindingIds_remapsLookupConfigBindingIdInSubListViewColumn() {
        Map<String, Object> column = new LinkedHashMap<>();
        column.put("columnType", "lookup");
        column.put("lookupConfig",
                "{\"bindingId\":60,\"tableId\":-1000000001,\"tableName\":\"sys_users\"}");

        Map<String, Object> subListViews = new LinkedHashMap<>();
        subListViews.put("101", Map.of("columns", List.of(column)));

        Map<String, Object> configJson = new HashMap<>();
        configJson.put("subListViews", subListViews);

        FormConfigJsonBindingIdRewriter.remapBindingIds(configJson, Map.of(60L, 334L));

        @SuppressWarnings("unchecked")
        Map<String, Object> entry = (Map<String, Object>) ((Map<?, ?>) configJson.get("subListViews")).get("101");
        @SuppressWarnings("unchecked")
        Map<String, Object> col = (Map<String, Object>) ((List<?>) entry.get("columns")).get(0);
        String cfg = (String) col.get("lookupConfig");
        assertTrue(cfg.contains("\"bindingId\":334"), cfg);
        assertFalse(cfg.contains("\"bindingId\":60"), cfg);
    }

    @Test
    void remapBindingIds_leavesUnmappedLookupConfigUntouched() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("lookupConfig", "{\"bindingId\":999,\"tableId\":1}");
        Map<String, Object> lookupNode = new LinkedHashMap<>();
        lookupNode.put("type", "lookup");
        lookupNode.put("props", props);

        Map<String, Object> configJson = new HashMap<>();
        configJson.put("rule", new ArrayList<>(List.of(lookupNode)));

        FormConfigJsonBindingIdRewriter.remapBindingIds(configJson, Map.of(35L, 333L));

        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) ((List<?>) configJson.get("rule")).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> remappedProps = (Map<String, Object>) node.get("props");
        assertEquals("{\"bindingId\":999,\"tableId\":1}", remappedProps.get("lookupConfig"));
    }

    @Test
    void remapBindingIds_remapsSubListViewLinkColumnRefs() {
        Map<String, Object> column = new LinkedHashMap<>();
        column.put("columnType", "linkForm");
        column.put("componentId", -101);
        column.put("boundSubTableBindingId", 102);

        Map<String, Object> subListViews = new LinkedHashMap<>();
        subListViews.put("101", Map.of("columns", List.of(column)));

        Map<String, Object> configJson = new HashMap<>();
        configJson.put("subListViews", subListViews);

        FormConfigJsonBindingIdRewriter.remapBindingIds(configJson,
                Map.of(101L, 501L, 102L, 502L));

        @SuppressWarnings("unchecked")
        Map<String, Object> entry = (Map<String, Object>) ((Map<?, ?>) configJson.get("subListViews")).get("501");
        @SuppressWarnings("unchecked")
        Map<String, Object> col = (Map<String, Object>) ((List<?>) entry.get("columns")).get(0);
        assertEquals(-501L, ((Number) col.get("componentId")).longValue());
        assertEquals(502L, ((Number) col.get("boundSubTableBindingId")).longValue());
    }
}
