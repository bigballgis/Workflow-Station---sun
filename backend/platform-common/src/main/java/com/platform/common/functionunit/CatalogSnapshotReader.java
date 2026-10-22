package com.platform.common.functionunit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

/** Reads deployed metadata only; shared by Portal writes and Engine email execution. */
@Component
@RequiredArgsConstructor
public class CatalogSnapshotReader {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private record Key(String catalogId, String type) { }
    private final Map<Key, List<Map<String, Object>>> cache = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(32, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Key, List<Map<String, Object>>> entry) {
                    return size() > 128;
                }
            });

    public List<Map<String, Object>> contents(String catalogId, String type) {
        requireCatalog(catalogId);
        Key key = new Key(catalogId, type);
        List<Map<String, Object>> existing = cache.get(key);
        if (existing != null) return copy(existing);
        List<Map<String, Object>> loaded = jdbc.queryForList("SELECT content_data FROM sys_function_unit_contents "
                        + "WHERE function_unit_id = ? AND content_type = ? ORDER BY id", String.class,
                catalogId, type).stream().map(json -> parseContent(type, json)).toList();
        cache.put(key, loaded);
        return copy(loaded);
    }

    private List<Map<String, Object>> copy(List<Map<String, Object>> value) {
        return mapper.convertValue(value, new TypeReference<List<Map<String, Object>>>() { });
    }

    public List<Map<String, Object>> bindings(String catalogId) {
        return contents(catalogId, "FORM").stream()
                .flatMap(form -> formBindings(catalogId, form).stream()).toList();
    }

    public List<Map<String, Object>> formBindings(String catalogId, Map<String, Object> form) {
        List<Map<String, Object>> tables = contents(catalogId, "DATA_TABLE");
        java.util.ArrayList<Map<String, Object>> result = new java.util.ArrayList<>();
        for (var raw : objects(form.get("tableBindings"))) {
            boolean related = "RELATED".equals(raw.get("bindingType"));
            var matches = tables.stream().filter(t -> java.util.Objects.equals(t.get("tableName"), raw.get("tableName")))
                    .filter(t -> related == "RELATION".equals(t.get("tableType"))).toList();
            if (matches.size() != 1) throw invalid(catalogId);
            var table = matches.get(0);
            Map<String, Object> binding = new java.util.LinkedHashMap<>(raw);
            Object tableId = related ? table.get("relationTableId") : table.get("tableId");
            binding.put("tableId", tableId);
            binding.put("tableType", table.get("tableType"));
            binding.put("tableDisplayName", table.get("tableDisplayName"));
            if (related) {
                binding.put("relationTableId", tableId);
                binding.put("relationTableName", table.get("tableName"));
            }
            var fields = objects(table.get("fields"));
            java.util.ArrayList<Map<String, Object>> definitions = new java.util.ArrayList<>();
            for (var field : fields) {
                Map<String, Object> definition = new java.util.LinkedHashMap<>();
                for (String key : List.of("fieldName", "dataType", "isPrimaryKey", "isForeignKey", "refPrimaryKeyFields", "fkDisplayMode", "isComputed", "computedField")) {
                    definition.put(key, field.get(key));
                }
                Object pk = field.get("pkGenerationJson");
                if (pk instanceof String text) pk = parse(text);
                definition.put("pkGeneration", pk);
                if (field.get("refTableName") != null) {
                    var parents = tables.stream().filter(t -> java.util.Objects.equals(t.get("tableName"), field.get("refTableName"))).toList();
                    if (parents.size() != 1) throw invalid(catalogId);
                    Object parentId = parents.get(0).get("tableId");
                    if (parentId == null) parentId = parents.get(0).get("relationTableId");
                    definition.put("refTableId", parentId);
                    if (java.util.Objects.equals(raw.get("filterFkFieldName"), field.get("fieldName"))) binding.put("filterFkRefTableId", parentId);
                }
                definitions.add(definition);
            }
            var keys = fields.stream().filter(f -> Boolean.TRUE.equals(f.get("isPrimaryKey"))).map(f -> String.valueOf(f.get("fieldName"))).toList();
            binding.put("primaryKeyFields", keys.isEmpty() ? List.of(com.platform.common.jdbc.SubTableRowIdentity.CANONICAL_FIELD) : keys);
            binding.put("fieldDefinitions", definitions);
            binding.put("columns", fields);
            binding.put("data", List.of());
            result.add(binding);
        }
        return result;
    }

    public Map<String, Map<String, Object>> tableAliases(String catalogId) {
        Map<String, Map<String, Object>> aliases = new java.util.LinkedHashMap<>();
        for (var table : contents(catalogId, "DATA_TABLE")) {
            if ("RELATION".equals(table.get("tableType"))) continue;
            String name = String.valueOf(table.get("tableName"));
            aliases.put(name, table);
            aliases.put(name.toLowerCase(java.util.Locale.ROOT), table);
            aliases.put(com.platform.common.subtable.SubTableStoreKeys.dwKey(name), table);
        }
        for (var binding : bindings(catalogId)) {
            Object name = binding.get("tableName");
            if (name != null && aliases.containsKey(String.valueOf(name).toLowerCase(java.util.Locale.ROOT))) {
                aliases.put(String.valueOf(binding.get("bindingId")), aliases.get(String.valueOf(name).toLowerCase(java.util.Locale.ROOT)));
            }
        }
        return aliases;
    }

    public Map<String, Object> form(String catalogId, String sourceId) {
        List<Map<String, Object>> forms = contents(catalogId, "FORM").stream()
                .filter(f -> sourceId.equals(String.valueOf(f.get("formId")))).toList();
        if (forms.size() != 1) throw invalid(catalogId);
        Map<String, Object> form = new java.util.LinkedHashMap<>(forms.get(0));
        form.put("tableBindings", formBindings(catalogId, form));
        return form;
    }

    public Map<String, Object> processForm(String catalogId, boolean requestScene) {
        var forms = contents(catalogId, "FORM").stream()
                .filter(form -> "PROCESS".equals(form.get("formType"))).toList();
        var requestForms = forms.stream().filter(form -> "REQUEST".equals(form.get("scene"))).toList();
        var selected = requestScene && !requestForms.isEmpty() ? requestForms
                : forms.stream().filter(ProcessStartForm::matches).toList();
        if (selected.size() != 1) throw invalid(catalogId);
        Map<String, Object> result = form(catalogId, String.valueOf(selected.get(0).get("formId")));
        if (result.get("configJson") instanceof String json) result.put("configJson", parse(json));
        return result;
    }

    public Map<String, Object> table(String catalogId, String sourceId) {
        List<Map<String, Object>> tables = contents(catalogId, "DATA_TABLE").stream()
                .filter(t -> sourceId.equals(String.valueOf(t.get("tableId")))).toList();
        if (tables.size() != 1) throw invalid(catalogId);
        return tables.get(0);
    }

    public Map<String, Object> mainTable(String catalogId) {
        List<Map<String, Object>> tables = contents(catalogId, "DATA_TABLE").stream()
                .filter(t -> "MAIN".equals(t.get("tableType"))).toList();
        if (tables.size() != 1) throw invalid(catalogId);
        return tables.get(0);
    }

    public void requireCatalog(String catalogId) {
        if (catalogId == null || catalogId.isBlank()) {
            throw new FunctionUnitVersionException("VERSION_RECOVERY_REQUIRED", catalogId);
        }
    }

    private Map<String, Object> parseContent(String type, String json) {
        if (!"MAIN_TABLE_VIEW".equals(type)) return parse(json);
        try {
            return Map.of("views", mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() { }));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw invalid(null);
        }
    }

    public Map<String, Object> detailFormForView(String catalogId, String viewName) {
        var views = contents(catalogId, "MAIN_TABLE_VIEW").stream()
                .flatMap(content -> objects(content.get("views")).stream())
                .filter(view -> viewName.equals(view.get("viewName"))).toList();
        if (views.size() != 1) throw invalid(catalogId);
        Object name = views.get(0).get("detailFormName");
        if (name == null) return Map.of(); // The published view explicitly has no DETAIL form.
        var forms = contents(catalogId, "FORM").stream()
                .filter(form -> "DETAIL".equals(form.get("formType")) && name.equals(form.get("formName"))).toList();
        if (forms.size() != 1) throw invalid(catalogId);
        return form(catalogId, String.valueOf(forms.get(0).get("formId")));
    }

    public Map<String, Object> parse(String json) {
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new FunctionUnitVersionException("VERSION_SNAPSHOT_INCOMPLETE", null);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> objects(Object value) {
        if (!(value instanceof List<?> list) || list.stream().anyMatch(v -> !(v instanceof Map<?, ?>))) {
            throw new FunctionUnitVersionException("VERSION_SNAPSHOT_INCOMPLETE", null);
        }
        return (List<Map<String, Object>>) value;
    }

    public static FunctionUnitVersionException invalid(String catalogId) {
        return new FunctionUnitVersionException("VERSION_SNAPSHOT_INCOMPLETE", catalogId);
    }
}
