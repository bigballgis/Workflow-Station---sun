package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.dto.AllocatePrimaryKeyRequest;
import com.portal.dto.AllocatePrimaryKeyResponse;
import com.portal.exception.PortalException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.platform.common.dto.PkGenerationConfig;
import com.platform.common.fk.PrimaryKeyAllocationService;

import java.util.List;
import java.util.Map;

/**
 * Portal PK allocation for sub-table add-row (PRD S5). Validates table belongs to the Function Unit.
 */
@Component
@RequiredArgsConstructor
public class PortalPrimaryKeyAllocationComponent {

    private final JdbcTemplate jdbcTemplate;
    private final PrimaryKeyAllocationService primaryKeyAllocationService;
    private final ObjectMapper objectMapper;
    private final FunctionUnitAccessComponent functionUnitAccessComponent;

    @Transactional
    public AllocatePrimaryKeyResponse allocate(String functionUnitIdOrCode, AllocatePrimaryKeyRequest request) {
        Long functionUnitId = resolveFunctionUnitId(functionUnitIdOrCode);
        Long tableId = request.getTableId();
        assertTableBelongsToFunctionUnit(tableId, functionUnitId);

        Map<String, Object> pkJson = loadPkGenerationJson(tableId, request.getFieldName());
        PkGenerationConfig config = toPkConfig(pkJson);
        int count = request.getCount() != null && request.getCount() > 0 ? request.getCount() : 1;
        String scopeKey = request.getScopeKey() != null ? request.getScopeKey() : String.valueOf(functionUnitId);
        List<String> values = primaryKeyAllocationService.allocate(
                tableId, request.getFieldName(), config, count, scopeKey);
        return AllocatePrimaryKeyResponse.builder().values(values).build();
    }

    private Long resolveFunctionUnitId(String functionUnitIdOrCode) {
        Long direct = resolveDwFunctionUnitIdDirect(functionUnitIdOrCode);
        if (direct != null) {
            return direct;
        }
        // Task/My Request views pass Flowable processDefinitionKey; map catalog id -> dw_function_units.id
        String catalogId = functionUnitAccessComponent.resolveFunctionUnitId(functionUnitIdOrCode);
        Long viaCatalog = resolveDwFunctionUnitIdFromCatalog(catalogId);
        if (viaCatalog != null) {
            return viaCatalog;
        }
        throw new PortalException("FUNCTION_UNIT_NOT_FOUND", "Function unit not found");
    }

    private Long resolveDwFunctionUnitIdDirect(String functionUnitIdOrCode) {
        Long byId = tryParseLong(functionUnitIdOrCode);
        if (byId != null) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM dw_function_units WHERE id = ?",
                    Integer.class,
                    byId);
            if (exists != null && exists > 0) {
                return byId;
            }
        }
        return jdbcTemplate.query(
                "SELECT id FROM dw_function_units WHERE code = ? LIMIT 1",
                rs -> rs.next() ? rs.getLong("id") : null,
                functionUnitIdOrCode);
    }

    private Long resolveDwFunctionUnitIdFromCatalog(String catalogId) {
        if (catalogId == null || catalogId.isBlank()) {
            return null;
        }
        return jdbcTemplate.query(
                """
                SELECT dw.id
                FROM dw_function_units dw
                INNER JOIN sys_function_units sys ON sys.code = dw.code
                WHERE sys.id = ?
                ORDER BY CASE WHEN COALESCE(sys.enabled, false) THEN 0 ELSE 1 END,
                         CASE WHEN COALESCE(dw.enabled, false) THEN 0 ELSE 1 END,
                         dw.id DESC
                LIMIT 1
                """,
                rs -> rs.next() ? rs.getLong("id") : null,
                catalogId);
    }

    private void assertTableBelongsToFunctionUnit(Long tableId, Long functionUnitId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dw_table_definitions WHERE id = ? AND function_unit_id = ?",
                Integer.class,
                tableId,
                functionUnitId);
        if (count == null || count == 0) {
            throw new PortalException("FORBIDDEN", "Table does not belong to Function Unit");
        }
    }

    private Map<String, Object> loadPkGenerationJson(Long tableId, String fieldName) {
        record PkFieldRow(boolean primaryKey, String json) {}
        PkFieldRow row = jdbcTemplate.query(
                """
                SELECT COALESCE(is_primary_key, false) AS is_pk, pk_generation_json::text AS json
                FROM dw_field_definitions
                WHERE table_id = ? AND field_name = ?
                LIMIT 1
                """,
                rs -> rs.next() ? new PkFieldRow(rs.getBoolean("is_pk"), rs.getString("json")) : null,
                tableId,
                fieldName);
        if (row == null || !row.primaryKey()) {
            throw new PortalException("NOT_PK_FIELD", "Primary key field not found: " + fieldName);
        }
        if (row.json() == null || row.json().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(row.json(), Map.class);
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

    private static Long tryParseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
