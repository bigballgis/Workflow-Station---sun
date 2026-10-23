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
    /** Whether a release predates runtime versioning, per release. */
    private final Map<String, Boolean> legacyReleases = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(32, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Boolean> entry) {
                    return size() > 512;
                }
            });
    /** Relation tables of pre-runtime-versioning releases, per release (immutable once published). */
    private final Map<String, List<Map<String, Object>>> legacyRelationTables = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(16, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, List<Map<String, Object>>> entry) {
                    return size() > 128;
                }
            });
    /** Time zone the platform's JPA layer records local timestamps (such as rt_table_versions.deployed_at) in. */
    @org.springframework.beans.factory.annotation.Value("${spring.jpa.properties.hibernate.jdbc.time_zone:UTC}")
    private String recordedTimeZone = "UTC";
    /** Row-key fields per (release, table name); releases are immutable, so the answer never changes. */
    private final Map<Key, List<String>> primaryKeys = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(32, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Key, List<String>> entry) {
                    return size() > 512;
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

    /** A form's bindings as its release defines them; reads of a legacy release also get its relation tables. */
    public List<Map<String, Object>> formBindings(String catalogId, Map<String, Object> form) {
        return formBindings(catalogId, form, true);
    }

    /**
     * A form's bindings from the release's own snapshot only — what activation and new requests rely on: a
     * release new requests start on is complete by itself, with no help from the relation-table history.
     */
    public List<Map<String, Object>> completeFormBindings(String catalogId, Map<String, Object> form) {
        return formBindings(catalogId, form, false);
    }

    private List<Map<String, Object>> formBindings(String catalogId, Map<String, Object> form,
                                                   boolean readLegacyRelationTables) {
        java.util.ArrayList<Map<String, Object>> tables = new java.util.ArrayList<>(contents(catalogId, "DATA_TABLE"));
        boolean legacyConsulted = !readLegacyRelationTables;
        java.util.ArrayList<Map<String, Object>> result = new java.util.ArrayList<>();
        for (var raw : objects(form.get("tableBindings"))) {
            boolean related = "RELATED".equals(raw.get("bindingType"));
            if (related && sameId(raw.get("relationTableId"), SYSTEM_USERS_RELATION_TABLE_ID)) {
                result.add(systemUsersBinding(raw));
                continue;
            }
            var matches = tables.stream().filter(t -> boundTable(t, raw, related)).toList();
            if (matches.isEmpty() && related && !legacyConsulted) {
                legacyConsulted = true;
                addLegacyRelationTables(catalogId, tables);
                matches = tables.stream().filter(t -> boundTable(t, raw, related)).toList();
            }
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
                // DW exports RELATED bindings without a name; give them the table's, as the live loader did.
                binding.putIfAbsent("tableName", table.get("tableName"));
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
                    var parents = parentsOf(tables, field);
                    if (parents.isEmpty() && !legacyConsulted) {
                        legacyConsulted = true;
                        addLegacyRelationTables(catalogId, tables);
                        parents = parentsOf(tables, field);
                    }
                    if (parents.size() != 1) throw invalid(catalogId);
                    Object parentId = parents.get(0).get("tableId");
                    if (parentId == null) parentId = parents.get(0).get("relationTableId");
                    definition.put("refTableId", parentId);
                    if (java.util.Objects.equals(raw.get("filterFkFieldName"), field.get("fieldName"))) binding.put("filterFkRefTableId", parentId);
                }
                definitions.add(definition);
            }
            binding.put("primaryKeyFields", rowKeyFields(fields));
            binding.put("fieldDefinitions", definitions);
            binding.put("columns", fields);
            binding.put("data", List.of());
            result.add(binding);
        }
        return result;
    }

    private static List<Map<String, Object>> parentsOf(List<Map<String, Object>> tables, Map<String, Object> field) {
        return tables.stream().filter(t -> java.util.Objects.equals(t.get("tableName"), field.get("refTableName"))).toList();
    }

    /**
     * Releases imported before relation tables were written into snapshots (no content checksum and no
     * RELATION table) keep their relation tables only in Admin Center's relation-table version history:
     * reads of requests on them see each relation table as deployed when the release was published — the
     * structure the runtime read live back then. Asked only when a binding or foreign key misses its table.
     */
    private void addLegacyRelationTables(String catalogId, List<Map<String, Object>> tables) {
        if (tables.stream().anyMatch(t -> "RELATION".equals(t.get("tableType"))) || !legacyRelease(catalogId)) return;
        // FALLBACK(migration): pre-runtime-versioning releases never stored their relation tables; read-only, no data
        // repair. Remove once no request is pinned to a release listed by the ledger #1711 query.
        tables.addAll(copy(legacyRelationTables.computeIfAbsent(catalogId, this::loadLegacyRelationTables)));
    }

    /** No content checksum = imported before runtime versioning; fixed once a release is deployed or pinned. */
    private boolean legacyRelease(String catalogId) {
        return legacyReleases.computeIfAbsent(catalogId, id -> {
            List<Boolean> legacy = jdbc.queryForList(
                    "SELECT content_checksum IS NULL FROM sys_function_units WHERE id = ?", Boolean.class, id);
            return legacy.size() == 1 && Boolean.TRUE.equals(legacy.get(0));
        });
    }

    private List<Map<String, Object>> loadLegacyRelationTables(String catalogId) {
        java.util.Deque<Object> pending = new java.util.ArrayDeque<>();
        var snapshotTables = contents(catalogId, "DATA_TABLE");
        java.util.Set<String> snapshotNames = new java.util.HashSet<>();
        for (var table : snapshotTables) snapshotNames.add(String.valueOf(table.get("tableName")));
        for (var table : snapshotTables) {
            if (!(table.get("fields") instanceof List<?>)) continue;
            for (var field : objects(table.get("fields"))) {
                Object ref = field.get("refTableName");
                if (ref != null && !snapshotNames.contains(String.valueOf(ref))) pending.add(ref);
            }
        }
        for (var form : contents(catalogId, "FORM")) {
            for (var raw : objects(form.get("tableBindings"))) {
                if (!"RELATED".equals(raw.get("bindingType"))
                        || sameId(raw.get("relationTableId"), SYSTEM_USERS_RELATION_TABLE_ID)) continue;
                Object ref = raw.get("relationTableId") != null ? raw.get("relationTableId") : raw.get("tableName");
                if (ref != null) pending.add(ref);
            }
        }
        Map<String, Map<String, Object>> byId = new java.util.LinkedHashMap<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        while (!pending.isEmpty()) {
            Object ref = pending.poll();
            if (!seen.add(String.valueOf(ref).toLowerCase(java.util.Locale.ROOT))) continue;
            Map<String, Object> table = relationTableAtRelease(catalogId, ref);
            if (table == null || byId.containsKey(String.valueOf(table.get("relationTableId")))) continue;
            byId.put(String.valueOf(table.get("relationTableId")), table);
            for (var field : objects(table.get("fields"))) {
                if (field.get("refTableName") != null) pending.add(field.get("refTableName"));
            }
        }
        return List.copyOf(byId.values());
    }

    /**
     * One relation table (by id or name) in the snapshot shape packages give it, at the version deployed when
     * the release was published; null when Admin Center has no such version. {@code deployed_at} is recorded
     * in the platform's JDBC time zone.
     */
    private Map<String, Object> relationTableAtRelease(String catalogId, Object ref) {
        boolean byId = ref instanceof Number || String.valueOf(ref).matches("-?\\d+");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT d.id, d.table_name, "
                + "COALESCE(d.deployed_display_name, d.display_name) AS display_name, d.description, v.snapshot_data "
                + "FROM rt_table_definitions d JOIN rt_table_versions v ON v.table_id = d.id "
                + "WHERE " + (byId ? "d.id = CAST(? AS BIGINT)" : "lower(d.table_name) = lower(?)")
                + " AND (v.deployed_at AT TIME ZONE ?) <= (SELECT f.created_at FROM sys_function_units f WHERE f.id = ?) "
                + "ORDER BY v.deployed_at DESC, v.version_number DESC LIMIT 1",
                String.valueOf(ref), recordedTimeZone, catalogId);
        if (rows.size() != 1) return null;
        var row = rows.get(0);
        java.util.ArrayList<Map<String, Object>> fields = new java.util.ArrayList<>();
        for (var recorded : recordedFields(row.get("snapshot_data"), catalogId)) {
            Map<String, Object> field = new java.util.LinkedHashMap<>(recorded);
            field.remove("id");
            Object pk = field.remove("pkGeneration");
            field.put("pkGenerationJson", pk == null ? null : json(pk));
            Object refTableId = field.remove("refTableId");
            field.put("refTableName", refTableId == null ? null : relationTableName(refTableId));
            fields.add(field);
        }
        Map<String, Object> table = new java.util.LinkedHashMap<>();
        table.put("relationTableId", ((Number) row.get("id")).longValue());
        table.put("tableName", row.get("table_name"));
        table.put("displayName", row.get("display_name"));
        table.put("description", row.get("description"));
        table.put("tableType", "RELATION");
        table.put("fields", fields);
        return table;
    }

    private List<Map<String, Object>> recordedFields(Object snapshotData, String catalogId) {
        try {
            return mapper.readValue(String.valueOf(snapshotData), new TypeReference<List<Map<String, Object>>>() { });
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw invalid(catalogId);
        }
    }

    private String relationTableName(Object relationTableId) {
        List<String> names = jdbc.queryForList("SELECT table_name FROM rt_table_definitions WHERE id = CAST(? AS BIGINT)",
                String.class, String.valueOf(relationTableId));
        return names.size() == 1 ? names.get(0) : null;
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Relation table version is not serialisable", e);
        }
    }

    /**
     * The platform's built-in Users relation (sys_users). It is virtual: there is no rt_ table row, so
     * no package carries its structure and the snapshot has no table for it.
     */
    public static final long SYSTEM_USERS_RELATION_TABLE_ID = -1_000_000_001L;

    /**
     * A binding names its table. A RELATED binding exported by Developer Workstation carries only its
     * relationTableId, so without a name it is matched by that id (as the live binding loader joined rt_).
     */
    private static boolean boundTable(Map<String, Object> table, Map<String, Object> raw, boolean related) {
        if (related != "RELATION".equals(table.get("tableType"))) return false;
        if (raw.get("tableName") != null || !related) return java.util.Objects.equals(table.get("tableName"), raw.get("tableName"));
        return sameId(table.get("relationTableId"), raw.get("relationTableId"));
    }

    private static boolean sameId(Object a, Object b) {
        return a != null && b != null && String.valueOf(a).trim().equals(String.valueOf(b).trim());
    }

    /** Same shape the live binding loader gave it: a RELATION binding with no table row and no fields. */
    private static Map<String, Object> systemUsersBinding(Map<String, Object> raw) {
        Map<String, Object> binding = new java.util.LinkedHashMap<>(raw);
        binding.put("tableId", null);
        binding.put("tableType", "RELATION");
        binding.put("fieldDefinitions", List.of());
        binding.put("columns", List.of());
        binding.put("data", List.of());
        return binding;
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

    /**
     * The release's own data table of this name (case-insensitive); relation tables are not its data tables.
     * Empty when the release has no such table.
     */
    public java.util.Optional<Map<String, Object>> dataTable(String catalogId, String tableName) {
        requireCatalog(catalogId);
        if (tableName == null) return java.util.Optional.empty();
        var matches = contents(catalogId, "DATA_TABLE").stream()
                .filter(t -> !"RELATION".equals(t.get("tableType")))
                .filter(t -> tableName.equalsIgnoreCase(String.valueOf(t.get("tableName")))).toList();
        if (matches.size() > 1) throw invalid(catalogId);
        return matches.stream().findFirst();
    }

    /**
     * The fields a sub-table's rows are keyed by in the release, in field order — the same key its
     * bindings carry ({@link #formBindings}).
     *
     * @throws FunctionUnitVersionException VERSION_SNAPSHOT_INCOMPLETE when the release has no such data table
     */
    public List<String> primaryKeyFields(String catalogId, String tableName) {
        requireCatalog(catalogId);
        Key key = new Key(catalogId, tableName);
        List<String> cached = primaryKeys.get(key);
        if (cached != null) return cached;
        List<String> keys = rowKeyFields(objects(dataTable(catalogId, tableName)
                .orElseThrow(() -> invalid(catalogId)).get("fields")));
        primaryKeys.put(key, keys);
        return keys;
    }

    /** A table's primary-key fields in field order, else the platform row id. */
    private static List<String> rowKeyFields(List<Map<String, Object>> fields) {
        var keys = fields.stream().filter(f -> Boolean.TRUE.equals(f.get("isPrimaryKey")))
                .map(f -> String.valueOf(f.get("fieldName"))).toList();
        return keys.isEmpty() ? List.of(com.platform.common.jdbc.SubTableRowIdentity.CANONICAL_FIELD) : keys;
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
