package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/** Initializes row versions only when a new instance captures membership capabilities. */
final class SubTableBatchInitialRows {
    private SubTableBatchInitialRows() { }

    static void initialize(Map<String, Object> variables, Map<String, Object> snapshot, ObjectMapper mapper) {
        if (!(variables.get("__subTables__") instanceof Map<?, ?> source)) return;
        Map<String, Object> tables = mapper.convertValue(source, new TypeReference<Map<String, Object>>() { });
        Map<?, ?> forms = map(snapshot.get("forms"));
        for (Object rawForm : forms.values()) {
            Object rawControls = map(rawForm).get("controls");
            if (!(rawControls instanceof List<?> controls)) continue;
            for (Object raw : controls) initializeControl(tables, map(raw), mapper);
        }
        variables.put("__subTables__", tables);
    }

    private static void initializeControl(Map<String, Object> tables, Map<?, ?> control, ObjectMapper mapper) {
        Map<?, ?> binding = map(map(control.get("rowDesign")).get("binding"));
        Object name = binding.get("tableName");
        if (!(name instanceof String tableName)) return; // Edit/Assign-only snapshots do not carry membership metadata.
        String key = "dw:" + tableName.toLowerCase(java.util.Locale.ROOT);
        Object value = tables.get(key);
        if (!(value instanceof List<?>)) return; // No rows in this binding at process creation.
        List<Map<String, Object>> rows = mapper.convertValue(value, new TypeReference<List<Map<String, Object>>>() { });
        rows.forEach(row -> row.put("_wsRowVersion", 1));
        tables.put(key, rows);
    }

    private static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> map ? map : Map.of(); }
}
