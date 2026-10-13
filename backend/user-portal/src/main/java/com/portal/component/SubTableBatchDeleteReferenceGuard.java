package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.dto.SubTableBatchChange;
import com.portal.exception.PortalException;

import java.util.List;
import java.util.Map;

/** Rejects deleting a parent business row that still has referenced JSON child rows. */
final class SubTableBatchDeleteReferenceGuard {
    private SubTableBatchDeleteReferenceGuard() { }

    static void validate(Map<String, Object> variables, Map<String, Object> form,
                         List<SubTableBatchChange> changes, ObjectMapper mapper) {
        if (changes == null || changes.isEmpty()) return;
        Map<String, Object> controls = SubTableBatchRuntimeSnapshot.projectForForm(variables, form.get("sourceId"), mapper);
        Map<?, ?> tables = map(variables.get("__subTables__"));
        for (SubTableBatchChange change : changes) {
            if (!"DELETE".equals(change.getOperation())) continue;
            Map<?, ?> design = map(map(controls.get(change.getBatchControlId())).get("rowDesign"));
            if (!(design.get("dependencies") instanceof List<?> dependencies)) {
                throw new PortalException("409", "Batch delete reference metadata is missing");
            }
            for (Object raw : dependencies) {
                Map<?, ?> dependency = map(raw);
                Object rawRows = tables.get(dependency.get("storeKey"));
                if (!(rawRows instanceof List<?> rows)) continue; // This instance has no rows in that related table.
                for (SubTableBatchChange.RowPatch patch : change.getRowPatches()) {
                    Object parentValue = encodedKey(patch.getRowKey(), dependency.get("refPrimaryKeyFields"));
                    if (rows.stream().anyMatch(row -> parentValue.equals(String.valueOf(map(row).get(dependency.get("fieldName")))))) {
                        throw new PortalException("409", "Batch delete is blocked by related child rows");
                    }
                }
            }
        }
    }

    private static String encodedKey(Map<String, Object> key, Object rawFields) {
        if (!(rawFields instanceof List<?> fields) || fields.isEmpty() || !key.keySet().containsAll(fields)) {
            throw new PortalException("409", "Batch delete cannot resolve the configured reference key");
        }
        List<String> names = fields.stream().map(String::valueOf).sorted().toList();
        return names.size() == 1 ? String.valueOf(key.get(names.get(0)))
                : names.stream().map(name -> name + "=" + key.get(name)).collect(java.util.stream.Collectors.joining("\u001f"));
    }

    private static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> map ? map : Map.of(); }
}
