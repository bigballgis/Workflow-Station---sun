package com.developer.component.impl;

import com.developer.exception.DeveloperBusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.relationtable.RelationTableStructureDiff;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Relation-table (rt_*) structure export/import for function-unit packages.
 *
 * <p>The rt_ structure (rt_table_definitions / rt_field_definitions) is owned by admin-center;
 * developer-workstation only references it by {@code relationTableId} on RELATED form bindings.
 * Since DW has no JPA mapping for these tables, this collaborator reads/writes them via JDBC
 * (same approach as {@code FormTableBindingLoader}).
 *
 * <p>Export: serialize each referenced relation table by {@code table_name} + its fields.
 * Import: upsert by {@code table_name} — absent ⇒ create as {@code INIT} (version 1);
 * present ⇒ replace fields, and only if the incoming structure actually differs from what's
 * stored, set {@code UPDATED} with {@code current_version + 1} (a no-op re-import leaves status/
 * version untouched). Returns a {@code table_name → new rt id} map so the caller can remap
 * RELATED bindings' relationTableId.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RelationTableStructurePortability {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /** Serialize the given relation table ids to a list of name-keyed structure maps. Unknown ids are skipped. */
    public List<Map<String, Object>> exportByIds(List<Long> relationTableIds) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (relationTableIds == null || relationTableIds.isEmpty()) {
            return out;
        }
        for (Long id : relationTableIds.stream().filter(java.util.Objects::nonNull).distinct().toList()) {
            Map<String, Object> table = exportOne(id);
            if (table != null) {
                out.add(table);
            }
        }
        return out;
    }

    private Map<String, Object> exportOne(Long id) {
        List<Map<String, Object>> defs = jdbcTemplate.query(
                "SELECT id, table_name, display_name, description FROM rt_table_definitions WHERE id = ?",
                (rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    // source id retained so the importer can remap RELATED bindings' relationTableId (old → new)
                    m.put("relationTableId", rs.getObject("id", Long.class));
                    m.put("tableName", rs.getString("table_name"));
                    m.put("displayName", rs.getString("display_name"));
                    m.put("description", rs.getString("description"));
                    return m;
                }, id);
        if (defs.isEmpty()) {
            log.warn("Relation table id {} referenced by a binding was not found; skipping from export", id);
            return null;
        }
        Map<String, Object> table = defs.get(0);
        table.put("fields", jdbcTemplate.query(
                "SELECT field_name, data_type, length, precision_value, scale, nullable, is_primary_key, "
                        + "default_value, display_name, is_foreign_key, ref_table_id, ref_primary_key_fields, "
                        + "pk_generation_json, fk_display_mode, lookup_config, sort_order, is_computed, "
                        + "computed_field_json "
                        + "FROM rt_field_definitions WHERE table_id = ? ORDER BY sort_order ASC, id ASC",
                (rs, n) -> {
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("fieldName", rs.getString("field_name"));
                    f.put("dataType", rs.getString("data_type"));
                    f.put("length", rs.getObject("length", Integer.class));
                    f.put("precision", rs.getObject("precision_value", Integer.class));
                    f.put("scale", rs.getObject("scale", Integer.class));
                    f.put("nullable", rs.getObject("nullable", Boolean.class));
                    f.put("isPrimaryKey", rs.getObject("is_primary_key", Boolean.class));
                    f.put("defaultValue", rs.getString("default_value"));
                    f.put("displayName", rs.getString("display_name"));
                    f.put("isForeignKey", rs.getObject("is_foreign_key", Boolean.class));
                    // ref_table_id is exported by name so it survives id remap on import
                    f.put("refTableName", refTableName(rs.getObject("ref_table_id", Long.class)));
                    f.put("refPrimaryKeyFields", rs.getString("ref_primary_key_fields"));
                    f.put("pkGenerationJson", rs.getString("pk_generation_json"));
                    f.put("fkDisplayMode", rs.getString("fk_display_mode"));
                    f.put("lookupConfig", rs.getString("lookup_config"));
                    f.put("sortOrder", rs.getObject("sort_order", Integer.class));
                    boolean computed = Boolean.TRUE.equals(rs.getObject("is_computed", Boolean.class));
                    String formulaJson = rs.getString("computed_field_json");
                    if (computed && (formulaJson == null || formulaJson.isBlank())) {
                        throw new DeveloperBusinessException("COMPUTED_FIELD_EXPORT_INVALID",
                                "Field '" + rs.getString("field_name")
                                        + "' is marked computed but has no usable formula JSON");
                    }
                    f.put("isComputed", computed);
                    f.put("computedField", computed ? formulaJson : null);
                    return f;
                }, id));
        return table;
    }

    private String refTableName(Long refTableId) {
        if (refTableId == null) {
            return null;
        }
        List<String> names = jdbcTemplate.query(
                "SELECT table_name FROM rt_table_definitions WHERE id = ?",
                (rs, n) -> rs.getString("table_name"), refTableId);
        return names.isEmpty() ? null : names.get(0);
    }

    /**
     * Upsert all relation-table structures from a package.
     *
     * @param relationTables list of name-keyed structure maps (as produced by {@link #exportByIds})
     * @param operator       audit user
     * @return table_name → rt_table_definitions.id for every imported/updated table
     */
    public Map<String, Long> importAll(List<Map<String, Object>> relationTables, String operator) {
        Map<String, Long> nameToId = new LinkedHashMap<>();
        if (relationTables == null || relationTables.isEmpty()) {
            return nameToId;
        }
        // Pass 1: upsert table definitions + fields (ref_table_id resolved in pass 2 once all ids exist).
        for (Map<String, Object> table : relationTables) {
            String tableName = (String) table.get("tableName");
            if (tableName == null || tableName.isBlank()) {
                continue;
            }
            Long id = upsertDefinition(table, operator);
            replaceFields(id, table, operator);
            nameToId.put(tableName, id);
        }
        // Pass 2: resolve each field's refTableName → ref_table_id now that all tables exist.
        for (Map<String, Object> table : relationTables) {
            String tableName = (String) table.get("tableName");
            Long id = nameToId.get(tableName);
            if (id != null) {
                resolveFieldRefs(id, table, nameToId);
            }
        }
        return nameToId;
    }

    private Long upsertDefinition(Map<String, Object> table, String operator) {
        String tableName = (String) table.get("tableName");
        String displayName = (String) table.get("displayName");
        String description = (String) table.get("description");
        Timestamp now = Timestamp.from(Instant.now());

        List<Long> existing = jdbcTemplate.query(
                "SELECT id FROM rt_table_definitions WHERE table_name = ?",
                (rs, n) -> rs.getLong("id"), tableName);

        if (existing.isEmpty()) {
            // Absent → create as INIT, version 1 (per requirement: a new import bumps version 0 → 1).
            return jdbcTemplate.queryForObject(
                    "INSERT INTO rt_table_definitions "
                            + "(table_name, display_name, deployed_display_name, description, status, enabled, "
                            + " portal_visible, current_version, created_at, created_by, updated_at, updated_by) "
                            + "VALUES (?, ?, ?, ?, 'INIT', true, false, 1, ?, ?, ?, ?) RETURNING id",
                    Long.class, tableName, displayName, displayName, description, now, operator, now, operator);
        }
        // Present → compare against current structure before touching anything; only a real change
        // should flip status to UPDATED and bump current_version (re-importing identical content
        // must be a no-op for status/version, otherwise every re-deploy round-trip looks like a change).
        Long id = existing.get(0);
        Map<String, Object> current = jdbcTemplate.queryForMap(
                "SELECT display_name, description FROM rt_table_definitions WHERE id = ?", id);
        boolean unchanged = RelationTableStructureDiff.unchanged(
                (String) current.get("display_name"), (String) current.get("description"), currentFieldMaps(id),
                displayName, description, incomingFieldMaps(table));

        if (unchanged) {
            jdbcTemplate.update(
                    "UPDATE rt_table_definitions SET display_name = ?, description = ?, updated_at = ?, updated_by = ? WHERE id = ?",
                    displayName, description, now, operator, id);
        } else {
            jdbcTemplate.update(
                    "UPDATE rt_table_definitions SET display_name = ?, description = ?, status = 'UPDATED', "
                            + "current_version = COALESCE(current_version, 0) + 1, updated_at = ?, updated_by = ? WHERE id = ?",
                    displayName, description, now, operator, id);
        }
        return id;
    }

    /** Normalizes the currently-stored fields into the shape {@link RelationTableStructureDiff} compares. */
    private List<Map<String, Object>> currentFieldMaps(Long tableId) {
        return jdbcTemplate.query(
                "SELECT f.field_name, f.data_type, f.length, f.precision_value, f.scale, f.nullable, "
                        + " f.is_primary_key, f.default_value, f.display_name, f.is_foreign_key, "
                        + " (SELECT table_name FROM rt_table_definitions WHERE id = f.ref_table_id) AS ref_table_name, "
                        + " f.ref_primary_key_fields, f.pk_generation_json, f.fk_display_mode, "
                        + " f.lookup_config, f.sort_order, f.is_computed, f.computed_field_json "
                        + "FROM rt_field_definitions f WHERE f.table_id = ?",
                (rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("fieldName", rs.getString("field_name"));
                    m.put("dataType", rs.getString("data_type"));
                    m.put("length", rs.getObject("length", Integer.class));
                    m.put("precision", rs.getObject("precision_value", Integer.class));
                    m.put("scale", rs.getObject("scale", Integer.class));
                    m.put("nullable", rs.getObject("nullable", Boolean.class));
                    m.put("isPrimaryKey", rs.getObject("is_primary_key", Boolean.class));
                    m.put("defaultValue", rs.getString("default_value"));
                    m.put("displayName", rs.getString("display_name"));
                    m.put("isForeignKey", rs.getObject("is_foreign_key", Boolean.class));
                    m.put("refTableName", rs.getString("ref_table_name"));
                    m.put("refPrimaryKeyFields", parseJsonList(rs.getString("ref_primary_key_fields")));
                    m.put("pkGenerationJson", parseJsonMapText(rs.getString("pk_generation_json")));
                    m.put("fkDisplayMode", rs.getString("fk_display_mode"));
                    m.put("lookupConfig", isLookup(rs.getString("data_type"))
                            ? parseJsonMapText(rs.getString("lookup_config")) : null);
                    m.put("sortOrder", rs.getObject("sort_order", Integer.class));
                    boolean computed = Boolean.TRUE.equals(rs.getObject("is_computed", Boolean.class));
                    m.put("isComputed", computed);
                    m.put("computedField", computed ? parseJsonMapText(rs.getString("computed_field_json")) : null);
                    return m;
                }, tableId);
    }

    /** Normalizes the import payload's field list into the shape {@link RelationTableStructureDiff} compares. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> incomingFieldMaps(Map<String, Object> table) {
        List<Map<String, Object>> result = new ArrayList<>();
        Object fieldsObj = table.get("fields");
        if (!(fieldsObj instanceof List<?> fields)) {
            return result;
        }
        int order = 0;
        for (Object fo : fields) {
            if (!(fo instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> f = (Map<String, Object>) raw;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fieldName", f.get("fieldName"));
            m.put("dataType", f.get("dataType"));
            m.put("length", asInt(f.get("length")));
            m.put("precision", asInt(f.get("precision")));
            m.put("scale", asInt(f.get("scale")));
            m.put("nullable", asBool(f.get("nullable"), true));
            m.put("isPrimaryKey", asBool(f.get("isPrimaryKey"), false));
            m.put("defaultValue", f.get("defaultValue"));
            m.put("displayName", f.get("displayName"));
            m.put("isForeignKey", asBool(f.get("isForeignKey"), false));
            m.put("refTableName", f.get("refTableName"));
            m.put("refPrimaryKeyFields", parseJsonListValue(f.get("refPrimaryKeyFields")));
            m.put("pkGenerationJson", parseJsonMapValue(f.get("pkGenerationJson")));
            m.put("fkDisplayMode", f.get("fkDisplayMode") != null ? f.get("fkDisplayMode") : "readonly");
            m.put("lookupConfig", isLookup(f.get("dataType")) ? parseJsonMapValue(f.get("lookupConfig")) : null);
            // Same positional fallback replaceFields() uses, so a payload that omits sortOrder does
            // not read as a change against the value that would actually be persisted.
            m.put("sortOrder", f.get("sortOrder") instanceof Number num ? num.intValue() : order);
            boolean computed = Boolean.TRUE.equals(f.get("isComputed"));
            m.put("isComputed", computed);
            m.put("computedField", computed ? parseJsonMapValue(f.get("computedField")) : null);
            result.add(m);
            order++;
        }
        return result;
    }

    /** LOOKUP config is only meaningful on a LOOKUP column; anywhere else it must normalize to null. */
    private boolean isLookup(Object dataType) {
        return dataType instanceof String s && "LOOKUP".equals(s);
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse JSON list '{}': {}", json, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> parseJsonMapText(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse JSON map '{}': {}", json, e.getMessage());
            return null;
        }
    }

    private Object parseJsonListValue(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof List<?>) {
            return v;
        }
        if (v instanceof String s) {
            return parseJsonList(s);
        }
        return v;
    }

    private Object parseJsonMapValue(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Map<?, ?>) {
            return v;
        }
        if (v instanceof String s) {
            return parseJsonMapText(s);
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    private void replaceFields(Long tableId, Map<String, Object> table, String operator) {
        jdbcTemplate.update("DELETE FROM rt_field_definitions WHERE table_id = ?", tableId);
        Object fieldsObj = table.get("fields");
        if (!(fieldsObj instanceof List<?> fields)) {
            return;
        }
        int order = 0;
        for (Object fo : fields) {
            if (!(fo instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> f = (Map<String, Object>) raw;
            Integer sortOrder = f.get("sortOrder") instanceof Number num ? num.intValue() : order;
            boolean computed = Boolean.TRUE.equals(f.get("isComputed"));
            jdbcTemplate.update(
                    "INSERT INTO rt_field_definitions "
                            + "(table_id, field_name, data_type, length, precision_value, scale, nullable, "
                            + " is_primary_key, default_value, display_name, is_foreign_key, ref_table_id, "
                            + " ref_primary_key_fields, pk_generation_json, fk_display_mode, lookup_config, "
                            + " sort_order, is_computed, computed_field_json) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?, ?, ?::jsonb)",
                    tableId,
                    f.get("fieldName"),
                    f.get("dataType"),
                    asInt(f.get("length")),
                    asInt(f.get("precision")),
                    asInt(f.get("scale")),
                    asBool(f.get("nullable"), true),
                    asBool(f.get("isPrimaryKey"), false),
                    f.get("defaultValue"),
                    f.get("displayName"),
                    asBool(f.get("isForeignKey"), false),
                    asJsonText(f.get("refPrimaryKeyFields")),
                    asJsonText(f.get("pkGenerationJson")),
                    f.get("fkDisplayMode") != null ? f.get("fkDisplayMode") : "readonly",
                    isLookup(f.get("dataType")) ? asJsonText(f.get("lookupConfig")) : null,
                    sortOrder,
                    computed,
                    computed ? requireJsonText(f.get("computedField"), tableId, f.get("fieldName")) : null);
            order++;
        }
    }

    @SuppressWarnings("unchecked")
    private void resolveFieldRefs(Long tableId, Map<String, Object> table, Map<String, Long> nameToId) {
        Object fieldsObj = table.get("fields");
        if (!(fieldsObj instanceof List<?> fields)) {
            return;
        }
        for (Object fo : fields) {
            if (!(fo instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> f = (Map<String, Object>) raw;
            String refName = (String) f.get("refTableName");
            if (refName == null) {
                continue;
            }
            Long refId = nameToId.get(refName);
            if (refId == null) {
                // Ref points outside this package; try resolving against existing rt tables.
                List<Long> found = jdbcTemplate.query(
                        "SELECT id FROM rt_table_definitions WHERE table_name = ?",
                        (rs, n) -> rs.getLong("id"), refName);
                refId = found.isEmpty() ? null : found.get(0);
            }
            if (refId != null) {
                jdbcTemplate.update(
                        "UPDATE rt_field_definitions SET ref_table_id = ? WHERE table_id = ? AND field_name = ?",
                        refId, tableId, f.get("fieldName"));
            } else {
                log.warn("Relation field {}.{} references unknown table '{}'; ref_table_id left null",
                        tableId, f.get("fieldName"), refName);
            }
        }
    }

    private Integer asInt(Object v) {
        return v instanceof Number num ? num.intValue() : null;
    }

    private Boolean asBool(Object v, boolean dflt) {
        return v instanceof Boolean b ? b : dflt;
    }

    private String requireJsonText(Object v, Long tableId, Object fieldName) {
        String text = asJsonText(v);
        if (text == null) {
            throw new DeveloperBusinessException("COMPUTED_FIELD_IMPORT_INVALID",
                    "Computed field '" + fieldName + "' on relation table " + tableId
                            + " is marked computed but has no usable formula JSON");
        }
        return text;
    }

    /** Normalize a value that may already be a JSON string or a parsed list/map into JSON text (or null). */
    private String asJsonText(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof String s) {
            return s.isBlank() ? null : s;
        }
        try {
            return objectMapper.writeValueAsString(v);
        } catch (Exception e) {
            log.warn("Failed to serialize relation field json value: {}", e.getMessage());
            return null;
        }
    }
}
