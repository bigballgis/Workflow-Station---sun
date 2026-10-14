package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** Initializes row versions only when a new instance captures membership capabilities. */
final class SubTableBatchInitialRows {
    private SubTableBatchInitialRows() { }

    /** The completion payload has already passed the canonical write guard, unlike an engine snapshot. */
    static Map<String, Object> mergeCompletion(Map<String, Object> saved, Map<String, Object> incoming,
            List<com.portal.dto.SubTableBatchChange> changes, ObjectMapper mapper) {
        Map<String, Object> current = new HashMap<>(saved);
        current.putAll(incoming);
        SubTableBatchDeletedRows.record(current, saved, changes);
        Map<String, Object> trusted = new HashMap<>(saved);
        Object deletions = current.get(com.portal.util.SubTableBatchMetadata.DELETED_ROWS_FIELD);
        if (deletions != null) trusted.put(com.portal.util.SubTableBatchMetadata.DELETED_ROWS_FIELD, deletions);
        return mergeSnapshot(trusted, incoming, mapper);
    }

    /** Engine completion payloads have no local row versions; preserve those by frozen identity. */
    static Map<String, Object> mergeSnapshot(Map<String, Object> saved, Map<String, Object> incoming, ObjectMapper mapper) {
        Map<String, Object> merged = new HashMap<>(saved);
        merged.putAll(incoming);
        merged.remove(com.portal.util.SubTableBatchMetadata.DELETED_ROWS_FIELD);
        Object deletions = saved.get(com.portal.util.SubTableBatchMetadata.DELETED_ROWS_FIELD);
        if (deletions != null) merged.put(com.portal.util.SubTableBatchMetadata.DELETED_ROWS_FIELD, deletions);
        // Runtime capability metadata is local/server-owned, never supplied by the engine.
        merged.remove(SubTableWriteIsolation.BATCH_RUNTIME_FIELD);
        Object snapshot = saved.get(SubTableWriteIsolation.BATCH_RUNTIME_FIELD);
        if (snapshot == null) return merged;
        merged.put(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot);
        if (!(merged.get("__subTables__") instanceof Map<?, ?> source)) return merged;
        Map<String, Object> tables = mapper.convertValue(source, new TypeReference<Map<String, Object>>() { });
        SubTableBatchDeletedRows.read(saved).forEach((key, store) -> {
            if (tables.get(key) instanceof List<?> rows) tables.put(key, SubTableBatchDeletedRows.filter(rows, store));
        });
        Map<?, ?> baseline = map(saved.get("__subTables__"));
        for (Object rawForm : map(map(snapshot).get("forms")).values()) {
            Object rawControls = map(rawForm).get("controls");
            if (!(rawControls instanceof List<?> controls)) continue;
            for (Object rawControl : controls) {
                Map<?, ?> binding = map(map(map(rawControl).get("rowDesign")).get("binding"));
                preserveStoreVersions(tables, baseline, binding, mapper);
            }
        }
        merged.put("__subTables__", tables);
        return merged;
    }

    private static void preserveStoreVersions(Map<String, Object> tables, Map<?, ?> baseline,
                                              Map<?, ?> binding, ObjectMapper mapper) {
        if (!(binding.get("tableName") instanceof String name)
                || !(binding.get("primaryKeyFields") instanceof List<?> keys) || keys.isEmpty()) return;
        String storeKey = "dw:" + name.toLowerCase(java.util.Locale.ROOT);
        if (!(tables.get(storeKey) instanceof List<?> rows)
                || !(baseline.get(storeKey) instanceof List<?> originals)) return;
        Map<List<String>, Number> versions = new HashMap<>();
        for (Object original : originals) {
            Map<?, ?> prior = map(original);
            if (prior.get("_wsRowVersion") instanceof Number version && keys.stream().allMatch(k -> prior.get(k) != null)) {
                versions.put(identity(prior, keys), version);
            }
        }
        List<Map<String, Object>> copied = mapper.convertValue(rows, new TypeReference<List<Map<String, Object>>>() { });
        for (Map<String, Object> row : copied) {
            Number version = keys.stream().allMatch(k -> row.get(k) != null) ? versions.get(identity(row, keys)) : null;
            if (version != null) row.put("_wsRowVersion", version);
        }
        tables.put(storeKey, copied);
    }

    private static List<String> identity(Map<?, ?> row, List<?> keys) {
        return keys.stream().map(key -> String.valueOf(row.get(key))).toList();
    }

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
