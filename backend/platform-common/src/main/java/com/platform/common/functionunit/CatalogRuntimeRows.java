package com.platform.common.functionunit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Durable Portal rows reference release metadata, never a replaceable DW table definition. */
@Component
@RequiredArgsConstructor
public class CatalogRuntimeRows {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CatalogSnapshotReader snapshots;

    @Transactional
    public long tableId(String catalogId, String sourceId) {
        Map<String, Object> definition = snapshots.table(catalogId, sourceId);
        jdbc.update("INSERT INTO up_function_unit_runtime_tables(catalog_id,source_table_id,definition) "
                        + "VALUES (?, ?, ?::jsonb) ON CONFLICT(catalog_id,source_table_id) DO NOTHING",
                catalogId, sourceId, json(definition));
        return jdbc.queryForObject("SELECT id FROM up_function_unit_runtime_tables "
                + "WHERE catalog_id=? AND source_table_id=?", Long.class, catalogId, sourceId);
    }

    @Transactional
    public void insert(String catalogId, String sourceId, String processId, Map<String, Object> data,
                       String actor) {
        if (processId == null || processId.isBlank()) throw new FunctionUnitVersionException("VERSION_RECOVERY_REQUIRED", processId);
        jdbc.update("INSERT INTO dw_table_data_rows(runtime_table_id,process_instance_id,row_id,data,created_by,updated_by) "
                        + "VALUES (?, ?, ?, ?::jsonb, ?, ?)",
                tableId(catalogId, sourceId), processId, UUID.randomUUID().toString(), json(data), actor, actor);
    }

    @Transactional
    public List<Map<String, Object>> rows(String catalogId, String sourceId, String processId) {
        migrateForProcess(catalogId, sourceId, processId);
        return jdbc.queryForList("SELECT r.data::text AS data FROM dw_table_data_rows r "
                        + "JOIN up_function_unit_runtime_tables t ON t.id=r.runtime_table_id "
                        + "WHERE t.catalog_id=? AND t.source_table_id=? AND r.process_instance_id=? "
                        + "AND r.status='ACTIVE' ORDER BY r.created_at,r.id", String.class,
                catalogId, sourceId, processId).stream().map(snapshots::parse).toList();
    }

    /** Exact snapshot bindings and persisted request ids are the only legacy association evidence. */
    @Transactional
    public void preserveDesignerTable(long designerTableId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,data::text AS data FROM dw_table_data_rows WHERE table_id=? FOR UPDATE", designerTableId);
        for (Map<String, Object> row : rows) {
            List<Association> matches = associations(String.valueOf(designerTableId), snapshots.parse(String.valueOf(row.get("data"))));
            if (matches.size() != 1) throw new FunctionUnitVersionException("VERSION_ROW_RECOVERY_REQUIRED", String.valueOf(row.get("id")));
            move(row.get("id"), String.valueOf(designerTableId), matches.get(0));
        }
    }

    @Transactional
    public void preserveDesignerFunctionUnit(long functionUnitId) {
        for (Long tableId : jdbc.queryForList("SELECT id FROM dw_table_definitions WHERE function_unit_id=?", Long.class, functionUnitId)) {
            preserveDesignerTable(tableId);
        }
    }

    private void migrateForProcess(String catalogId, String sourceId, String processId) {
        List<String> requestIds = jdbc.queryForList("SELECT variables->>'__request_id' FROM up_process_instance WHERE id=?", String.class, processId);
        if (requestIds.isEmpty() || requestIds.get(0) == null) return; // New rows already carry process identity.
        java.util.Set<Object> seen = new java.util.HashSet<>();
        for (var binding : snapshots.bindings(catalogId)) {
            if (!"ACTION".equals(binding.get("bindingType")) || !sourceId.equals(String.valueOf(binding.get("tableId")))
                    || !(binding.get("foreignKeyField") instanceof String fk)) continue;
            for (var row : jdbc.queryForList("SELECT id,data::text AS data FROM dw_table_data_rows "
                    + "WHERE table_id=? AND data->>? = ? FOR UPDATE", Long.valueOf(sourceId), fk, requestIds.get(0))) {
                if (!seen.add(row.get("id"))) continue;
                List<Association> matches = associations(sourceId, snapshots.parse(String.valueOf(row.get("data"))));
                if (matches.size() != 1 || !matches.get(0).catalogId().equals(catalogId)
                        || !matches.get(0).processId().equals(processId)) {
                    throw new FunctionUnitVersionException("VERSION_ROW_RECOVERY_REQUIRED", String.valueOf(row.get("id")));
                }
                move(row.get("id"), sourceId, matches.get(0));
            }
        }
    }

    private record Association(String catalogId, String processId) { }

    private List<Association> associations(String sourceId, Map<String, Object> data) {
        return jdbc.query("""
                SELECT DISTINCT pi.function_unit_catalog_id, pi.id
                FROM sys_function_unit_contents c
                CROSS JOIN LATERAL jsonb_array_elements(
                    CASE WHEN jsonb_typeof(c.content_data::jsonb->'tableBindings')='array'
                    THEN c.content_data::jsonb->'tableBindings' ELSE '[]'::jsonb END) binding
                JOIN sys_function_unit_contents t ON t.function_unit_id=c.function_unit_id
                    AND t.content_type='DATA_TABLE'
                    AND t.content_data::jsonb->>'tableName'=binding->>'tableName'
                    AND t.content_data::jsonb->>'tableType'<>'RELATION'
                JOIN up_process_instance pi ON pi.function_unit_catalog_id=c.function_unit_id
                WHERE c.content_type='FORM' AND binding->>'bindingType'='ACTION'
                  AND t.content_data::jsonb->>'tableId'=?
                  AND pi.variables->>'__request_id' = ?::jsonb ->> (binding->>'foreignKeyField')
                """, (rs, index) -> new Association(rs.getString(1), rs.getString(2)), sourceId, json(data));
    }

    private void move(Object rowId, String sourceId, Association association) {
        jdbc.update("UPDATE dw_table_data_rows SET runtime_table_id=?,process_instance_id=?,table_id=NULL "
                        + "WHERE id=? AND table_id::text=?",
                tableId(association.catalogId(), sourceId), association.processId(), rowId, sourceId);
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw CatalogSnapshotReader.invalid(null); }
    }
}
