package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;

/** Freezes the metadata needed to validate membership changes in a new instance. */
final class SubTableBatchRowDesign {
    private SubTableBatchRowDesign() { }

    static void validateAddForm(Map<String, Object> design, Map<String, Object> batchForm) {
        java.util.Set<String> represented = SubTableBatchFormRules.fields(batchForm).keySet();
        if (!(design.get("fields") instanceof List<?> fields)) throw invalid("field metadata is missing");
        java.util.Set<String> defined = new java.util.HashSet<>();
        for (Object raw : fields) {
            if (!(raw instanceof Map<?, ?> field)) throw invalid("field metadata is invalid");
            defined.add(String.valueOf(field.get("fieldName")));
            if (represented.contains(field.get("fieldName")) && "FILE".equals(field.get("dataType"))) {
                throw invalid("batch attachment input is unsupported");
            }
            boolean primary = Boolean.TRUE.equals(field.get("isPrimaryKey"));
            Object config = field.get("pkGenerationJson");
            boolean manual = config instanceof Map<?, ?> generation && "manual".equals(generation.get("strategy"));
            if (Boolean.TRUE.equals(field.get("isForeignKey")) || Boolean.TRUE.equals(field.get("isComputed")) || (primary && !manual)) continue;
            if ((Boolean.FALSE.equals(field.get("nullable")) || primary) && !represented.contains(field.get("fieldName"))) {
                throw invalid("batch form omits a required user-entered field");
            }
        }
        if (!defined.containsAll(represented)) throw invalid("batch form contains an unbound field");
    }

    static Map<String, Object> capture(Map<String, Object> form, String bindingId,
                                       Map<String, Object> content, ObjectMapper mapper) {
        if (!(form.get("tableBindings") instanceof List<?> bindings)) throw invalid("binding metadata is missing");
        Map<?, ?> binding = bindings.stream().filter(raw -> raw instanceof Map<?, ?> value
                        && bindingId.equals(String.valueOf(value.get("bindingId"))))
                .map(raw -> (Map<?, ?>) raw).findFirst().orElseThrow(() -> invalid("binding metadata is missing"));
        if (!"SUB".equals(binding.get("bindingType")) || !"EDITABLE".equals(binding.get("bindingMode"))
                || !"structuralFk".equals(binding.get("bindingLinkMode"))) {
            throw invalid("Add/Delete require an editable structural business binding");
        }
        Map<String, Object> table = findTable(binding.get("tableName"), content, mapper);
        if (!(table.get("fields") instanceof List<?> fields) || fields.isEmpty()) throw invalid("field metadata is missing");
        Map<String, Object> tableIds = resolvedTableIds(content);
        Map<String, Object> frozenBinding = new LinkedHashMap<>();
        binding.forEach((key, value) -> frozenBinding.put(String.valueOf(key), value));
        frozenBinding.put("fieldDefinitions", runtimeFields(fields, tableIds));
        frozenBinding.put("primaryKeyFields", primaryKeys(fields));
        Map<String, Object> parents = new LinkedHashMap<>();
        for (Object raw : fields) {
            if (!(raw instanceof Map<?, ?> field) || !Boolean.TRUE.equals(field.get("isForeignKey"))) continue;
            String parentName = String.valueOf(field.get("refTableName"));
            Map<String, Object> parent = findTable(parentName, content, mapper);
            if (!(parent.get("fields") instanceof List<?> parentFields)) throw invalid("parent fields are missing");
            Object parentId = tableIds.get(parentName);
            if (parentId == null) throw invalid("parent table identity is unresolved");
            parents.put(String.valueOf(parentId), Map.of("fieldDefinitions", runtimeFields(parentFields, tableIds),
                    "tableName", parentName, "tableType", parent.get("tableType"), "primaryKeyFields", primaryKeys(parentFields)));
        }
        if (binding.get("filterFkRefTableName") instanceof String parentName) {
            frozenBinding.put("filterFkRefTableId", tableIds.get(parentName));
        }
        return Map.of("binding", frozenBinding, "fields", fields, "parentTablesById", parents,
                "dependencies", dependencies(binding.get("tableName"), content, mapper));
    }

    /** Only runtime table ids are resolved externally; field semantics always come from published JSON. */
    private static Map<String, Object> resolvedTableIds(Map<String, Object> content) {
        Map<String, Object> ids = new LinkedHashMap<>();
        if (!(content.get("forms") instanceof List<?> forms)) throw invalid("form metadata is missing");
        for (Object rawForm : forms) {
            if (!(rawForm instanceof Map<?, ?> form) || !(form.get("tableBindings") instanceof List<?> bindings)) continue;
            for (Object raw : bindings) {
                if (!(raw instanceof Map<?, ?> binding) || !(binding.get("tableName") instanceof String name)
                        || !(binding.get("tableId") instanceof Number id)) continue;
                Object previous = ids.putIfAbsent(name, id);
                if (previous != null && !String.valueOf(previous).equals(String.valueOf(id))) throw invalid("table identity is ambiguous");
            }
        }
        return ids;
    }

    private static List<Map<String, Object>> runtimeFields(List<?> fields, Map<String, Object> tableIds) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object raw : fields) {
            if (!(raw instanceof Map<?, ?> field)) throw invalid("field metadata is invalid");
            Map<String, Object> runtime = new LinkedHashMap<>();
            field.forEach((key, value) -> runtime.put(String.valueOf(key), value));
            runtime.put("pkGeneration", field.get("pkGenerationJson"));
            if (Boolean.TRUE.equals(field.get("isForeignKey"))) {
                Object parentId = tableIds.get(String.valueOf(field.get("refTableName")));
                if (parentId == null) throw invalid("reference table identity is unresolved");
                runtime.put("refTableId", parentId);
            }
            result.add(runtime);
        }
        return result;
    }

    private static List<String> primaryKeys(List<?> fields) {
        List<String> keys = fields.stream().filter(raw -> raw instanceof Map<?, ?> field && Boolean.TRUE.equals(field.get("isPrimaryKey")))
                .map(raw -> String.valueOf(((Map<?, ?>) raw).get("fieldName"))).toList();
        return keys.isEmpty() ? com.platform.common.jdbc.SubTableRowIdentity.IDENTITY_FIELDS : keys;
    }

    private static List<Map<String, Object>> dependencies(Object tableName, Map<String, Object> content, ObjectMapper mapper) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!(content.get("dataTables") instanceof List<?> tables)) throw invalid("table metadata is missing");
        for (Object raw : tables) {
            if (!(raw instanceof Map<?, ?> wrapper) || !(wrapper.get("data") instanceof String data)) continue;
            Map<String, Object> table;
            try { table = mapper.readValue(data, new TypeReference<Map<String, Object>>() { }); }
            catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw invalid("table metadata is invalid"); }
            if (!(table.get("fields") instanceof List<?> fields)) throw invalid("field metadata is missing");
            for (Object rawField : fields) {
                if (!(rawField instanceof Map<?, ?> field) || !Boolean.TRUE.equals(field.get("isForeignKey"))) continue;
                if (tableName != null && tableName.equals(field.get("refTableName"))) {
                    if (!(field.get("refPrimaryKeyFields") instanceof List<?> keys) || keys.isEmpty()) throw invalid("reference identity is missing");
                    result.add(Map.of("storeKey", "dw:" + String.valueOf(table.get("tableName")).toLowerCase(java.util.Locale.ROOT),
                            "fieldName", field.get("fieldName"), "refPrimaryKeyFields", keys));
                }
            }
        }
        return result;
    }

    private static Map<String, Object> findTable(Object name, Map<String, Object> content, ObjectMapper mapper) {
        if (!(content.get("dataTables") instanceof List<?> tables)) throw invalid("table metadata is missing");
        for (Object raw : tables) {
            if (!(raw instanceof Map<?, ?> wrapper) || !(wrapper.get("data") instanceof String data)) continue;
            Map<String, Object> table;
            try { table = mapper.readValue(data, new TypeReference<Map<String, Object>>() { }); }
            catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw invalid("table metadata is invalid"); }
            if (name != null && name.equals(table.get("tableName"))) return table;
        }
        throw invalid("bound table metadata is missing");
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Batch membership configuration: " + message);
    }
}
