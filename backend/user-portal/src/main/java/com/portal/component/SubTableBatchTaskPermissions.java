package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.audit.SystemAuditFields;
import com.portal.exception.PortalException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Additional authority for explicitly declared batch operations; ordinary writes use TaskSubTableSubmission. */
final class SubTableBatchTaskPermissions {
    private SubTableBatchTaskPermissions() { }

    static Map<String, Object> activate(Map<String, Object> variables, Map<String, Object> form) {
        if (!enhanced(variables) || form == null) return form;
        if (!(form.get("publishedFieldPermissions") instanceof Map<?, ?> permissions)) throw invalid();
        if (permissions.entrySet().stream().anyMatch(entry -> !(entry.getKey() instanceof String)
                || !("READONLY".equals(entry.getValue()) || "EDITABLE".equals(entry.getValue())))) throw invalid();
        Map<String, Object> result = new LinkedHashMap<>(form);
        result.put("fieldPermissions", permissions);
        return result;
    }

    /** A partial save omits untouched tables; omission is never a delete declaration. */
    static void preserveOmittedStores(Map<String, Object> variables, Map<String, Object> inbound) {
        if (!enhanced(variables) || !inbound.containsKey("__subTables__")) return;
        if (!(inbound.get("__subTables__") instanceof Map<?, ?> submitted)) throw invalid();
        Map<String, Object> merged = new LinkedHashMap<>();
        if (variables.get("__subTables__") instanceof Map<?, ?> baseline) {
            baseline.forEach((key, value) -> {
                if (!submitted.containsKey(key)) merged.put(String.valueOf(key),
                        com.platform.common.util.JsonUtils.getObjectMapper().convertValue(value, Object.class));
            });
        }
        for (Map.Entry<?, ?> entry : submitted.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof List<?> rows)
                    || rows.stream().anyMatch(row -> !(row instanceof Map<?, ?>))) throw invalid();
            merged.put(key, entry.getValue());
        }
        inbound.put("__subTables__", merged);
    }

    static void validate(Map<String, Object> variables, Map<String, Object> form,
                         Map<String, Object> submitted, ObjectMapper mapper) {
        validate(variables, form, submitted, mapper, null);
    }

    static void validate(Map<String, Object> variables, Map<String, Object> form,
                         Map<String, Object> submitted, ObjectMapper mapper, List<com.portal.dto.SubTableBatchChange> changes) {
        SubTableBatchWorkspaceContext.validate(variables, submitted);
        if (changes == null || changes.isEmpty()) return;
        if (!enhanced(variables) || !submitted.containsKey("__subTables__")) return;
        if (!(submitted.get("__subTables__") instanceof Map<?, ?> tables)) throw invalid();
        Map<?, ?> baseline = map(variables.get("__subTables__"));
        if (form == null) {
            if (!tables.equals(baseline)) throw forbidden();
            return; // A task with no form may complete without changing any business table.
        }
        Map<?, ?> permissions = map(form.get("fieldPermissions"));
        SubTableBatchChangeGuard.validate(variables, form, changes, mapper);
        Map<String, Object> controls = SubTableBatchRuntimeSnapshot.projectForForm(variables, form.get("sourceId"), mapper);
        for (Object raw : controls.values()) {
            Map<?, ?> control = map(raw);
            var declared = changes.stream().filter(change -> Objects.equals(control.get("batchControlId"), change.getBatchControlId())
                    && String.valueOf(control.get("bindingId")).equals(change.getBindingId().trim())).toList();
            if (!declared.isEmpty()) validateControl(control, permissions, tables, baseline, declared);
        }
    }

    private static void validateControl(Map<?, ?> control, Map<?, ?> permissions, Map<?, ?> tables, Map<?, ?> baseline,
                                        List<com.portal.dto.SubTableBatchChange> changes) {
        if (!(control.get("rowDesign") instanceof Map<?, ?> design)) throw invalid();
        Map<?, ?> binding = map(design.get("binding"));
        if (!(binding.get("tableName") instanceof String name) || !(binding.get("primaryKeyFields") instanceof List<?> keys)
                || keys.isEmpty() || !(design.get("fields") instanceof List<?> fields)) throw invalid();
        String store = "dw:" + name.toLowerCase(java.util.Locale.ROOT);
        Map<List<String>, Map<?, ?>> originals = index(baseline.get(store), keys);
        java.util.Set<List<String>> seen = new java.util.HashSet<>();
        for (Object raw : list(tables.get(store))) {
            Map<?, ?> row = map(raw);
            List<String> id = identity(row, keys);
            if (!seen.add(id)) throw invalid();
            var rowChanges = changes.stream().filter(change -> change.getRowPatches().stream()
                    .anyMatch(patch -> keys.stream().allMatch(key -> Objects.equals(
                            String.valueOf(patch.getRowKey().get(key)), String.valueOf(row.get(key)))))).toList();
            if (rowChanges.isEmpty()) continue; // Ordinary sibling edits are authorized by TaskSubTableSubmission.
            Map<?, ?> original = originals.get(id);
            boolean metadataChanged = original == null;
            for (Object rawField : fields) {
                Map<?, ?> field = map(rawField);
                String fieldName = String.valueOf(field.get("fieldName"));
                metadataChanged |= original != null && !Objects.equals(original.get(fieldName), row.get(fieldName));
                // Ordinary row dialogs fill audit columns at Save; the existing task writer
                // preserves them independently of designer-controlled business permissions.
                if (SystemAuditFields.isAuditField(fieldName)) continue;
                if (!"READONLY".equals(permissions.get(control.get("bindingId") + ":" + fieldName))) continue;
                if (Boolean.TRUE.equals(field.get("isComputed")) || Boolean.TRUE.equals(field.get("isForeignKey"))
                        || Boolean.TRUE.equals(field.get("isPrimaryKey"))) continue; // Existing identity/scope/generated-value guards own these.
                if (original != null && !Objects.equals(original.get(fieldName), row.get(fieldName))) throw forbidden();
                if (original == null && row.get(fieldName) != null && !"".equals(row.get(fieldName))
                        && !SubTableBatchPublishedPermissions.declaredDefault(field, row.get(fieldName))) throw forbidden();
            }
            boolean assignmentOnly = SubTableBatchAssignmentOnly.validate(control, fields, original, row, changes);
            if (metadataChanged && !assignmentOnly && rowChanges.stream().anyMatch(change -> !"ASSIGN".equals(change.getOperation()))) {
                SubTableBatchEditedRowGuard.validateMetadata(fields, row, map(control.get("batchForm")));
            }
        }
    }

    private static Map<List<String>, Map<?, ?>> index(Object rows, List<?> keys) {
        Map<List<String>, Map<?, ?>> result = new LinkedHashMap<>();
        for (Object raw : list(rows)) {
            Map<?, ?> row = map(raw);
            if (result.putIfAbsent(identity(row, keys), row) != null) throw invalid();
        }
        return result;
    }
    private static List<String> identity(Map<?, ?> row, List<?> keys) {
        return keys.stream().map(key -> {
            if (!(key instanceof String name) || name.isBlank()) throw invalid();
            Object value = row.get(name);
            if (!(value instanceof String || value instanceof Number) || String.valueOf(value).isBlank()) throw invalid();
            return String.valueOf(value);
        }).toList();
    }
    private static boolean enhanced(Map<String, Object> variables) { return variables != null && variables.containsKey(SubTableWriteIsolation.BATCH_RUNTIME_FIELD); }
    private static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> map ? map : Map.of(); }
    private static List<?> list(Object value) { return value instanceof List<?> list ? list : List.of(); }
    private static PortalException invalid() { return new PortalException("409", "Current batch-enabled task permission authority is invalid"); }
    private static PortalException forbidden() { return new PortalException("403", "Task submission changes a read-only or unavailable table field"); }
}
