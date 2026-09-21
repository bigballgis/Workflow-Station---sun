package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.exception.PortalException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves write rules from the same immutable catalog used to render the instance. */
@Component
@RequiredArgsConstructor
class SubTableWriteDesign {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    record Binding(String id, String storeKey, String filterField, String refType,
                   String refTableName, List<String> pk, List<String> refPk, String linkMode,
                   List<String> foreignKeyFields, List<String> explicitFillFields) {
        Binding(String id, String storeKey, String filterField, String refType,
                String refTableName, List<String> pk, List<String> refPk, String linkMode) {
            this(id, storeKey, filterField, refType, refTableName, pk, refPk, linkMode,
                    List.of(), List.of());
        }
    }

    /** Null is exclusively the legacy, non-frozen form contract. */
    Map<String, Binding> resolve(String catalogId, String stageId) {
        if (!StringUtils.hasText(catalogId)) return null;
        List<Map<String, Object>> forms = contents(catalogId, "FORM");
        Long taskFormId = null;
        if (StringUtils.hasText(stageId)) {
            List<String> processes = jdbc.queryForList(
                    "SELECT content_data FROM sys_function_unit_contents WHERE function_unit_id = ? "
                            + "AND content_type = 'PROCESS' ORDER BY created_at DESC LIMIT 1",
                    String.class, catalogId);
            if (!processes.isEmpty()) {
                taskFormId = ChangeHistoryBpmnFormResolver.resolveTaskFormIdFromStored(processes.get(0), stageId);
            }
        }
        Long selectedId = taskFormId;
        List<Map<String, Object>> selected = forms.stream().filter(form -> selectedId != null
                ? String.valueOf(selectedId).equals(String.valueOf(form.get("formId")))
                : "PROCESS".equals(form.get("formType"))).toList();
        if (selected.isEmpty() && forms.stream().noneMatch(f -> f.containsKey("tableBindings"))) {
            // FALLBACK(migration): pre-freeze catalog packages contain bare configJson.
            // Retire only after those pinned instances have completed; never upgrade their semantics.
            return null;
        }
        if (selected.size() != 1) throw invalid("Pinned form is missing or ambiguous");
        Map<String, Object> form = selected.get(0);
        if (!form.containsKey("tableBindings")) return null; // Same legacy package contract above.
        Map<String, Map<String, Object>> tables = new LinkedHashMap<>();
        for (Map<String, Object> table : contents(catalogId, "DATA_TABLE")) {
            String tableName = normalizedName(table.get("tableName"));
            if (tableName == null || tables.put(tableName, table) != null) {
                throw invalid("Pinned table identity is missing or duplicated");
            }
        }
        Map<String, Binding> out = new LinkedHashMap<>();
        for (Map<String, Object> binding : objects(form.get("tableBindings"))) {
            if (!"SUB".equals(binding.get("bindingType"))) continue;
            String name = normalizedName(binding.get("tableName"));
            Map<String, Object> table = requireTable(tables, name);
            String refName = normalizedName(binding.get("filterFkRefTableName"));
            String field = text(binding.get("filterFkFieldName"));
            if (field == null) {
                String structuralField = text(binding.get("foreignKeyField"));
                Map<String, Object> configuredFk = objects(table.get("fields")).stream()
                        .filter(f -> Boolean.TRUE.equals(f.get("isForeignKey")))
                        .filter(f -> structuralField != null
                                && structuralField.equalsIgnoreCase(text(f.get("fieldName"))))
                        .findFirst().orElse(null);
                if (configuredFk != null) {
                    field = structuralField;
                    refName = normalizedName(configuredFk.get("refTableName"));
                }
            }
            if (field != null && refName == null) {
                String lookupField = field;
                refName = objects(table.get("fields")).stream()
                        .filter(f -> lookupField.equalsIgnoreCase(text(f.get("fieldName"))))
                        .map(f -> normalizedName(f.get("refTableName"))).findFirst().orElse(null);
            }
            Map<String, Object> parent = refName == null ? Map.of() : requireTable(tables, refName);
            String id = text(binding.get("bindingId"));
            if (id == null || out.containsKey(id)) throw invalid("Pinned binding identity is missing or duplicated");
            out.put(id, new Binding(id, "dw:" + name.toLowerCase(java.util.Locale.ROOT), field,
                    text(parent.get("tableType")), refName, primaryKeys(table), primaryKeys(parent),
                    text(binding.get("bindingLinkMode")), foreignKeyFields(table),
                    explicitFillFields(binding, table)));
        }
        return out;
    }

    private List<Map<String, Object>> contents(String catalogId, String type) {
        return jdbc.queryForList("SELECT content_data FROM sys_function_unit_contents "
                + "WHERE function_unit_id = ? AND content_type = ?", String.class, catalogId, type)
                .stream().map(this::parse).toList();
    }

    private Map<String, Object> parse(String json) {
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw invalid("Pinned write configuration is invalid");
        }
    }

    private static Map<String, Object> requireTable(Map<String, Map<String, Object>> tables, String name) {
        Map<String, Object> table = tables.get(name);
        if (table == null) throw invalid("Pinned table configuration is missing");
        return table;
    }

    private static List<String> primaryKeys(Map<String, Object> table) {
        return objects(table.get("fields")).stream().filter(f -> Boolean.TRUE.equals(f.get("isPrimaryKey")))
                .map(f -> text(f.get("fieldName"))).toList();
    }

    private static List<String> foreignKeyFields(Map<String, Object> table) {
        return objects(table.get("fields")).stream().filter(f -> Boolean.TRUE.equals(f.get("isForeignKey")))
                .map(f -> text(f.get("fieldName"))).filter(java.util.Objects::nonNull).toList();
    }

    private static List<String> explicitFillFields(Map<String, Object> binding, Map<String, Object> table) {
        Map<String, String> namesById = new LinkedHashMap<>();
        for (Map<String, Object> field : objects(table.get("fields"))) {
            String id = text(field.get("id"));
            String name = text(field.get("fieldName"));
            if (id != null && name != null) namesById.put(id, name);
        }
        return objects(binding.get("fkFillSources")).stream()
                .map(source -> {
                    String name = text(source.get("fieldName"));
                    return name != null ? name : namesById.get(text(source.get("fieldId")));
                })
                .filter(java.util.Objects::nonNull).distinct().toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> objects(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> list) || list.stream().anyMatch(x -> !(x instanceof Map<?, ?>))) {
            throw invalid("Pinned configuration list is invalid");
        }
        return (List<Map<String, Object>>) value;
    }

    private static String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim();
    }

    private static String normalizedName(Object value) {
        String name = text(value);
        return name == null ? null : name.toLowerCase(java.util.Locale.ROOT);
    }

    private static PortalException invalid(String message) {
        return new PortalException("409", message);
    }
}
