package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.PkGenerationConfig;
import com.platform.common.fk.PrimaryKeyAllocationService;
import com.portal.exception.PortalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Server-side backstop for Table Design auto-PK strategies in process variables.
 * All users share {@code dw_pk_sequences} (perTable scope) — not per-user counters.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessSubTablePrimaryKeyEnricherComponent {

    /** Slice map key used both at the top level of variables and on a row that hosts nested sub-tables. */
    private static final String NESTED_SUB_TABLES_KEY = "__subTables__";

    private record AutoPkField(String fieldName, PkGenerationConfig config) {}

    private final JdbcTemplate jdbcTemplate;
    private final PrimaryKeyAllocationService primaryKeyAllocationService;
    private final ObjectMapper objectMapper;
    private final PortalPrimaryKeyAllocationComponent portalPrimaryKeyAllocationComponent;

    /**
     * Walk {@code variables.__subTables__} and allocate missing auto-generated PK values
     * before process start or task form persist.
     */
    @SuppressWarnings("unchecked")
    public void allocateMissingPrimaryKeysInVariables(String functionUnitIdOrCode, Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return;
        }
        Object subTablesObj = variables.get(NESTED_SUB_TABLES_KEY);
        if (!(subTablesObj instanceof Map<?, ?>)) {
            return;
        }
        Long functionUnitId = resolveFunctionUnitIdOrNull(functionUnitIdOrCode);
        if (functionUnitId == null) {
            log.debug("Skip sub-table PK enrich: function unit not resolved for {}", functionUnitIdOrCode);
            return;
        }

        Map<String, Long> sliceKeyToTableId = loadSubTableSliceKeyToTableId(functionUnitId);
        Map<Long, List<AutoPkField>> autoPkByTable = loadAutoPrimaryKeyFields(functionUnitId);
        if (sliceKeyToTableId.isEmpty() || autoPkByTable.isEmpty()) {
            return;
        }

        Map<String, Object> subTables = (Map<String, Object>) subTablesObj;
        int allocated = enrichSubTableMap(subTables, sliceKeyToTableId, autoPkByTable);
        if (allocated > 0) {
            log.info("Allocated {} missing sub-table PK value(s) for functionUnit={}", allocated, functionUnitIdOrCode);
        }
    }

    private Long resolveFunctionUnitIdOrNull(String functionUnitIdOrCode) {
        try {
            return portalPrimaryKeyAllocationComponent.resolveFunctionUnitIdForAllocation(functionUnitIdOrCode);
        } catch (PortalException e) {
            return null;
        }
    }

    /**
     * Enrich one {@code __subTables__} map. Rows of a sub-table may themselves carry a nested
     * {@code __subTables__} map (sub-table inside a sub-table); those grandchild rows are real
     * rows of their own table and need their auto PKs just as much, so recurse into them.
     */
    private int enrichSubTableMap(Map<String, Object> subTables,
                                  Map<String, Long> sliceKeyToTableId,
                                  Map<Long, List<AutoPkField>> autoPkByTable) {
        int allocated = 0;
        for (Map.Entry<String, Object> entry : subTables.entrySet()) {
            Long tableId = sliceKeyToTableId.get(entry.getKey());
            if (tableId == null) {
                tableId = sliceKeyToTableId.get(entry.getKey().toLowerCase(Locale.ROOT));
            }
            // Unmapped keys are display-name aliases holding duplicate copies of a mapped slice's
            // rows. Walking them too would allocate a second, different key for every logical row.
            if (tableId == null) {
                continue;
            }
            // A mapped slice with no auto PK of its own is still walked — its rows may host
            // nested slices that do have one.
            List<AutoPkField> fields = autoPkByTable.get(tableId);
            allocated += enrichRows(entry.getValue(), tableId, fields, sliceKeyToTableId, autoPkByTable);
        }
        return allocated;
    }

    @SuppressWarnings("unchecked")
    private int enrichRows(Object rowsObj,
                           Long tableId,
                           List<AutoPkField> fields,
                           Map<String, Long> sliceKeyToTableId,
                           Map<Long, List<AutoPkField>> autoPkByTable) {
        if (!(rowsObj instanceof List<?> list)) {
            return 0;
        }
        int allocated = 0;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> row = (Map<String, Object>) item;
            if (fields != null) {
                for (AutoPkField pk : fields) {
                    if (!isBlank(row.get(pk.fieldName()))) {
                        continue;
                    }
                    List<String> values = primaryKeyAllocationService.allocate(
                            tableId, pk.fieldName(), pk.config(), 1, "");
                    if (values != null && !values.isEmpty()) {
                        row.put(pk.fieldName(), values.get(0));
                        allocated++;
                    }
                }
            }
            if (row.get(NESTED_SUB_TABLES_KEY) instanceof Map<?, ?> nested) {
                allocated += enrichSubTableMap(
                        (Map<String, Object>) nested, sliceKeyToTableId, autoPkByTable);
            }
        }
        return allocated;
    }

    private Map<String, Long> loadSubTableSliceKeyToTableId(Long functionUnitId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                SELECT ftb.id AS binding_id, ftb.table_id, td.table_name
                FROM dw_form_table_bindings ftb
                INNER JOIN dw_form_definitions fd ON fd.id = ftb.form_id
                INNER JOIN dw_table_definitions td ON td.id = ftb.table_id
                WHERE fd.function_unit_id = ? AND ftb.table_id IS NOT NULL
                """,
                (rs, rowNum) -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("bindingId", rs.getLong("binding_id"));
                    m.put("tableId", rs.getLong("table_id"));
                    m.put("tableName", rs.getString("table_name"));
                    return m;
                },
                functionUnitId);
        Map<String, Long> out = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long bindingId = (Long) row.get("bindingId");
            Long tableId = (Long) row.get("tableId");
            String tableName = (String) row.get("tableName");
            out.put(String.valueOf(bindingId), tableId);
            if (tableName != null && !tableName.isBlank()) {
                out.putIfAbsent(tableName, tableId);
                out.putIfAbsent(tableName.toLowerCase(Locale.ROOT), tableId);
            }
        }
        return out;
    }

    private Map<Long, List<AutoPkField>> loadAutoPrimaryKeyFields(Long functionUnitId) {
        return jdbcTemplate.query(
                """
                SELECT fd.table_id, fd.field_name, fd.pk_generation_json::text AS json
                FROM dw_field_definitions fd
                INNER JOIN dw_table_definitions td ON td.id = fd.table_id
                WHERE td.function_unit_id = ? AND COALESCE(fd.is_primary_key, false) = true
                """,
                rs -> {
                    Map<Long, List<AutoPkField>> map = new HashMap<>();
                    while (rs.next()) {
                        long tableId = rs.getLong("table_id");
                        String fieldName = rs.getString("field_name");
                        PkGenerationConfig config = toPkConfig(parseJsonMap(rs.getString("json")));
                        if ("manual".equalsIgnoreCase(config.getStrategy())) {
                            continue;
                        }
                        map.computeIfAbsent(tableId, k -> new ArrayList<>())
                                .add(new AutoPkField(fieldName, config));
                    }
                    return map;
                },
                functionUnitId);
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private PkGenerationConfig toPkConfig(Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            return PkGenerationConfig.builder().strategy("uuid").build();
        }
        return objectMapper.convertValue(json, PkGenerationConfig.class);
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).trim().isEmpty();
    }
}
