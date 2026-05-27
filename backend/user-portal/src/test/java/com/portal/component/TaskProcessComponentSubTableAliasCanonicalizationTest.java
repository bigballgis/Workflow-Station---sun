package com.portal.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskProcessComponentSubTableAliasCanonicalizationTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> canonicalizeReflect(Map<String, Object> in) throws Exception {
        Method m = TaskProcessComponent.class.getDeclaredMethod(
                "canonicalizeSubTablesAliasKeys", Map.class);
        m.setAccessible(true);
        return (Map<String, Object>) m.invoke(null, in);
    }

    @Test
    void canonicalize_keepsOnlyNumericKeys_andRecursesNestedSubTables() throws Exception {
        Map<String, Object> nestedRowSub = new LinkedHashMap<>();
        nestedRowSub.put("90", List.of(new LinkedHashMap<>(Map.of("id", 1L))));
        nestedRowSub.put("subtable A", List.of(new LinkedHashMap<>(Map.of("id", 1L))));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("__subTables__", nestedRowSub);
        List<Map<String, Object>> rows = List.of(row);

        Map<String, Object> subTables = new LinkedHashMap<>();
        subTables.put("69", rows);
        subTables.put("participants", rows); // alias slice with identical payload

        Map<String, Object> out = canonicalizeReflect(subTables);

        assertThat(out.keySet()).hasSize(1).contains("69");
        List<?> outRows = (List<?>) out.get("69");
        assertThat(outRows).hasSize(1);
        Map<?, ?> outRow = (Map<?, ?>) outRows.get(0);

        Object nestedOutObj = outRow.get("__subTables__");
        assertThat(nestedOutObj).isInstanceOf(Map.class);
        Map<?, ?> nestedOut = (Map<?, ?>) nestedOutObj;
        assertThat(nestedOut.keySet()).hasSize(1);
        assertThat(nestedOut.keySet().iterator().next()).isEqualTo("90");
    }
}

