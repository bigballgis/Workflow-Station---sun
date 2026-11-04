package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.exception.PortalException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Validate legacy computed text cells against server results without changing submitted business values. */
final class ComputedFieldBatchValidation {
    private ComputedFieldBatchValidation() { }

    static Map<String, Object> prepare(Map<String, Object> variables, Map<String, Object> form,
                                       Map<String, Object> submitted, ObjectMapper mapper,
                                       Consumer<Map<String, Object>> recalculate) {
        if (form == null || variables == null || !variables.containsKey(SubTableWriteIsolation.BATCH_RUNTIME_FIELD)
                || !(submitted.get("__subTables__") instanceof Map<?, ?> stores)) return submitted;
        List<Target> targets = new ArrayList<>();
        for (Object raw : SubTableBatchRuntimeSnapshot.projectForForm(variables, form.get("sourceId"), mapper).values()) {
            Map<?, ?> design = map(map(raw).get("rowDesign"));
            Map<?, ?> binding = map(design.get("binding"));
            if (!(binding.get("tableName") instanceof String name) || !(binding.get("primaryKeyFields") instanceof List<?> keys)
                    || !(design.get("fields") instanceof List<?> fields)) continue;
            String store = "dw:" + name.toLowerCase(Locale.ROOT);
            List<String> computedText = fields.stream().map(ComputedFieldBatchValidation::map)
                    .filter(field -> Boolean.TRUE.equals(field.get("isComputed"))
                            && Set.of("VARCHAR", "CHAR", "TEXT").contains(String.valueOf(field.get("dataType"))))
                    .map(field -> String.valueOf(field.get("fieldName"))).toList();
            if (rows(stores.get(store)).stream().anyMatch(row -> computedText.stream().anyMatch(field -> row.get(field) instanceof Number))) {
                targets.add(new Target(store, keys, computedText));
            }
        }
        if (targets.isEmpty()) return submitted;
        Map<String, Object> combined = new LinkedHashMap<>(variables);
        combined.putAll(submitted);
        SubTableBatchTaskPermissions.preserveOmittedStores(variables, combined);
        combined = mapper.convertValue(combined, new TypeReference<Map<String, Object>>() { });
        recalculate.accept(combined);
        Map<String, Object> checked = mapper.convertValue(submitted, new TypeReference<Map<String, Object>>() { });
        Map<?, ?> calculatedStores = map(combined.get("__subTables__"));
        Map<?, ?> checkedStores = map(checked.get("__subTables__"));
        for (Target target : targets) {
            Map<List<String>, Map<?, ?>> calculated = new LinkedHashMap<>();
            for (Map<?, ?> row : rows(calculatedStores.get(target.store()))) {
                if (calculated.putIfAbsent(identity(row, target.keys()), row) != null) throw invalidIdentity();
            }
            for (Map<?, ?> row : rows(checkedStores.get(target.store()))) {
                Map<?, ?> expected = calculated.get(identity(row, target.keys()));
                if (expected == null) throw invalidIdentity();
                @SuppressWarnings("unchecked") Map<String, Object> mutable = (Map<String, Object>) row;
                for (String field : target.fields()) mutable.put(field, expected.get(field));
            }
        }
        return checked;
    }

    private static List<String> identity(Map<?, ?> row, List<?> keys) {
        if (keys.isEmpty()) throw invalidIdentity();
        List<String> identity = new ArrayList<>(keys.size());
        for (Object key : keys) {
            if (!(key instanceof String name) || row.get(name) == null) throw invalidIdentity();
            identity.add(String.valueOf(row.get(name)));
        }
        return identity;
    }

    private static PortalException invalidIdentity() { return new PortalException("409", "Computed validation row identity is ambiguous"); }

    private record Target(String store, List<?> keys, List<String> fields) { }
    private static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> map ? map : Map.of(); }
    private static List<Map<?, ?>> rows(Object value) {
        return value instanceof List<?> list ? list.stream().<Map<?, ?>>map(ComputedFieldBatchValidation::map).toList() : List.of();
    }
}
