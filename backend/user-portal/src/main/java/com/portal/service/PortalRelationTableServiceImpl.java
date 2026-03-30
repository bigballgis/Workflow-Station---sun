package com.portal.service;

import com.platform.common.dto.RelationTableDTO;
import com.platform.common.enums.RelationTableStatus;
import com.portal.component.RoleAccessComponent;
import com.portal.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Portal Relation Table 服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortalRelationTableServiceImpl implements PortalRelationTableService {

    private final JdbcTemplate jdbcTemplate;
    private final RoleAccessComponent roleAccessComponent;

    @Override
    @Transactional(readOnly = true)
    public List<RelationTableDTO> getVisibleTables(String userId) {
        // Get user's business role IDs
        Set<String> userRoleIds = getUserRoleIds(userId);

        List<RelationTableDTO> allVisible;
        try {
            // Query portal-visible, deployed, enabled tables
            String sql = "SELECT t.id, t.table_name, t.display_name, t.description, t.status, "
                    + "t.enabled, t.portal_visible, t.current_version "
                    + "FROM rt_table_definitions t "
                    + "WHERE t.status = ? AND t.enabled = true AND t.portal_visible = true";
            allVisible = jdbcTemplate.query(sql, (rs, rowNum) ->
                    RelationTableDTO.builder()
                            .id(rs.getLong("id"))
                            .tableName(rs.getString("table_name"))
                            .displayName(rs.getString("display_name"))
                            .description(rs.getString("description"))
                            .status(RelationTableStatus.fromCode(rs.getString("status")))
                            .enabled(rs.getBoolean("enabled"))
                            .portalVisible(rs.getBoolean("portal_visible"))
                            .currentVersion(rs.getInt("current_version"))
                            .build(),
                    RelationTableStatus.DEPLOYED.getCode());
        } catch (Exception e) {
            log.warn("Failed to query rt_table_definitions (table may not exist yet): {}", e.getMessage());
            return Collections.emptyList();
        }

        if (userRoleIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Filter by access permissions
        return allVisible.stream()
                .filter(table -> {
                    try {
                        return hasAccess(table.getId(), userRoleIds);
                    } catch (Exception e) {
                        log.warn("Failed to check access for table {}: {}", table.getId(), e.getMessage());
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> queryTableData(Long tableId, String userId,
                                                             int page, int size, String search) {
        try {
            // Verify access
            Set<String> userRoleIds = getUserRoleIds(userId);
            if (!hasAccess(tableId, userRoleIds)) {
                return PageResponse.of(Collections.emptyList(), page, size, 0);
            }

            // Get physical table name
            String tableName = getPhysicalTableName(tableId);
            if (tableName == null) {
                return PageResponse.of(Collections.emptyList(), page, size, 0);
            }

            // Get field names for the table
            List<String> fieldNames = getFieldNames(tableId);
            if (fieldNames.isEmpty()) {
                return PageResponse.of(Collections.emptyList(), page, size, 0);
            }

            String columns = String.join(", ", fieldNames);

            // Count total
            String countSql = "SELECT COUNT(*) FROM " + tableName;
            Long total = jdbcTemplate.queryForObject(countSql, Long.class);
            if (total == null) total = 0L;

            // Query data with pagination
            String dataSql = "SELECT " + columns + " FROM " + tableName
                    + " LIMIT ? OFFSET ?";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(dataSql, size, page * size);

            return PageResponse.of(rows, page, size, total);
        } catch (Exception e) {
            log.warn("Failed to query table data for tableId {}: {}", tableId, e.getMessage());
            return PageResponse.of(Collections.emptyList(), page, size, 0);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String exportCsv(Long tableId, String userId, int maxRows) {
        Set<String> userRoleIds = getUserRoleIds(userId);
        if (!hasAccess(tableId, userRoleIds)) {
            log.warn("Export CSV access denied for user {} on table {}", userId, tableId);
            return "";
        }

        String tableName = getPhysicalTableName(tableId);
        if (tableName == null) {
            log.warn("Export CSV table not found: {}", tableId);
            return "";
        }

        List<String> fieldNames = getFieldNames(tableId);
        if (fieldNames.isEmpty()) {
            return "";
        }

        try {
            String columns = String.join(", ", fieldNames);
            String sql = "SELECT " + columns + " FROM " + tableName + " LIMIT ?";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, maxRows);

            StringBuilder csv = new StringBuilder();
            // Header
            csv.append(String.join(",", fieldNames)).append("\n");
            // Data rows
            for (Map<String, Object> row : rows) {
                csv.append(fieldNames.stream()
                        .map(f -> escapeCsvValue(row.get(f)))
                        .collect(Collectors.joining(","))
                ).append("\n");
            }
            return csv.toString();
        } catch (Exception e) {
            log.warn("Failed to export CSV for tableId {}: {}", tableId, e.getMessage());
            return "";
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchForLookup(Long tableId, String keyword,
                                                      List<String> searchFields, String displayField,
                                                      int limit) {
        try {
            String tableName = getPhysicalTableName(tableId);
            if (tableName == null) {
                return Collections.emptyList();
            }

            // If keyword is empty or no search fields configured, return all rows (up to limit)
            if (keyword == null || keyword.isBlank() || searchFields == null || searchFields.isEmpty()) {
                String sql = "SELECT * FROM " + tableName + " LIMIT ?";
                return jdbcTemplate.queryForList(sql, limit);
            }

            // Build WHERE clause with ILIKE for each search field
            String whereClause = searchFields.stream()
                    .map(f -> f + " ILIKE ?")
                    .collect(Collectors.joining(" OR "));

            String likePattern = "%" + keyword + "%";
            Object[] params = new Object[searchFields.size() + 1];
            Arrays.fill(params, 0, searchFields.size(), likePattern);
            params[searchFields.size()] = limit;

            String sql = "SELECT * FROM " + tableName
                    + " WHERE " + whereClause + " LIMIT ?";
            return jdbcTemplate.queryForList(sql, params);
        } catch (Exception e) {
            log.warn("Failed to search for lookup in tableId {}: {}", tableId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getLookupConfigs(Long formId) {
        try {
            String sql = "SELECT lc.component_id, lc.table_id, lc.search_fields, lc.display_field, lc.view_config_id "
                    + "FROM rt_lookup_configs lc WHERE lc.form_id = ?";
            List<Map<String, Object>> configs = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> lc = new java.util.HashMap<>();
                lc.put("componentId", rs.getString("component_id"));
                lc.put("tableId", rs.getLong("table_id"));
                lc.put("searchFields", rs.getString("search_fields"));
                lc.put("displayField", rs.getString("display_field"));
                lc.put("viewConfigId", rs.getObject("view_config_id"));
                return lc;
            }, formId);

            // Load view fields for each config that has a viewConfigId
            for (Map<String, Object> config : configs) {
                Object vcId = config.get("viewConfigId");
                // If no viewConfigId in lookup config, try to find view config by tableId
                if (vcId == null) {
                    Object tableId = config.get("tableId");
                    if (tableId != null) {
                        try {
                            vcId = jdbcTemplate.queryForObject(
                                "SELECT id FROM rt_view_configs WHERE table_id = ? ORDER BY id DESC LIMIT 1",
                                Long.class, ((Number) tableId).longValue());
                        } catch (Exception ignored) {}
                    }
                }
                if (vcId != null) {
                    try {
                        String vfSql = "SELECT field_name, display_label, column_width, sort_order, visible "
                                + "FROM rt_view_fields WHERE view_config_id = ? ORDER BY sort_order";
                        List<Map<String, Object>> viewFields = jdbcTemplate.query(vfSql, (rs, rowNum) -> {
                            Map<String, Object> vf = new java.util.HashMap<>();
                            vf.put("fieldName", rs.getString("field_name"));
                            vf.put("displayLabel", rs.getString("display_label"));
                            vf.put("columnWidth", rs.getObject("column_width"));
                            vf.put("sortOrder", rs.getInt("sort_order"));
                            vf.put("visible", rs.getBoolean("visible"));
                            return vf;
                        }, ((Number) vcId).longValue());
                        config.put("viewFields", viewFields);
                    } catch (Exception e) {
                        log.warn("Failed to load view fields for viewConfigId {}: {}", vcId, e.getMessage());
                        config.put("viewFields", Collections.emptyList());
                    }
                } else {
                    config.put("viewFields", Collections.emptyList());
                }
            }
            return configs;
        } catch (Exception e) {
            log.warn("Failed to load lookup configs for formId {}: {}", formId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getViewFieldsByTableId(Long tableId) {
        try {
            // First try rt_view_fields (configured view)
            String sql = "SELECT vf.field_name, vf.display_label, vf.column_width, vf.sort_order, vf.visible "
                    + "FROM rt_view_fields vf "
                    + "JOIN rt_view_configs vc ON vc.id = vf.view_config_id "
                    + "WHERE vc.table_id = ? ORDER BY vf.sort_order";
            List<Map<String, Object>> result = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> vf = new java.util.HashMap<>();
                vf.put("fieldName", rs.getString("field_name"));
                vf.put("displayLabel", rs.getString("display_label"));
                vf.put("columnWidth", rs.getObject("column_width"));
                vf.put("sortOrder", rs.getInt("sort_order"));
                vf.put("visible", rs.getBoolean("visible"));
                return vf;
            }, tableId);

            if (!result.isEmpty()) return result;

            // Fallback: use rt_field_definitions (table field definitions)
            String fallbackSql = "SELECT field_name, comment, sort_order FROM rt_field_definitions WHERE table_id = ? ORDER BY sort_order ASC";
            return jdbcTemplate.query(fallbackSql, (rs, rowNum) -> {
                Map<String, Object> vf = new java.util.HashMap<>();
                vf.put("fieldName", rs.getString("field_name"));
                vf.put("displayLabel", rs.getString("comment") != null ? rs.getString("comment") : rs.getString("field_name"));
                vf.put("columnWidth", null);
                vf.put("sortOrder", rs.getInt("sort_order"));
                vf.put("visible", true);
                return vf;
            }, tableId);
        } catch (Exception e) {
            log.warn("Failed to load view fields for tableId {}: {}", tableId, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== Helper methods ====================

    private Set<String> getUserRoleIds(String userId) {
        List<Map<String, Object>> roles = roleAccessComponent.getUserBusinessRoles(userId);
        return roles.stream()
                .map(r -> String.valueOf(r.getOrDefault("id", r.getOrDefault("roleId", ""))))
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toSet());
    }

    private boolean hasAccess(Long tableId, Set<String> userRoleIds) {
        if (userRoleIds.isEmpty()) return false;
        String sql = "SELECT COUNT(*) FROM rt_table_access WHERE table_id = ? AND target_type = 'ROLE' AND target_id IN ("
                + userRoleIds.stream().map(id -> "?").collect(Collectors.joining(",")) + ")";
        List<Object> params = new ArrayList<>();
        params.add(tableId);
        params.addAll(userRoleIds);
        Long count = jdbcTemplate.queryForObject(sql, Long.class, params.toArray());
        return count != null && count > 0;
    }

    private String getPhysicalTableName(Long tableId) {
        String sql = "SELECT table_name FROM rt_table_definitions WHERE id = ? AND status = ?";
        List<String> names = jdbcTemplate.query(sql,
                (rs, rowNum) -> rs.getString("table_name"),
                tableId, RelationTableStatus.DEPLOYED.getCode());
        return names.isEmpty() ? null : names.get(0);
    }

    private List<String> getFieldNames(Long tableId) {
        String sql = "SELECT field_name FROM rt_field_definitions WHERE table_id = ? ORDER BY sort_order ASC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("field_name"), tableId);
    }

    private String escapeCsvValue(Object value) {
        if (value == null) return "";
        String str = value.toString();
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }
}
