package com.portal.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubTableNestingSanitizerTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> row(Object id, Map<String, Object> nested) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", id);
        if (nested != null) {
            r.put("__subTables__", nested);
        }
        return r;
    }

    private static Map<String, Object> subTables(String slice, List<Map<String, Object>> rows) {
        Map<String, Object> st = new HashMap<>();
        st.put(slice, new ArrayList<>(rows));
        return st;
    }

    @Test
    void nullOrEmptyIsNoOp() {
        assertEquals(0, SubTableNestingSanitizer.stripDeepNestedSubTables(null));
        assertEquals(0, SubTableNestingSanitizer.stripDeepNestedSubTables(new HashMap<>()));
        Map<String, Object> noSub = new HashMap<>();
        noSub.put("field", "value");
        assertEquals(0, SubTableNestingSanitizer.stripDeepNestedSubTables(noSub));
    }

    @Test
    void keepsOneLevelNesting() {
        // root -> slice "66" -> row(id=1) -> __subTables__ "10" -> row(id=2)  [no deeper nesting]
        Map<String, Object> nestedLevel1 = subTables("10", List.of(row(2, null)));
        Map<String, Object> top = subTables("66", List.of(row(1, nestedLevel1)));
        Map<String, Object> variables = new HashMap<>();
        variables.put("__subTables__", top);

        int removed = SubTableNestingSanitizer.stripDeepNestedSubTables(variables);

        assertEquals(0, removed, "single-level nesting must be preserved");
        Object keptNested = firstRow(topSubTables(variables), "66").get("__subTables__");
        assertTrue(keptNested instanceof Map, "depth-1 nesting should remain");
    }

    @Test
    @SuppressWarnings("unchecked")
    void stripsDepthTwoAndBelow() {
        // root -> "66" -> row(1) -> "10" -> row(2) -> "20" -> row(3)
        Map<String, Object> level2 = subTables("20", List.of(row(3, null)));
        Map<String, Object> level1 = subTables("10", List.of(row(2, level2)));
        Map<String, Object> top = subTables("66", List.of(row(1, level1)));
        Map<String, Object> variables = new HashMap<>();
        variables.put("__subTables__", top);

        int removed = SubTableNestingSanitizer.stripDeepNestedSubTables(variables);

        assertEquals(1, removed, "the depth-2 row's __subTables__ should be removed");
        Map<String, Object> depth0Row = firstRow(topSubTables(variables), "66");
        Map<String, Object> depth1Row = firstRow((Map<String, Object>) depth0Row.get("__subTables__"), "10");
        assertFalse(depth1Row.containsKey("__subTables__"), "depth-1 rows must not carry nested __subTables__");
        // depth-1 nesting itself is preserved on the depth-0 row
        assertTrue(depth0Row.get("__subTables__") instanceof Map);
    }

    @Test
    void geometricBloatCollapsesToOneLevel() {
        // Each row carries a full nested copy 4 levels deep -> after strip, max one row-level nesting remains.
        Map<String, Object> deepest = subTables("d", List.of(row(4, null)));
        Map<String, Object> l3 = subTables("c", List.of(row(3, deepest)));
        Map<String, Object> l2 = subTables("b", List.of(row(2, l3)));
        Map<String, Object> l1 = subTables("a", List.of(row(1, l2)));
        Map<String, Object> variables = new HashMap<>();
        variables.put("__subTables__", l1);

        int removed = SubTableNestingSanitizer.stripDeepNestedSubTables(variables);

        assertTrue(removed >= 1);
        assertEquals(1, nestingDepth(variables.get("__subTables__")),
                "after sanitize, deepest row-level nesting must be exactly one level");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> topSubTables(Map<String, Object> variables) {
        return (Map<String, Object>) variables.get("__subTables__");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstRow(Map<String, Object> subTables, String slice) {
        List<Object> rows = (List<Object>) subTables.get(slice);
        return (Map<String, Object>) rows.get(0);
    }

    /** Consecutive row-level nesting depth under a __subTables__ map (0 = no row carries nested __subTables__). */
    @SuppressWarnings("unchecked")
    private static int nestingDepth(Object subTables) {
        if (!(subTables instanceof Map<?, ?> map)) {
            return 0;
        }
        int best = 0;
        for (Object sliceVal : map.values()) {
            if (!(sliceVal instanceof List<?> rows)) {
                continue;
            }
            for (Object rowObj : rows) {
                if (!(rowObj instanceof Map<?, ?> row)) {
                    continue;
                }
                Object nested = ((Map<String, Object>) row).get("__subTables__");
                if (nested instanceof Map) {
                    best = Math.max(best, 1 + nestingDepth(nested));
                }
            }
        }
        return best;
    }
}
