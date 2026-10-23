package com.portal.component;

import com.platform.common.audit.SystemAuditFields;
import com.portal.exception.PortalException;
import com.portal.util.SystemAuditFieldFiller;
import java.util.*;

/** Prepares user-authored rows before either Save or Complete filters/merges them. */
final class TaskSubTableSubmission {
    private TaskSubTableSubmission() { }

    static Map<String, Object> prepare(Map<String, Object> input, Map<String, Object> baseline,
                                       Map<String, Object> form, String actor, List<String> emptied) {
        return prepare(input, baseline, form, actor, emptied, List.of());
    }

    static Map<String, Object> prepare(Map<String, Object> input, Map<String, Object> baseline,
                                       Map<String, Object> form, String actor, List<String> emptied,
                                       List<com.portal.dto.SubTableBindingScope> scopes) {
        Map<String, Object> result = com.platform.common.util.JsonUtils.getObjectMapper().convertValue(input, Map.class);
        com.portal.util.SubTableNestingSanitizer.stripDeepNestedSubTables(result);
        if (!result.containsKey("__subTables__")) return result;
        if (!(result.get("__subTables__") instanceof Map<?, ?> rawTables)) throw invalid();
        if (form == null) {
            if (!Objects.equals(rawTables, map(baseline.get("__subTables__")))) throw denied("form", "table");
            return result;
        }
        if (!(form.get("tableBindings") instanceof List<?> bindings)) throw invalid();
        Map<String, Object> tables = new LinkedHashMap<>();
        Map<?, ?> originals = map(baseline.get("__subTables__"));
        for (var entry : rawTables.entrySet()) {
            String store = String.valueOf(entry.getKey());
            List<Map<?, ?>> matches = bindings.stream().map(TaskSubTableSubmission::map)
                    .filter(b -> "SUB".equals(b.get("bindingType")))
                    .filter(b -> store.equals(storeKey(b)) || store.equals(String.valueOf(b.get("bindingId")))).toList();
            if (matches.isEmpty()) {
                if (!Objects.equals(entry.getValue(), originals.get(store))) throw denied(store, "binding");
                tables.put(store, originals.get(store));
                continue;
            }
            // Binding scopes remain authoritative when a form renders several slices of one table.
            Map<?, ?> binding = matches.get(0);
            boolean editable = !Boolean.TRUE.equals(form.get("readOnly")) && matches.stream().anyMatch(b -> "EDITABLE".equals(b.get("bindingMode")));
            if (!editable && emptied != null && emptied.contains(store)) throw denied(store, "delete");
            for (var scope : scopes == null ? List.<com.portal.dto.SubTableBindingScope>of() : scopes) {
                if (!store.equals(scope.getStoreKey())) continue;
                var scopedBinding = matches.stream().filter(b -> String.valueOf(b.get("bindingId")).equals(scope.getBindingId())).findFirst();
                if (scopedBinding.isPresent() && (Boolean.TRUE.equals(form.get("readOnly")) || !"EDITABLE".equals(scopedBinding.get().get("bindingMode")))
                        && (Boolean.TRUE.equals(scope.getEmptied()) || scope.getDeletedRows() != null && !scope.getDeletedRows().isEmpty())) throw denied(scope.getBindingId(), "delete");
            }
            if (!(binding.get("primaryKeyFields") instanceof List<?> keys) || keys.isEmpty()
                    || !(binding.get("columns") instanceof List<?> fields) || fields.isEmpty()) throw invalid();
            Map<List<Object>, Map<?, ?>> before = new LinkedHashMap<>();
            for (Object row : list(originals.get(store))) {
                if (before.putIfAbsent(identity(map(row), keys), map(row)) != null) throw invalid();
            }
            Set<String> managed = new HashSet<>();
            for (Object name : list(map(form.get("runtimeFieldsByTable")).get(store))) managed.add(String.valueOf(name));
            Map<?, ?> permissions = map(form.get("fieldPermissions"));
            List<Map<String, Object>> rows = new ArrayList<>();
            Set<List<Object>> seen = new HashSet<>();
            for (Object raw : list(entry.getValue())) {
                if (!(raw instanceof Map<?, ?>)) throw invalid();
                Map<?, ?> row = map(raw);
                List<Object> identityKey = identity(row, keys);
                if (identityKey.stream().anyMatch(value -> value == null || String.valueOf(value).isBlank())
                        || !seen.add(identityKey)) throw invalid();
                Map<?, ?> old = before.get(identityKey);
                List<Map<?, ?>> rowBindings = matches.size() == 1 ? matches : resolveBindings(matches, store, row, keys, scopes);
                Map<?, ?> rowBinding = rowBindings.isEmpty() ? binding : rowBindings.get(0);
                boolean rowEditable = !Boolean.TRUE.equals(form.get("readOnly")) && !rowBindings.isEmpty()
                        && rowBindings.stream().allMatch(b -> "EDITABLE".equals(b.get("bindingMode")));
                if (!rowEditable && old == null) throw denied(String.valueOf(rowBinding.get("bindingId")), "add");
                Map<String, Object> next = new LinkedHashMap<>();
                oldOrEmpty(old).forEach((key, value) -> next.put(String.valueOf(key), value));
                boolean changed = old == null;
                Set<String> declared = new HashSet<>();
                for (Object rawField : fields) {
                    Map<?, ?> field = map(rawField);
                    String name = String.valueOf(field.get("fieldName"));
                    declared.add(name);
                    if (SystemAuditFields.isAuditField(name) || managed.contains(name) || Boolean.TRUE.equals(field.get("isComputed"))) continue;
                    if (!row.containsKey(name)) continue; // Omitted fields are not clear declarations.
                    Object value = row.get(name);
                    boolean differs = old == null ? value != null && !"".equals(value) : !Objects.equals(old.get(name), value);
                    boolean readonly = !rowEditable || rowBindings.stream().anyMatch(b -> "READONLY".equals(permissions.get(b.get("bindingId") + ":" + name)));
                    // PK/FK identity and ownership are checked by the existing scope guard.
                    boolean identity = Boolean.TRUE.equals(field.get("isPrimaryKey")) || Boolean.TRUE.equals(field.get("isForeignKey"));
                    if (differs && (!identity || old != null) && readonly
                            && !(old == null && SubTableBatchPublishedPermissions.declaredDefault(field, value)))
                        throw denied(String.valueOf(binding.get("bindingId")), name);
                    if (differs && !identity && rowEditable) changed = true;
                    if (differs && !identity && rowEditable
                            && !structuredControl(form, rowBinding, name)) {
                        SubTableBatchEditedRowGuard.validateMetadata(List.of(field), Collections.singletonMap(name, value));
                    }
                    next.put(name, value);
                }
                for (var extra : row.entrySet()) {
                    String name = String.valueOf(extra.getKey());
                    if (declared.contains(name) || managed.contains(name)) continue;
                    if ("__subTables__".equals(name)) {
                        Map<String, Object> nested = prepareNested(extra.getValue(), oldOrEmpty(old).get(name), form, rowBindings,
                                row, actor);
                        next.put(name, nested);
                        changed |= !Objects.equals(nested, oldOrEmpty(old).get(name));
                    } else if ("_wsRowVersion".equals(name) || com.platform.common.jdbc.SubTableRowIdentity.CANONICAL_FIELD.equals(name)) {
                        next.put(name, extra.getValue());
                    } else if (!"assignee_display_name".equals(name) && !Objects.equals(oldOrEmpty(old).get(name), extra.getValue())) {
                        throw denied(String.valueOf(binding.get("bindingId")), name);
                    }
                }
                if (changed) {
                    if (!declared.contains("assignee_display_name")) next.remove("assignee_display_name");
                    if (old == null) SystemAuditFieldFiller.fillOnInsert(next, actor);
                    else SystemAuditFieldFiller.fillOnUpdate(next, actor);
                    next.keySet().removeIf(name -> SystemAuditFields.isAuditField(name) && !declared.contains(name));
                }
                rows.add(next);
            }
            if (editable) tables.put(store, rows);
            else {
                Map<List<Object>, Map<String,Object>> prepared = new LinkedHashMap<>();
                rows.forEach(row -> prepared.put(identity(row, keys), row));
                tables.put(store, list(originals.get(store)).stream().map(TaskSubTableSubmission::map)
                        .map(row -> prepared.containsKey(identity(row, keys)) ? prepared.get(identity(row, keys)) : row).toList());
            }
        }
        result.put("__subTables__", tables);
        return result;
    }

    private static List<Object> identity(Map<?, ?> row, List<?> keys) {
        return keys.stream().<Object>map(row::get).toList();
    }
    private static String storeKey(Map<?, ?> binding) {
        return (binding.get("relationTableId") != null || "RELATION".equals(binding.get("tableType")) ? "rt:" : "dw:")
                + String.valueOf(binding.get("tableName")).toLowerCase(Locale.ROOT);
    }
    private static List<Map<?, ?>> resolveBindings(List<Map<?, ?>> bindings, String store, Map<?, ?> row, List<?> keys,
                                            List<com.portal.dto.SubTableBindingScope> scopes) {
        var claims = (scopes == null ? List.<com.portal.dto.SubTableBindingScope>of() : scopes).stream()
                .filter(scope -> store.equals(scope.getStoreKey()) && scope.getRowKeys() != null)
                .filter(scope -> scope.getRowKeys().stream().anyMatch(key -> keys.stream().allMatch(name -> Objects.equals(key.get(name), row.get(name)))))
                .map(scope -> String.valueOf(scope.getBindingId())).distinct().toList();
        var matches = bindings.stream().filter(binding -> claims.contains(String.valueOf(binding.get("bindingId")))).toList();
        return matches;
    }
    private static Map<String, Object> prepareNested(Object raw, Object original, Map<String,Object> form,
                                                      List<Map<?, ?>> parentBindings, Map<?, ?> parentRow, String actor) {
        if (!(raw instanceof Map<?, ?> nested)) throw invalid();
        if (parentBindings.isEmpty()) {
            if (!Objects.equals(raw, original)) throw denied("unclaimed", "nested binding");
            return new LinkedHashMap<>((Map<String,Object>) map(original));
        }
        Map<?, ?> parentBinding = parentBindings.get(0);
        Set<String> ids = new HashSet<>();
        boolean first = true;
        for (var parent : parentBindings) {
            Set<String> placed = new HashSet<>();
            Object rules = map(map(map(form.get("configJson")).get("subForms")).get(String.valueOf(parent.get("bindingId")))).get("rule");
            collectNestedBindings(rules, placed);
            if (first) ids.addAll(placed); else ids.retainAll(placed);
            first = false;
        }
        List<Map<?, ?>> children = list(form.get("tableBindings")).stream().map(TaskSubTableSubmission::map)
                .filter(binding -> ids.contains(String.valueOf(binding.get("bindingId")))).toList();
        Map<String,Object> scoped = new LinkedHashMap<>();
        for (var slice : nested.entrySet()) {
            String store = String.valueOf(slice.getKey());
            if (Objects.equals(slice.getValue(), map(original).get(store))) {
                scoped.put(store, map(original).get(store));
                continue;
            }
            var child = children.stream().filter(b -> store.equals(storeKey(b)) || store.equals(String.valueOf(b.get("bindingId")))).findFirst()
                    .orElseThrow(() -> denied(store, "nested binding"));
            var ownership = list(child.get("columns")).stream().map(TaskSubTableSubmission::map)
                    .filter(field -> Boolean.TRUE.equals(field.get("isForeignKey")) && Objects.equals(field.get("refTableName"), parentBinding.get("tableName"))).toList();
            if (ownership.isEmpty()) throw invalid();
            List<Map<String,Object>> records = new ArrayList<>();
            for (Object record : list(slice.getValue())) {
                Map<String,Object> row = new LinkedHashMap<>();
                map(record).forEach((key,value) -> row.put(String.valueOf(key),value));
                for (var fk : ownership) {
                    if (!(fk.get("refPrimaryKeyFields") instanceof List<?> references) || references.size() != 1) throw invalid();
                    Object owner = parentRow.get(references.get(0));
                    String field = String.valueOf(fk.get("fieldName"));
                    if (owner == null) throw invalid();
                    if (row.get(field) != null && !Objects.equals(row.get(field), owner)) throw denied(store, field);
                    row.put(field, owner);
                }
                records.add(row);
            }
            scoped.put(store, records);
        }
        Map<String,Object> childForm = new LinkedHashMap<>(form);
        childForm.put("tableBindings", children);
        Map<String,Object> prepared = prepare(Map.of("__subTables__", scoped), Map.of("__subTables__", map(original)), childForm, actor, List.of());
        Map<String,Object> result = new LinkedHashMap<>((Map<String,Object>) map(original));
        result.putAll((Map<String,Object>) prepared.get("__subTables__"));
        return result;
    }
    private static void collectNestedBindings(Object value, Set<String> ids) {
        if (value instanceof List<?> list) { list.forEach(item -> collectNestedBindings(item, ids)); return; }
        if (!(value instanceof Map<?, ?> rule)) return;
        if ("subTable".equals(rule.get("type"))) {
            ids.add(com.platform.common.form.SubTableBatchDefinition.resolveBindingId(rule));
            return;
        }
        rule.values().forEach(item -> { if (item instanceof List<?> || item instanceof Map<?, ?>) collectNestedBindings(item, ids); });
    }
    private static boolean structuredControl(Map<?, ?> form, Map<?, ?> binding, String name) {
        Object rules = map(map(map(form.get("configJson")).get("subForms")).get(String.valueOf(binding.get("bindingId")))).get("rule");
        return structuredRule(rules, name);
    }
    private static boolean structuredRule(Object value, String name) {
        if (value instanceof List<?> list) return list.stream().anyMatch(item -> structuredRule(item, name));
        if (!(value instanceof Map<?, ?> rule)) return false;
        if (name.equals(rule.get("field")) && Set.of("lookup", "upload", "user", "department", "cascader", "checkbox", "transfer", "tree").contains(String.valueOf(rule.get("type")))) return true;
        return rule.values().stream().anyMatch(item -> (item instanceof Map<?, ?> || item instanceof List<?>) && structuredRule(item, name));
    }
    private static Map<?, ?> oldOrEmpty(Map<?, ?> row) { return row == null ? Map.of() : row; }
    private static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> map ? map : Map.of(); }
    private static List<?> list(Object value) {
        if (value instanceof List<?> list) return list;
        if (value == null) return List.of();
        throw invalid();
    }
    private static PortalException invalid() { return new PortalException("409", "Task sub-table submission metadata is invalid"); }
    private static PortalException denied(String binding, String field) {
        return new PortalException("403", "Task sub-table write is not permitted: binding=" + binding + ", field=" + field);
    }
}
