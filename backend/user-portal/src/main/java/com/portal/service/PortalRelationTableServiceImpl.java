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

        // Query portal-visible, deployed, enabled tables
        String sql = "SELECT t.id, t.table_name, t.display_name, t.description, t.status, "
                + "t.enabled, t.portal_visible, t.current_version "
                + "FROM rt_table_definitions t "
                + "WHERE t.status = ? AND t.enabled = true AND t.portal_visible = true";
        List<RelationTableDTO> allVisible = jdbcTemplate.query(sql, (rs, rowNum) ->
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

        if (userRoleIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Filter by access permissions
        return allVisible.stream()
                .filter(table -> hasAccess(table.getId(), userRoleIds))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> queryTableData(Long tableId, String userId,
                                                             int page, int size, String search) {
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
    }

    @Override
    @Transactional(readOnly = true)
    public String exportCsv(Long tableId, String userId, int maxRows) {
        Set<String> userRoleIds = getUserRoleIds(userId);
        if (!hasAccess(tableId, userRoleIds)) {
            throw new IllegalArgumentException("Access denied");
        }

        String tableName = getPhysicalTableName(tableId);
        if (tableName == null) {
            throw new IllegalArgumentException("Table not found");
        }

        List<String> fieldNames = getFieldNames(tableId);
        if (fieldNames.isEmpty()) {
            return "";
        }

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
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchForLookup(Long tableId, String keyword,
                                                      List<String> searchFields, String displayField,
                                                      int limit) {
        String tableName = getPhysicalTableName(tableId);
        if (tableName == null || searchFields == null || searchFields.isEmpty()) {
            return Collections.emptyList();
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
        return names.isEmpty() ? null : "rt_data_" + names.get(0);
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
