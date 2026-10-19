package com.portal.component;

import com.portal.dto.SubTableBatchChange;
import com.portal.exception.PortalException;
import com.portal.util.SubTableBatchMetadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Local deletion identities distinguish stale engine rows from new service output. */
final class SubTableBatchDeletedRows {
    private SubTableBatchDeletedRows() { }

    record Store(List<String> primaryKeyFields, Set<List<String>> identities) { }

    static Map<String, Store> read(Map<String, Object> variables) {
        if (variables == null || !variables.containsKey(SubTableBatchMetadata.DELETED_ROWS_FIELD)) return Map.of();
        Object raw = variables.get(SubTableBatchMetadata.DELETED_ROWS_FIELD);
        if (!(raw instanceof Map<?, ?> stores)) throw invalid();
        Map<String, Store> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : stores.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof Map<?, ?> value)) throw invalid();
            List<String> fields = fields(value.get("primaryKeyFields"));
            if (!(value.get("identities") instanceof List<?> identities)) throw invalid();
            Set<List<String>> parsed = new LinkedHashSet<>();
            for (Object identity : identities) {
                List<String> parts = fields(identity);
                if (parts.size() != fields.size()) throw invalid();
                parsed.add(parts);
            }
            result.put(key, new Store(fields, parsed));
        }
        return result;
    }

    /** Called only after the existing batch/scope guards validate the complete canonical write. */
    static void record(Map<String, Object> updated, Map<String, Object> baseline, List<SubTableBatchChange> changes) {
        if ((changes == null || changes.isEmpty()) && !baseline.containsKey(SubTableBatchMetadata.DELETED_ROWS_FIELD)) return;
        List<SubTableBatchChange> declared = changes == null ? List.of() : changes;
        Map<String, Store> stores = new LinkedHashMap<>(read(baseline));
        Map<String, SubTableWriteDesign.Binding> bindings = SubTableBatchScopeDesign.overlay(Map.of(), baseline, declared);
        for (SubTableBatchChange change : declared) {
            if (!"ADD".equals(change.getOperation()) && !"DELETE".equals(change.getOperation())) continue;
            SubTableWriteDesign.Binding binding = bindings.get(change.getBindingId());
            String key = binding.storeKey();
            List<String> fields = binding.pk();
            Set<List<String>> deleted = new LinkedHashSet<>();
            Store prior = stores.get(key);
            if (prior != null) {
                if (!prior.primaryKeyFields().equals(fields)) throw invalid();
                deleted.addAll(prior.identities());
            }
            Set<List<String>> current = identities(updated, key, fields);
            if ("DELETE".equals(change.getOperation())) {
                Set<List<String>> removed = identities(baseline, key, fields);
                removed.removeAll(current);
                deleted.addAll(removed);
            }
            deleted.removeAll(current); // A validated manual-PK Add may reuse a previously deleted identity.
            stores.put(key, new Store(fields, deleted));
        }
        releaseSavedIdentities(stores, updated);
        Map<String, Object> encoded = new LinkedHashMap<>();
        stores.forEach((key, store) -> encoded.put(key,
                Map.of("primaryKeyFields", store.primaryKeyFields(), "identities", new ArrayList<>(store.identities()))));
        if (!encoded.isEmpty()) updated.put(SubTableBatchMetadata.DELETED_ROWS_FIELD, encoded);
    }

    /** Ordinary authorized Add must also release a reused manual identity. */
    private static void releaseSavedIdentities(Map<String, Store> stores, Map<String, Object> updated) {
        for (Map.Entry<String, Store> entry : stores.entrySet()) {
            if (updated.get("__subTables__") instanceof Map<?, ?> tables && tables.get(entry.getKey()) instanceof List<?>) {
                Set<List<String>> deleted = new LinkedHashSet<>(entry.getValue().identities());
                deleted.removeAll(identities(updated, entry.getKey(), entry.getValue().primaryKeyFields()));
                entry.setValue(new Store(entry.getValue().primaryKeyFields(), deleted));
            }
        }
    }

    static List<?> filter(List<?> rows, Store store) {
        if (store == null || store.identities().isEmpty()) return rows;
        return rows.stream().filter(raw -> !(raw instanceof Map<?, ?> row)
                || !store.identities().contains(identity(row, store.primaryKeyFields()))).toList();
    }

    private static Set<List<String>> identities(Map<String, Object> variables, String key, List<String> fields) {
        if (!(variables.get("__subTables__") instanceof Map<?, ?> tables) || !(tables.get(key) instanceof List<?> rows)) throw invalid();
        Set<List<String>> result = new LinkedHashSet<>();
        for (Object raw : rows) {
            if (!(raw instanceof Map<?, ?> row)) throw invalid();
            List<String> id = identity(row, fields);
            if (id == null) throw invalid();
            result.add(id);
        }
        return result;
    }

    private static List<String> identity(Map<?, ?> row, List<String> fields) {
        if (fields.stream().anyMatch(field -> row.get(field) == null)) return null;
        return fields.stream().map(field -> String.valueOf(row.get(field))).toList();
    }
    private static List<String> fields(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()
                || list.stream().anyMatch(value -> !(value instanceof String text) || text.isBlank())) throw invalid();
        return list.stream().map(String.class::cast).toList();
    }
    private static PortalException invalid() { return new PortalException("409", "Saved batch deletion identities are invalid"); }
}
