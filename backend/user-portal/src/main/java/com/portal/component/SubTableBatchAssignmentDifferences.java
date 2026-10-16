package com.portal.component;

import com.portal.exception.PortalException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Server-derived stage checks for actual assignment changes, independent of client batch markers. */
final class SubTableBatchAssignmentDifferences {
    private SubTableBatchAssignmentDifferences() { }
    record Control(String id, String bindingId) { }

    static List<Control> changedControls(Map<String, Object> variables, Map<String, Object> controls,
                                         Map<String, Object> submitted) {
        if (submitted == null || !submitted.containsKey("__subTables__")) return List.of();
        Map<?, ?> tables = requiredMap(submitted.get("__subTables__"));
        Map<?, ?> baseline = variables.containsKey("__subTables__") ? requiredMap(variables.get("__subTables__")) : Map.of();
        List<Control> changed = new ArrayList<>();
        for (Object raw : controls.values()) {
            Map<?, ?> control = requiredMap(raw);
            if (!Boolean.TRUE.equals(requiredMap(control.get("capabilities")).get("allowBatchAssign"))) continue;
            Map<?, ?> binding = requiredMap(requiredMap(control.get("rowDesign")).get("binding"));
            String store = "dw:" + requiredText(binding.get("tableName")).toLowerCase(java.util.Locale.ROOT);
            if (!tables.containsKey(store)) continue; // An omitted store is not a request to clear its targets.
            List<String> keys = requiredStrings(binding.get("primaryKeyFields"));
            Map<?, ?> config = requiredMap(control.get("assignmentConfig"));
            List<String> fields = new ArrayList<>();
            for (String property : List.of("assigneeField", "roleField", "buField")) {
                if (config.get(property) != null) fields.add(requiredText(config.get(property)));
            }
            if (fields.isEmpty()) throw invalid();
            Map<List<String>, Map<?, ?>> originals = baseline.containsKey(store) ? index(baseline.get(store), keys) : Map.of();
            boolean differs = index(tables.get(store), keys).entrySet().stream().anyMatch(entry -> {
                Map<?, ?> old = originals.get(entry.getKey());
                return fields.stream().anyMatch(field -> !same(old == null ? null : old.get(field), entry.getValue().get(field)));
            });
            if (differs) changed.add(new Control(requiredText(control.get("batchControlId")), requiredText(control.get("bindingId"))));
        }
        return changed;
    }

    private static Map<List<String>, Map<?, ?>> index(Object value, List<String> keys) {
        if (!(value instanceof List<?> rows)) throw invalid();
        Map<List<String>, Map<?, ?>> result = new LinkedHashMap<>();
        for (Object raw : rows) {
            Map<?, ?> row = requiredMap(raw);
            List<String> identity = keys.stream().map(key -> {
                Object part = row.get(key);
                if (!(part instanceof String || part instanceof Number) || String.valueOf(part).isBlank()) throw invalid();
                return String.valueOf(part);
            }).toList();
            if (result.putIfAbsent(identity, row) != null) throw invalid();
        }
        return result;
    }

    static java.util.Set<String> changedPersonalTargets(Map<String, Object> variables, Map<String, Object> controls,
                                                        Map<String, Object> submitted) {
        java.util.Set<String> targets = new java.util.LinkedHashSet<>();
        if (submitted == null || !submitted.containsKey("__subTables__")) return targets;
        Map<?, ?> baseline = variables.containsKey("__subTables__") ? requiredMap(variables.get("__subTables__")) : Map.of();
        Map<?, ?> tables = requiredMap(submitted.get("__subTables__"));
        for (Control changed : changedControls(variables, controls, submitted)) {
            Map<?, ?> control = requiredMap(controls.get(changed.id()));
            Map<?, ?> config = requiredMap(control.get("assignmentConfig"));
            Object field = config.get("assigneeField");
            if (field == null) continue;
            String name = requiredText(field);
            List<String> assignmentFields = List.of("assigneeField", "roleField", "buField").stream()
                    .map(config::get).filter(Objects::nonNull).map(SubTableBatchAssignmentDifferences::requiredText).toList();
            Map<?, ?> binding = requiredMap(requiredMap(control.get("rowDesign")).get("binding"));
            String store = "dw:" + requiredText(binding.get("tableName")).toLowerCase(java.util.Locale.ROOT);
            List<String> keys = requiredStrings(binding.get("primaryKeyFields"));
            Map<List<String>, Map<?, ?>> old = baseline.containsKey(store) ? index(baseline.get(store), keys) : Map.of();
            index(tables.get(store), keys).forEach((identity, row) -> {
                Object person = row.get(name);
                Map<?, ?> before = old.get(identity);
                if (!empty(person) && assignmentFields.stream().anyMatch(key -> !same(before == null ? null : before.get(key), row.get(key)))) {
                    targets.add(requiredText(person));
                }
            });
        }
        return targets;
    }

    private static boolean same(Object first, Object second) {
        return (empty(first) && empty(second)) || Objects.equals(first, second);
    }
    private static boolean empty(Object value) { return value == null || "".equals(value); }
    private static List<String> requiredStrings(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) throw invalid();
        return list.stream().map(SubTableBatchAssignmentDifferences::requiredText).toList();
    }
    private static String requiredText(Object value) {
        if (!(value instanceof String text) || text.isBlank()) throw invalid();
        return text;
    }
    private static Map<?, ?> requiredMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw invalid();
        return map;
    }
    private static PortalException invalid() { return new PortalException("409", "Frozen assignment difference context is invalid"); }
}
