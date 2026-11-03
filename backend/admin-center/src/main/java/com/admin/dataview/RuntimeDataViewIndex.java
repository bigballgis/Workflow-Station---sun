package com.admin.dataview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.functionunit.CatalogSnapshotReader;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Indexes immutable Admin snapshots. No designer reads, including during legacy recovery. */
@Component
@RequiredArgsConstructor
public class RuntimeDataViewIndex {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RuntimeExternalTableSnapshots externalTables;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void indexExistingReleases() {
        var used=jdbc.queryForList("SELECT fu.id FROM sys_function_units fu WHERE fu.enabled=TRUE "
                + "OR EXISTS(SELECT 1 FROM up_process_instance pi WHERE pi.function_unit_catalog_id=fu.id)",String.class);
        for (String id : jdbc.queryForList("SELECT id FROM sys_function_units ORDER BY id", String.class)) {
            // Upgrade freezes the dependencies the running releases currently use. Unused inactive
            // releases must be explicitly re-imported before activation if they lack dependency copies.
            if (used.contains(id)) externalTables.capture(id);
            indexDefinition(id);
        }
    }

    @Transactional
    public void index(String catalogId) {
        externalTables.capture(catalogId);
        indexDefinition(catalogId);
    }

    private void indexDefinition(String catalogId) {
        List<String> codes = jdbc.queryForList("SELECT code FROM sys_function_units WHERE id = ?", String.class, catalogId);
        if (codes.size() != 1) throw CatalogSnapshotReader.invalid(catalogId);
        String code = codes.get(0);
        Long fuId = jdbc.queryForObject("INSERT INTO ac_data_view_function_units(code) VALUES (?) "
                + "ON CONFLICT(code) DO UPDATE SET code=EXCLUDED.code RETURNING id", Long.class, code);
        List<Map<String, Object>> tables = jdbc.queryForList("SELECT content_data FROM sys_function_unit_contents "
                + "WHERE function_unit_id=? AND content_type='DATA_TABLE' ORDER BY id", String.class, catalogId)
                .stream().map(json -> parseMap(json, catalogId)).toList();
        List<String> payloads = jdbc.queryForList("SELECT content_data FROM sys_function_unit_contents "
                + "WHERE function_unit_id=? AND content_type='MAIN_TABLE_VIEW' ORDER BY id", String.class, catalogId);
        if (payloads.size() > 1) throw CatalogSnapshotReader.invalid(catalogId);
        var releaseForms=jdbc.queryForList("SELECT content_data FROM sys_function_unit_contents "
                + "WHERE function_unit_id=? AND content_type='FORM' ORDER BY id",String.class,catalogId)
                .stream().map(value -> parseMap(value,catalogId)).toList();
        HashSet<String> keys = new HashSet<>();
        for (String payload : payloads) {
            List<Map<String, Object>> views;
            try {
                views = mapper.readValue(payload, new com.fasterxml.jackson.core.type.TypeReference<>() { });
            } catch (java.io.IOException ex) { throw CatalogSnapshotReader.invalid(catalogId); }
            int ordinal = 0;
            for (Map<String, Object> view : views) {
                Object keyValue = view.get("viewKey");
                // FALLBACK(migration): old immutable releases lack viewKey. Their address is release-local;
                // never infer continuity or transfer BI grants to a later same-named view.
                String key = keyValue == null ? UUID.nameUUIDFromBytes((catalogId + ":" + ordinal)
                        .getBytes(StandardCharsets.UTF_8)).toString() : UUID.fromString(keyValue.toString()).toString();
                ordinal++;
                if (!keys.add(key)) throw CatalogSnapshotReader.invalid(catalogId);
                String tableName = required(view, "mainTableName", catalogId);
                List<Map<String, Object>> matches = tables.stream()
                        .filter(t -> tableName.equals(t.get("tableName")))
                        .filter(t -> "MAIN".equals(t.get("tableType")) || "SUB".equals(t.get("tableType"))).toList();
                if (matches.size() != 1) throw CatalogSnapshotReader.invalid(catalogId);
                Map<String, Object> table = matches.get(0);
                Long tableId = jdbc.queryForObject("INSERT INTO ac_data_view_tables(function_unit_id,table_name) VALUES (?,?) "
                        + "ON CONFLICT(function_unit_id,table_name) DO UPDATE SET table_name=EXCLUDED.table_name RETURNING id",
                        Long.class, fuId, tableName);
                Long viewId = jdbc.queryForObject("INSERT INTO ac_data_view_identities(function_unit_id,view_key) VALUES (?,?) "
                        + "ON CONFLICT(function_unit_id,view_key) DO UPDATE SET view_key=EXCLUDED.view_key RETURNING id",
                        Long.class, fuId, key);
                if(view.get("detailFormName") instanceof String formName && "SUB".equals(table.get("tableType"))) {
                    var forms=releaseForms.stream().filter(form -> "DETAIL".equals(form.get("formType")) && formName.equals(form.get("formName"))).toList();
                    if(forms.size()!=1 || forms.get(0).get("formId")==null) throw CatalogSnapshotReader.invalid(catalogId);
                    view.put("detailFormId",forms.get(0).get("formId"));
                }
                String json;
                try { json = mapper.writeValueAsString(view); }
                catch (java.io.IOException ex) { throw CatalogSnapshotReader.invalid(catalogId); }
                jdbc.update("INSERT INTO ac_data_view_releases(view_id,catalog_id,table_id,table_name,table_display_name,"
                                + "table_type,view_name,definition) VALUES (?,?,?,?,?,?,?,CAST(? AS JSONB)) "
                                + "ON CONFLICT(view_id,catalog_id) DO NOTHING", viewId, catalogId, tableId, tableName,
                        table.get("tableDisplayName"), table.get("tableType"), required(view, "viewName", catalogId), json);
                // Exact lineage captured once by migration, never inferred from display names.
                jdbc.update("UPDATE bi_data_view_assignment a SET function_unit_id=?,table_id=?,view_id=?,runtime_target=TRUE "
                        + "WHERE NOT a.runtime_target AND a.legacy_target->>'viewKey'=? "
                        + "AND a.legacy_target->>'functionUnitCode'=? AND NOT EXISTS (SELECT 1 FROM bi_data_view_assignment b "
                        + "WHERE b.runtime_target AND b.dashboard_id=a.dashboard_id AND b.view_id=?)",
                        fuId,tableId,viewId,key,code,viewId);
            }
        }
    }

    private Map<String, Object> parseMap(String json, String catalogId) {
        try { return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() { }); }
        catch (java.io.IOException ex) { throw CatalogSnapshotReader.invalid(catalogId); }
    }

    private String required(Map<String, Object> map, String key, String catalogId) {
        Object value = map.get(key);
        if (!(value instanceof String text) || text.isBlank()) throw CatalogSnapshotReader.invalid(catalogId);
        return text;
    }
}
