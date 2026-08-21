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

    @Test
    void remapIds_remapsPortalViewsFormSourceRefs() {
        Map<String, Object> formSource = new LinkedHashMap<>();
        formSource.put("type", "linkForm");
        formSource.put("formId", 11);
        formSource.put("linkFormColumnId", -101);
        Map<String, Object> portalViewsEntry = new LinkedHashMap<>();
        portalViewsEntry.put("assigneeTodo", "formBelowTable");
        portalViewsEntry.put("assigneeTodoFormSource", formSource);

        Map<String, Object> portalViews = new LinkedHashMap<>();
        portalViews.put("101", portalViewsEntry);

        Map<String, Object> configJson = new HashMap<>();
        configJson.put("subTablePortalViews", portalViews);

        FormConfigJsonBindingIdRewriter.remapIds(configJson,
                Map.of(101L, 501L), Map.of(11L, 91L), Map.of(), Map.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> entry = (Map<String, Object>) ((Map<?, ?>) configJson.get("subTablePortalViews")).get("501");
        @SuppressWarnings("unchecked")
        Map<String, Object> fs = (Map<String, Object>) entry.get("assigneeTodoFormSource");
        assertEquals(91L, ((Number) fs.get("formId")).longValue());
        assertEquals(-501L, ((Number) fs.get("linkFormColumnId")).longValue());
    }

    @Test
    void remapIds_remapsPositiveLinkFormComponentIds() {
        Map<String, Object> column = new LinkedHashMap<>();
        column.put("columnType", "linkForm");
        column.put("componentId", 7);
        column.put("linkFormColumnId", 7);
        column.put("linkedFormId", 11);

        Map<String, Object> subListViews = new LinkedHashMap<>();
        subListViews.put("101", Map.of("columns", List.of(column)));

        Map<String, Object> configJson = new HashMap<>();
        configJson.put("subListViews", subListViews);

        FormConfigJsonBindingIdRewriter.remapIds(configJson,
                Map.of(101L, 501L), Map.of(11L, 91L), Map.of(7L, 70L), Map.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> entry = (Map<String, Object>) ((Map<?, ?>) configJson.get("subListViews")).get("501");
        @SuppressWarnings("unchecked")
        Map<String, Object> col = (Map<String, Object>) ((List<?>) entry.get("columns")).get(0);
        assertEquals(70L, ((Number) col.get("componentId")).longValue());
        assertEquals(70L, ((Number) col.get("linkFormColumnId")).longValue());
        assertEquals(91L, ((Number) col.get("linkedFormId")).longValue());
    }

    @Test
    void remapIds_remapsPortalViewsOnSubTableRuleNode() {
        Map<String, Object> formSource = new LinkedHashMap<>();
        formSource.put("type", "subForm");
        formSource.put("linkFormColumnId", 7);
        Map<String, Object> portalViews = new LinkedHashMap<>();
        portalViews.put("assigneeTodoFormSource", formSource);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("portalViews", portalViews);
        Map<String, Object> subTableNode = new LinkedHashMap<>();
        subTableNode.put("type", "subTable");
        subTableNode.put("_bindingId", 101);
        subTableNode.put("props", props);

        Map<String, Object> configJson = new HashMap<>();
        configJson.put("rule", new ArrayList<>(List.of(subTableNode)));

        FormConfigJsonBindingIdRewriter.remapIds(configJson,
                Map.of(101L, 501L), Map.of(), Map.of(7L, 70L), Map.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) ((List<?>) configJson.get("rule")).get(0);
        assertEquals(501L, ((Number) node.get("_bindingId")).longValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> fs = (Map<String, Object>) ((Map<?, ?>) ((Map<?, ?>) node.get("props")).get("portalViews"))
                .get("assigneeTodoFormSource");
        assertEquals(70L, ((Number) fs.get("linkFormColumnId")).longValue());
    }

    @Test
    void remapIds_remapsLookupConfigRelationTableIdButKeepsVirtualIds() {
        Map<String, Object> propsReal = new LinkedHashMap<>();
        propsReal.put("lookupConfig", "{\"bindingId\":35,\"tableId\":1,\"tableName\":\"test\"}");
        Map<String, Object> lookupReal = new LinkedHashMap<>(Map.of("type", "lookup", "props", propsReal));

        Map<String, Object> propsVirtual = new LinkedHashMap<>();
        propsVirtual.put("lookupConfig", "{\"bindingId\":36,\"tableId\":-1000000001,\"tableName\":\"sys_users\"}");
        Map<String, Object> lookupVirtual = new LinkedHashMap<>(Map.of("type", "lookup", "props", propsVirtual));

        Map<String, Object> configJson = new HashMap<>();
        configJson.put("rule", new ArrayList<>(List.of(lookupReal, lookupVirtual)));

        FormConfigJsonBindingIdRewriter.remapIds(configJson,
                Map.of(35L, 335L, 36L, 336L), Map.of(), Map.of(),
                Map.of(1L, 21L, -1000000001L, 999L));

        @SuppressWarnings("unchecked")
        Map<String, Object> real = (Map<String, Object>) ((List<?>) configJson.get("rule")).get(0);
        String realCfg = (String) ((Map<?, ?>) real.get("props")).get("lookupConfig");
        assertTrue(realCfg.contains("\"tableId\":21"), realCfg);
        assertTrue(realCfg.contains("\"bindingId\":335"), realCfg);

        @SuppressWarnings("unchecked")
        Map<String, Object> virtual = (Map<String, Object>) ((List<?>) configJson.get("rule")).get(1);
        String virtualCfg = (String) ((Map<?, ?>) virtual.get("props")).get("lookupConfig");
        assertTrue(virtualCfg.contains("\"tableId\":-1000000001"), virtualCfg);
        assertTrue(virtualCfg.contains("\"bindingId\":336"), virtualCfg);
    }

    /**
     * The Inline Form widget points at a SUB binding via the same {@code _bindingId} key as
     * {@code subTable}. Without it in the type gate, an imported / cloned / rolled-back FU keeps
     * the SOURCE environment's bindingId — the widget then binds to an unrelated table or shows
     * "stale", with no error anywhere.
     */
    @Test
    void remapBindingIds_remapsInlineSubFormBindingIdAtTopLevelAndInProps() {
        Map<String, Object> configJson = new HashMap<>();
        List<Map<String, Object>> rule = new ArrayList<>();
        rule.add(new LinkedHashMap<>(Map.of(
                "type", "inlineSubForm",
                "_bindingId", 101,
                "title", "Inline Form",
                "props", new LinkedHashMap<>(Map.of("_bindingId", 101))
        )));
        configJson.put("rule", rule);

        FormConfigJsonBindingIdRewriter.remapBindingIds(configJson, Map.of(101L, 501L));

        @SuppressWarnings("unchecked")
        Map<String, Object> inline = (Map<String, Object>) ((List<?>) configJson.get("rule")).get(0);
        assertEquals(501L, ((Number) inline.get("_bindingId")).longValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) inline.get("props");
        assertEquals(501L, ((Number) props.get("_bindingId")).longValue());
    }

    @Test
    void remapBindingIds_remapsInlineSubFormNestedInChildren() {
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("type", "inlineSubForm");
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
}
