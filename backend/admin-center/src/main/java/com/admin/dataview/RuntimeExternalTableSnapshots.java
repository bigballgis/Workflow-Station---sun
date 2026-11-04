package com.admin.dataview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.functionunit.CatalogSnapshotReader;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

/** Captures the exact Admin metadata used at import; runtime never follows another FU's pointer. */
@Component
@RequiredArgsConstructor
public class RuntimeExternalTableSnapshots {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Transactional
    public void capture(String catalogId) {
        var tables = jdbc.queryForList("SELECT content_data FROM sys_function_unit_contents "
                + "WHERE function_unit_id=? AND content_type='DATA_TABLE' ORDER BY id",String.class,catalogId);
        for (String json : tables) {
            for (var field : CatalogSnapshotReader.objects(parse(json,catalogId).get("fields"))) {
                if (!(field.get("refFunctionUnitCode") instanceof String code)) continue;
                if (!(field.get("refTableName") instanceof String name) || code.isBlank() || name.isBlank()) {
                    throw CatalogSnapshotReader.invalid(catalogId);
                }
                Long existing = jdbc.queryForObject("SELECT COUNT(*) FROM ac_external_table_snapshots "
                        + "WHERE catalog_id=? AND function_unit_code=? AND table_name=?",Long.class,catalogId,code,name);
                if (existing != null && existing == 1) continue;
                var parents = jdbc.queryForList("SELECT c.function_unit_id,c.content_data FROM sys_function_unit_contents c "
                        + "JOIN sys_function_units fu ON fu.id=c.function_unit_id "
                        + "WHERE fu.code=? AND fu.status='DEPLOYED' AND fu.enabled=TRUE AND c.content_type='DATA_TABLE' "
                        + "AND c.content_data::jsonb->>'tableName'=?",code,name);
                if (parents.size()!=1) throw CatalogSnapshotReader.invalid(catalogId);
                var parent=parents.get(0);
                Map<String,Object> definition=parse(String.valueOf(parent.get("content_data")),catalogId);
                if (definition.get("tableId")==null) throw CatalogSnapshotReader.invalid(catalogId);
                jdbc.update("INSERT INTO ac_external_table_snapshots(catalog_id,function_unit_code,table_name,source_catalog_id,definition) "
                        + "VALUES (?,?,?,?,CAST(? AS JSONB)) ON CONFLICT DO NOTHING",catalogId,code,name,
                        parent.get("function_unit_id"),parent.get("content_data"));
            }
        }
    }

    private Map<String,Object> parse(String json,String catalogId) {
        try { return mapper.readValue(json,new com.fasterxml.jackson.core.type.TypeReference<>() { }); }
        catch (java.io.IOException ex) { throw CatalogSnapshotReader.invalid(catalogId); }
    }
}
