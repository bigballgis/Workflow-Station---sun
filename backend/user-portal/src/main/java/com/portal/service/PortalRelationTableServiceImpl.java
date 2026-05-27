package com.portal.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** Portal implementation for Relation Table access. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortalRelationTableServiceImpl implements PortalRelationTableService {

    private static final String DATA_ROWS_TABLE = "rt_table_data_rows";
    private static final Long SYSTEM_USER_TABLE_ID = -1_000_000_001L;
    private static final String SYSTEM_USER_TABLE_NAME = "sys_users";
    private static final List<String> SYSTEM_USER_FIELD_NAMES = List.of(
            "id", "username", "display_name", "full_name", "email", "employee_id", "status", "language");
    private static final List<String> DEFAULT_SYSTEM_USER_SEARCH_FIELDS = List.of(
            "username", "display_name", "full_name", "email", "employee_id");

    private final JdbcTemplate jdbcTemplate;
    private final RoleAccessComponent roleAccessComponent;
    private final ObjectMapper objectMapper;

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

            if (SYSTEM_USER_TABLE_ID.equals(tableId)) {
                return querySystemUserTableData(page, size, search);
            }

            if (!isDeployedRelationTable(tableId)) {
                return PageResponse.of(Collections.emptyList(), page, size, 0);
            }

            List<Object> searchParams = new ArrayList<>();
            String searchClause = buildJsonDataSearchClause(getFieldNames(tableId), search, searchParams);

            List<Object> countParams = new ArrayList<>();
            countParams.add(tableId);
            countParams.addAll(searchParams);
            Long total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + DATA_ROWS_TABLE + " WHERE table_id = ?" + searchClause,
                    Long.class, countParams.toArray());
            if (total == null) total = 0L;

            List<Object> dataParams = new ArrayList<>();
            dataParams.add(tableId);
            dataParams.addAll(searchParams);
            dataParams.add(size);
            dataParams.add(page * size);
            List<Map<String, Object>> rows = jdbcTemplate.query(
                    "SELECT data FROM " + DATA_ROWS_TABLE + " WHERE table_id = ?" + searchClause
                            + " ORDER BY id LIMIT ? OFFSET ?",
                    (rs, rowNum) -> parseJsonRow(rs.getString("data")),
                    dataParams.toArray());

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

        if (SYSTEM_USER_TABLE_ID.equals(tableId)) {
            return exportSystemUserCsv(userId, maxRows);
        }

        if (!isDeployedRelationTable(tableId)) {
            log.warn("Export CSV table not found: {}", tableId);
            return "";
        }

        List<String> fieldNames = getFieldNames(tableId);
        if (fieldNames.isEmpty()) {
            return "";
        }

        try {
            List<Map<String, Object>> rows = jdbcTemplate.query(
                    "SELECT data FROM " + DATA_ROWS_TABLE + " WHERE table_id = ? ORDER BY id LIMIT ?",
                    (rs, rowNum) -> parseJsonRow(rs.getString("data")),
                    tableId, maxRows);

            StringBuilder csv = new StringBuilder();
            csv.append(String.join(",", fieldNames)).append("\n");
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

    private String exportSystemUserCsv(String userId, int maxRows) {
        Set<String> userRoleIds = getUserRoleIds(userId);
        if (!hasAccess(SYSTEM_USER_TABLE_ID, userRoleIds)) {
            log.warn("Export CSV access denied for user {} on system user table", userId);
            return "";
        }
        String tableName = sanitizeIdentifier(SYSTEM_USER_TABLE_NAME);
        List<String> fieldNames = SYSTEM_USER_FIELD_NAMES.stream().map(this::sanitizeIdentifier).toList();
        try {
            String columns = String.join(", ", fieldNames);
            String sql = "SELECT " + columns + " FROM " + tableName + " LIMIT ?";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, maxRows);
            StringBuilder csv = new StringBuilder();
            csv.append(String.join(",", SYSTEM_USER_FIELD_NAMES)).append("\n");
            for (Map<String, Object> row : rows) {
                csv.append(SYSTEM_USER_FIELD_NAMES.stream()
                        .map(f -> escapeCsvValue(row.get(f)))
                        .collect(Collectors.joining(","))
                ).append("\n");
            }
            return csv.toString();
        } catch (Exception e) {
            log.warn("Failed to export system user CSV: {}", e.getMessage());
            return "";
        }
    }

    private PageResponse<Map<String, Object>> querySystemUserTableData(int page, int size, String search) {
        String tableName = sanitizeIdentifier(SYSTEM_USER_TABLE_NAME);
        List<String> fieldNames = SYSTEM_USER_FIELD_NAMES.stream().map(this::sanitizeIdentifier).toList();
        String columns = String.join(", ", fieldNames);

        List<Object> searchParams = new ArrayList<>();
        String searchClause = buildSystemUserSearchClause(search, searchParams);

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + searchClause,
                Long.class, searchParams.toArray());
        if (total == null) total = 0L;

        List<Object> dataParams = new ArrayList<>(searchParams);
        dataParams.add(size);
        dataParams.add(page * size);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT " + columns + " FROM " + tableName + searchClause + " LIMIT ? OFFSET ?",
                dataParams.toArray());
        return PageResponse.of(rows, page, size, total);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchForLookup(Long tableId, String keyword,
                                                      List<String> searchFields, String displayField,
                                                      String filterConditions,
                                                      int limit) {
        try {
            int safeLimit = normalizeLimit(limit);
            if (SYSTEM_USER_TABLE_ID.equals(tableId)) {
                List<LookupFilterCondition> filters = parseLookupFilterConditions(
                        filterConditions, SYSTEM_USER_FIELD_NAMES);
                return searchSystemUsersForLookup(keyword, searchFields, filters, safeLimit);
            }

            if (!isDeployedRelationTable(tableId)) {
                return Collections.emptyList();
            }

            List<String> allowedFields = getFieldNames(tableId);
            List<LookupFilterCondition> filters = parseLookupFilterConditions(filterConditions, allowedFields);
            List<String> predicates = new ArrayList<>();
            List<Object> params = new ArrayList<>();
            params.add(tableId);

            for (LookupFilterCondition filter : filters) {
                if (!allowedFields.contains(filter.fieldName())) {
                    continue;
                }
                predicates.add("data->>'" + sanitizeIdentifier(filter.fieldName()) + "' = ?");
                params.add(filter.value());
            }

            if (keyword != null && !keyword.isBlank() && searchFields != null && !searchFields.isEmpty()) {
                List<String> sanitizedFields = searchFields.stream()
                        .filter(allowedFields::contains)
                        .map(this::sanitizeIdentifier)
                        .toList();
                if (!sanitizedFields.isEmpty()) {
                    String keywordClause = sanitizedFields.stream()
                            .map(f -> "data->>'" + f + "' ILIKE ?")
                            .collect(Collectors.joining(" OR "));
                    predicates.add("(" + keywordClause + ")");
                    String likePattern = "%" + keyword + "%";
                    sanitizedFields.forEach(ignored -> params.add(likePattern));
                }
            }

            params.add(safeLimit);
            String sql = "SELECT data FROM " + DATA_ROWS_TABLE + " WHERE table_id = ?"
                    + (predicates.isEmpty() ? "" : " AND " + String.join(" AND ", predicates))
                    + " ORDER BY id LIMIT ?";
            return jdbcTemplate.query(sql, (rs, rowNum) -> parseJsonRow(rs.getString("data")), params.toArray());
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
            if (SYSTEM_USER_TABLE_ID.equals(tableId)) {
                return systemUserViewFields();
            }

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

    private List<Map<String, Object>> searchSystemUsersForLookup(String keyword,
                                                                 List<String> searchFields,
                                                                 List<LookupFilterCondition> filters,
                                                                 int limit) {
        String columns = SYSTEM_USER_FIELD_NAMES.stream()
                .map(this::sanitizeIdentifier)
                .collect(Collectors.joining(", "));

        List<String> predicates = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        predicates.add("deleted = false");
        predicates.add("status = 'ACTIVE'");

        for (LookupFilterCondition filter : filters) {
            predicates.add(sanitizeIdentifier(filter.fieldName()) + " = ?");
            params.add(filter.value());
        }

        if (keyword == null || keyword.isBlank()) {
            String sql = "SELECT " + columns + " FROM " + SYSTEM_USER_TABLE_NAME
                    + " WHERE " + String.join(" AND ", predicates)
                    + " ORDER BY username LIMIT ?";
            params.add(limit);
            return jdbcTemplate.queryForList(sql, params.toArray());
        }

        List<String> sanitizedFields = systemUserSearchFields(searchFields).stream()
                .map(this::sanitizeIdentifier)
                .toList();
        String whereClause = sanitizedFields.stream()
                .map(f -> f + " ILIKE ?")
                .collect(Collectors.joining(" OR "));

        String likePattern = "%" + keyword + "%";
        sanitizedFields.forEach(ignored -> params.add(likePattern));
        params.add(limit);

        String sql = "SELECT " + columns + " FROM " + SYSTEM_USER_TABLE_NAME
                + " WHERE " + String.join(" AND ", predicates) + " AND ("
                + whereClause + ") ORDER BY username LIMIT ?";
        return jdbcTemplate.queryForList(sql, params.toArray());
    }

    private List<LookupFilterCondition> parseLookupFilterConditions(String raw, List<String> allowedFields) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        try {
            Set<String> allowed = new HashSet<>(allowedFields);
            return objectMapper.readValue(raw, new TypeReference<List<LookupFilterCondition>>() {})
                    .stream()
                    .filter(condition -> condition.fieldName() != null
                            && condition.value() != null
                            && allowed.contains(condition.fieldName()))
                    .toList();
        } catch (Exception e) {
            log.warn("Ignoring invalid lookup filter conditions: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private record LookupFilterCondition(String fieldName, String value) {}

    private List<String> systemUserSearchFields(List<String> searchFields) {
        if (searchFields == null || searchFields.isEmpty()) {
            return DEFAULT_SYSTEM_USER_SEARCH_FIELDS;
        }
        List<String> allowedFields = searchFields.stream()
                .filter(SYSTEM_USER_FIELD_NAMES::contains)
                .distinct()
                .toList();
        return allowedFields.isEmpty() ? DEFAULT_SYSTEM_USER_SEARCH_FIELDS : allowedFields;
    }

    private List<Map<String, Object>> systemUserViewFields() {
        List<Map<String, Object>> fields = new ArrayList<>();
        for (int i = 0; i < SYSTEM_USER_FIELD_NAMES.size(); i++) {
            String fieldName = SYSTEM_USER_FIELD_NAMES.get(i);
            Map<String, Object> field = new HashMap<>();
            field.put("fieldName", fieldName);
            field.put("displayLabel", systemUserFieldLabel(fieldName));
            field.put("columnWidth", null);
            field.put("sortOrder", i + 1);
            field.put("visible", true);
            fields.add(field);
        }
        return fields;
    }

    private String systemUserFieldLabel(String fieldName) {
        return switch (fieldName) {
            case "id" -> "User ID";
            case "username" -> "Username";
            case "display_name" -> "Display Name";
            case "full_name" -> "Full Name";
            case "email" -> "Email";
            case "employee_id" -> "Employee ID";
            case "status" -> "Status";
            case "language" -> "Language";
            default -> fieldName;
        };
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) return 50;
        return Math.min(limit, 200);
    }

    private Set<String> getUserRoleIds(String userId) {
        // Roles assigned directly or via virtual groups
        List<Map<String, Object>> roles = roleAccessComponent.getUserBusinessRoles(userId);
        Set<String> roleIds = roles.stream()
                .map(r -> String.valueOf(r.getOrDefault("id", r.getOrDefault("roleId", ""))))
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toSet());

        // Also include roles the user holds through BU membership (sys_user_business_unit_roles).
        // The admin-center user-roles API may not include these when called without profileContext.
        try {
            List<String> buRoleIds = jdbcTemplate.queryForList(
                    "SELECT DISTINCT role_id FROM sys_user_business_unit_roles WHERE user_id = ?",
                    String.class, userId);
            roleIds.addAll(buRoleIds);
        } catch (Exception e) {
            log.warn("Failed to fetch BU role IDs for user {}: {}", userId, e.getMessage());
        }

        return roleIds;
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

    private boolean isDeployedRelationTable(Long tableId) {
        if (tableId == null || SYSTEM_USER_TABLE_ID.equals(tableId)) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rt_table_definitions WHERE id = ? AND status = ? AND enabled = true",
                Integer.class, tableId, RelationTableStatus.DEPLOYED.getCode());
        return count != null && count > 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonRow(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse relation table row JSON: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private List<String> getFieldNames(Long tableId) {
        if (SYSTEM_USER_TABLE_ID.equals(tableId)) {
            return SYSTEM_USER_FIELD_NAMES;
        }
        String sql = "SELECT field_name FROM rt_field_definitions WHERE table_id = ? ORDER BY sort_order ASC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("field_name"), tableId);
    }

    /**
     * @return SQL fragment like {@code  AND (data->>'f1' ILIKE ? OR ...)} or empty when search is blank
     */
    private String buildJsonDataSearchClause(List<String> fieldNames, String search, List<Object> outParams) {
        if (search == null || search.isBlank() || fieldNames == null || fieldNames.isEmpty()) {
            return "";
        }
        List<String> sanitizedFields = fieldNames.stream()
                .map(this::sanitizeIdentifier)
                .toList();
        if (sanitizedFields.isEmpty()) {
            return "";
        }
        String keywordClause = sanitizedFields.stream()
                .map(f -> "data->>'" + f + "' ILIKE ?")
                .collect(Collectors.joining(" OR "));
        String likePattern = "%" + search + "%";
        sanitizedFields.forEach(ignored -> outParams.add(likePattern));
        return " AND (" + keywordClause + ")";
    }

    private String buildSystemUserSearchClause(String search, List<Object> outParams) {
        if (search == null || search.isBlank()) {
            return "";
        }
        List<String> sanitizedFields = DEFAULT_SYSTEM_USER_SEARCH_FIELDS.stream()
                .map(this::sanitizeIdentifier)
                .toList();
        String keywordClause = sanitizedFields.stream()
                .map(f -> f + " ILIKE ?")
                .collect(Collectors.joining(" OR "));
        String likePattern = "%" + search + "%";
        sanitizedFields.forEach(ignored -> outParams.add(likePattern));
        return " WHERE (" + keywordClause + ")";
    }

    private String sanitizeIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
        }
        return identifier;
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
