package com.admin.service.impl;

import com.admin.config.DatabaseSchemaResolver;
import com.admin.dto.response.RelationTableResponse;
import com.admin.entity.RelationFieldDefinition;
import com.admin.entity.RelationTableDefinition;
import com.admin.entity.RelationTableVersion;
import com.admin.exception.RelationTableNotFoundException;
import com.admin.repository.RelationTableDefinitionRepository;
import com.admin.repository.RelationTableVersionRepository;
import com.admin.service.RelationTableAuditService;
import com.admin.service.RelationTableDataService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.RelationFieldDTO;
import com.platform.common.dto.RelationTableDataRowDTO;
import com.platform.common.enums.RelationTableStatus;
import com.platform.security.util.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Relation Table 表数据管理服务实现
 * 使用 JdbcTemplate 对用户定义的物理表执行动态 SQL 查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelationTableDataServiceImpl implements RelationTableDataService {

    private final RelationTableDefinitionRepository tableDefinitionRepository;
    private final RelationTableVersionRepository versionRepository;
    private final RelationTableAuditService auditService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DatabaseSchemaResolver schemaResolver;

    @Override
    @Transactional(readOnly = true)
    public List<RelationTableResponse> getDeployedTables() {
        return tableDefinitionRepository.findByStatusInAndEnabledTrue(
                        List.of(RelationTableStatus.DEPLOYED, RelationTableStatus.UPDATED, RelationTableStatus.ROLLBACK)).stream()
                .map(this::toDeployedTableResponse)
                .collect(Collectors.toList());
    }

    /**
     * 将表定义转换为响应 DTO。
     * 对于 UPDATED / ROLLBACK 状态的表，字段列表取最新已部署版本的快照（与物理表一致），
     * 而非 rt_field_definitions 中可能包含尚未部署的新字段。
     */
    private RelationTableResponse toDeployedTableResponse(RelationTableDefinition entity) {
        if (entity.getStatus() == RelationTableStatus.UPDATED
                || entity.getStatus() == RelationTableStatus.ROLLBACK) {
            Optional<RelationTableVersion> latestVersion =
                    versionRepository.findLatestVersion(entity.getId());
            if (latestVersion.isPresent()) {
                try {
                    List<RelationFieldDTO> snapshotFields =
                            parseSnapshotData(latestVersion.get().getSnapshotData());
                    if (!snapshotFields.isEmpty()) {
                        return buildResponseWithSnapshotFields(entity, snapshotFields);
                    }
                } catch (Exception e) {
                    log.warn("Cannot parse snapshot for table {}, falling back to JPA fields",
                            entity.getId());
                }
            }
        }
        return RelationTableResponse.fromEntity(entity);
    }

    private RelationTableResponse buildResponseWithSnapshotFields(
            RelationTableDefinition entity, List<RelationFieldDTO> snapshotFields) {
        List<RelationTableResponse.FieldDefinitionResponse> fields = snapshotFields.stream()
                .map(f -> RelationTableResponse.FieldDefinitionResponse.builder()
                        .id(f.getId())
                        .fieldName(f.getFieldName())
                        .dataType(f.getDataType())
                        .length(f.getLength())
                        .precision(f.getPrecision())
                        .scale(f.getScale())
                        .nullable(f.getNullable())
                        .isPrimaryKey(f.getIsPrimaryKey())
                        .defaultValue(f.getDefaultValue())
                        .comment(f.getComment())
                        .sortOrder(f.getSortOrder())
                        .build())
                .collect(Collectors.toList());
        return RelationTableResponse.builder()
                .id(entity.getId())
                .tableName(entity.getTableName())
                .displayName(entity.getDisplayName())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .enabled(entity.getEnabled())
                .portalVisible(entity.getPortalVisible())
                .currentVersion(entity.getCurrentVersion())
                .fieldDefinitions(fields)
                .createdAt(entity.getCreatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedAt(entity.getUpdatedAt())
                .updatedBy(entity.getUpdatedBy())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RelationTableDataRowDTO> queryData(Long tableId, String search, Pageable pageable) {
        RelationTableDefinition tableDef = getDeployedTableDefinition(tableId);
        List<RelationFieldDTO> fields = getDeployedFields(tableDef);
        String physicalTableName = tableDef.getTableName();

        // Find primary key field (may be CTID_PK when no field is marked as PK)
        String pkField = findPrimaryKeyField(fields);
        boolean ctidMode = usesCtid(pkField);

        // Build column list; include ctid when no real PK exists
        List<String> columnNames = fields.stream()
                .map(RelationFieldDTO::getFieldName)
                .collect(Collectors.toList());

        String dataColumns = columnNames.stream()
                .map(this::quoteIdentifier)
                .collect(Collectors.joining(", "));
        // When no PK: fetch ctid alongside the data columns so we can return a stable rowId
        String selectColumns = ctidMode ? "ctid::text AS \"" + CTID_PK + "\", " + dataColumns : dataColumns;

        // Build WHERE clause for search
        List<Object> params = new ArrayList<>();
        String whereClause = buildSearchWhereClause(fields, search, params);

        // Count query (ctid alias not needed here)
        String countSql = "SELECT COUNT(*) FROM " + quoteIdentifier(physicalTableName) + whereClause;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        if (total == null) total = 0L;

        // Data query with pagination
        String dataSql = "SELECT " + selectColumns + " FROM " + quoteIdentifier(physicalTableName) +
                whereClause + " LIMIT ? OFFSET ?";
        params.add(pageable.getPageSize());
        params.add(pageable.getOffset());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(dataSql, params.toArray());

        List<RelationTableDataRowDTO> dtoList = rows.stream()
                .map(row -> {
                    // rowId: use ctid alias when no real PK, otherwise use PK column value
                    String rowId = row.get(ctidMode ? CTID_PK : pkField) != null
                            ? row.get(ctidMode ? CTID_PK : pkField).toString()
                            : null;
                    // Remove the synthetic ctid column from the data map before returning
                    if (ctidMode) row.remove(CTID_PK);
                    return RelationTableDataRowDTO.builder()
                            .rowId(rowId)
                            .tableId(tableId)
                            .data(row)
                            .build();
                })
                .collect(Collectors.toList());

        return new PageImpl<>(dtoList, pageable, total);
    }

    @Override
    @Transactional
    public RelationTableDataRowDTO addData(Long tableId, Map<String, Object> data) {
        RelationTableDefinition tableDef = getDeployedTableDefinition(tableId);
        List<RelationFieldDTO> fields = getDeployedFields(tableDef);
        String physicalTableName = tableDef.getTableName();
        String pkField = findPrimaryKeyField(fields);

        // Filter data to only include valid field names
        Set<String> validFieldNames = fields.stream()
                .map(RelationFieldDTO::getFieldName)
                .collect(Collectors.toSet());
        Map<String, Object> filteredData = data.entrySet().stream()
                .filter(e -> validFieldNames.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // 查询物理表实际存在的列，避免插入不存在的列
        Set<String> physicalColumns = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = ? AND table_schema = ?",
                String.class, physicalTableName, schemaResolver.getSchema()));

        // 过滤掉物理表中不存在的列
        filteredData.entrySet().removeIf(e -> !physicalColumns.contains(e.getKey()));

        // 自动填充审计字段（仅当物理表存在对应列时）
        String currentUser = SecurityContextUtils.getCurrentUsername().orElse("system");
        java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
        if (physicalColumns.contains("created_at")) {
            filteredData.put("created_at", now);
        }
        if (physicalColumns.contains("created_by")) {
            filteredData.put("created_by", currentUser);
        }
        if (physicalColumns.contains("updated_at")) {
            filteredData.put("updated_at", now);
        }
        if (physicalColumns.contains("updated_by")) {
            filteredData.put("updated_by", currentUser);
        }

        // Build INSERT SQL
        List<String> columns = new ArrayList<>(filteredData.keySet());
        String columnList = columns.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        Object[] values = columns.stream().map(filteredData::get).toArray();

        String sql = "INSERT INTO " + quoteIdentifier(physicalTableName) +
                " (" + columnList + ") VALUES (" + placeholders + ")";
        jdbcTemplate.update(sql, values);

        String rowId = filteredData.get(pkField) != null ? filteredData.get(pkField).toString() : null;

        // Audit log
        auditService.logAdd(tableId, physicalTableName, rowId, filteredData);

        return RelationTableDataRowDTO.builder()
                .rowId(rowId)
                .tableId(tableId)
                .data(filteredData)
                .build();
    }

    @Override
    @Transactional
    public RelationTableDataRowDTO updateData(Long tableId, String rowId, Map<String, Object> data) {
        RelationTableDefinition tableDef = getDeployedTableDefinition(tableId);
        List<RelationFieldDTO> fields = getDeployedFields(tableDef);
        String physicalTableName = tableDef.getTableName();
        String pkField = findPrimaryKeyField(fields);

        // Get old data for audit
        Map<String, Object> oldData = getRowData(physicalTableName, fields, pkField, rowId);
        if (oldData == null) {
            throw new RelationTableNotFoundException("Row not found: " + rowId);
        }

        // 查询物理表实际存在的列
        Set<String> physicalColumns = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = ? AND table_schema = ?",
                String.class, physicalTableName, schemaResolver.getSchema()));

        // Filter data to only include valid field names (exclude PK)
        Set<String> validFieldNames = fields.stream()
                .map(RelationFieldDTO::getFieldName)
                .collect(Collectors.toSet());
        Map<String, Object> filteredData = data.entrySet().stream()
                .filter(e -> validFieldNames.contains(e.getKey()))
                .filter(e -> !e.getKey().equals(pkField))
                .filter(e -> physicalColumns.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // 自动填充审计字段（仅当物理表存在对应列时）
        String currentUser = SecurityContextUtils.getCurrentUsername().orElse("system");
        if (physicalColumns.contains("updated_at")) {
            filteredData.put("updated_at", java.sql.Timestamp.from(Instant.now()));
        }
        if (physicalColumns.contains("updated_by")) {
            filteredData.put("updated_by", currentUser);
        }

        if (filteredData.isEmpty()) {
            return RelationTableDataRowDTO.builder()
                    .rowId(rowId)
                    .tableId(tableId)
                    .data(oldData)
                    .build();
        }

        // Build UPDATE SQL
        List<String> setClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, Object> entry : filteredData.entrySet()) {
            setClauses.add(quoteIdentifier(entry.getKey()) + " = ?");
            params.add(entry.getValue());
        }
        String pkWhere = usesCtid(pkField) ? "ctid = ?::tid" : quoteIdentifier(pkField) + " = ?";
        params.add(usesCtid(pkField) ? rowId : castRowId(rowId, fields));

        String sql = "UPDATE " + quoteIdentifier(physicalTableName) +
                " SET " + String.join(", ", setClauses) +
                " WHERE " + pkWhere;
        jdbcTemplate.update(sql, params.toArray());

        // Get updated data.
        // After a ctid-based UPDATE the row's physical location changes (MVCC), so the old
        // ctid is no longer valid. Reconstruct newData by merging the applied changes into oldData.
        Map<String, Object> newData;
        if (usesCtid(pkField)) {
            newData = new java.util.LinkedHashMap<>(oldData);
            newData.putAll(filteredData);
        } else {
            newData = getRowData(physicalTableName, fields, pkField, rowId);
        }

        // Audit log
        auditService.logUpdate(tableId, physicalTableName, rowId, oldData, newData);

        return RelationTableDataRowDTO.builder()
                .rowId(rowId)
                .tableId(tableId)
                .data(newData)
                .build();
    }

    @Override
    @Transactional
    public void deleteData(Long tableId, String rowId) {
        RelationTableDefinition tableDef = getDeployedTableDefinition(tableId);
        List<RelationFieldDTO> fields = getDeployedFields(tableDef);
        String physicalTableName = tableDef.getTableName();
        String pkField = findPrimaryKeyField(fields);

        // Get old data for audit
        Map<String, Object> oldData = getRowData(physicalTableName, fields, pkField, rowId);
        if (oldData == null) {
            throw new RelationTableNotFoundException("Row not found: " + rowId);
        }

        String pkWhere = usesCtid(pkField) ? "ctid = ?::tid" : quoteIdentifier(pkField) + " = ?";
        Object pkParam = usesCtid(pkField) ? rowId : castRowId(rowId, fields);
        String sql = "DELETE FROM " + quoteIdentifier(physicalTableName) + " WHERE " + pkWhere;
        jdbcTemplate.update(sql, pkParam);

        // Audit log
        auditService.logDelete(tableId, physicalTableName, rowId, oldData);
    }

    @Override
    @Transactional
    public RelationTableDataRowDTO changeStatus(Long tableId, String rowId, String status) {
        RelationTableDefinition tableDef = getDeployedTableDefinition(tableId);
        List<RelationFieldDTO> fields = getDeployedFields(tableDef);
        String physicalTableName = tableDef.getTableName();
        String pkField = findPrimaryKeyField(fields);

        // Check if status column exists
        boolean hasStatusField = fields.stream()
                .anyMatch(f -> "status".equalsIgnoreCase(f.getFieldName()));

        Map<String, Object> oldData = getRowData(physicalTableName, fields, pkField, rowId);
        if (oldData == null) {
            throw new RelationTableNotFoundException("Row not found: " + rowId);
        }

        String oldStatus;
        if (hasStatusField) {
            oldStatus = oldData.get("status") != null ? oldData.get("status").toString() : "Unknown";
            String pkWhere = usesCtid(pkField) ? "ctid = ?::tid" : quoteIdentifier(pkField) + " = ?";
            Object pkParam = usesCtid(pkField) ? rowId : castRowId(rowId, fields);
            String sql = "UPDATE " + quoteIdentifier(physicalTableName) +
                    " SET \"status\" = ? WHERE " + pkWhere;
            jdbcTemplate.update(sql, status, pkParam);
        } else {
            // If no status column, we still log the status change intent
            oldStatus = "Unknown";
            log.warn("Table '{}' does not have a 'status' column, status change logged but not applied to physical table", physicalTableName);
        }

        // Audit log
        auditService.logStatusChange(tableId, physicalTableName, rowId, oldStatus, status);

        // For ctid-addressed rows the tuple moves after UPDATE; reconstruct instead of re-fetching.
        Map<String, Object> newData;
        if (usesCtid(pkField)) {
            newData = new java.util.LinkedHashMap<>(oldData);
            if (hasStatusField) newData.put("status", status);
        } else {
            newData = getRowData(physicalTableName, fields, pkField, rowId);
        }

        return RelationTableDataRowDTO.builder()
                .rowId(rowId)
                .tableId(tableId)
                .data(newData != null ? newData : oldData)
                .build();
    }

    // ==================== 辅助方法 ====================

    /**
     * 导出表数据为 CSV
     */
    @Override
    @Transactional(readOnly = true)
    public String exportCsv(Long tableId, int maxRows) {
        RelationTableDefinition tableDef = getDeployedTableDefinition(tableId);
        List<RelationFieldDTO> fields = getDeployedFields(tableDef);
        String physicalTableName = tableDef.getTableName();

        List<String> columnNames = fields.stream()
                .map(RelationFieldDTO::getFieldName)
                .collect(Collectors.toList());

        String columnList = columnNames.stream()
                .map(this::quoteIdentifier)
                .collect(Collectors.joining(", "));

        String sql = "SELECT " + columnList + " FROM " + quoteIdentifier(physicalTableName) + " LIMIT ?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, maxRows);

        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", columnNames)).append("\n");
        for (Map<String, Object> row : rows) {
            csv.append(columnNames.stream()
                    .map(f -> escapeCsvValue(row.get(f)))
                    .collect(Collectors.joining(","))
            ).append("\n");
        }
        return csv.toString();
    }

    private String escapeCsvValue(Object value) {
        if (value == null) return "";
        String str = value.toString();
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }

    /**
     * 获取已部署的表定义，验证状态为 DEPLOYED
     */
    private RelationTableDefinition getDeployedTableDefinition(Long tableId) {
        RelationTableDefinition tableDef = tableDefinitionRepository.findById(tableId)
                .orElseThrow(() -> new RelationTableNotFoundException(tableId));
        if (tableDef.getStatus() != RelationTableStatus.DEPLOYED
                && tableDef.getStatus() != RelationTableStatus.UPDATED
                && tableDef.getStatus() != RelationTableStatus.ROLLBACK) {
            throw new RelationTableNotFoundException("Table is not deployed: " + tableId);
        }
        return tableDef;
    }

    /**
     * 获取用于 Data Management 查询的字段列表——必须与物理表实际已部署的列完全一致。
     * <p>
     * 对于 UPDATED / ROLLBACK 状态的表，rt_field_definitions 可能包含尚未执行 DDL 的新字段；
     * 此时改用最新版本快照，避免 SELECT 中出现物理表不存在的列名导致 SQL 异常。
     */
    private List<RelationFieldDTO> getDeployedFields(RelationTableDefinition tableDef) {
        if (tableDef.getStatus() == RelationTableStatus.UPDATED
                || tableDef.getStatus() == RelationTableStatus.ROLLBACK) {
            Optional<RelationTableVersion> latestVersion =
                    versionRepository.findLatestVersion(tableDef.getId());
            if (latestVersion.isPresent()) {
                try {
                    List<RelationFieldDTO> snapshotFields =
                            parseSnapshotData(latestVersion.get().getSnapshotData());
                    if (!snapshotFields.isEmpty()) {
                        log.debug("Using snapshot fields for {}-status table '{}' to match physical schema",
                                tableDef.getStatus(), tableDef.getTableName());
                        return snapshotFields;
                    }
                } catch (Exception e) {
                    log.warn("Cannot parse snapshot for table {}, falling back to rt_field_definitions",
                            tableDef.getId());
                }
            }
        }
        List<RelationFieldDTO> fields = jdbcTemplate.query(
                "SELECT id, field_name, data_type, length, precision_value, scale, nullable, is_primary_key, default_value, comment, sort_order "
                + "FROM rt_field_definitions WHERE table_id = ? ORDER BY sort_order ASC",
                (rs, rowNum) -> RelationFieldDTO.builder()
                        .id(rs.getLong("id"))
                        .fieldName(rs.getString("field_name"))
                        .dataType(com.platform.common.enums.RelationDataType.valueOf(rs.getString("data_type")))
                        .length(rs.getObject("length", Integer.class))
                        .precision(rs.getObject("precision_value", Integer.class))
                        .scale(rs.getObject("scale", Integer.class))
                        .nullable(rs.getBoolean("nullable"))
                        .isPrimaryKey(rs.getBoolean("is_primary_key"))
                        .defaultValue(rs.getString("default_value"))
                        .comment(rs.getString("comment"))
                        .sortOrder(rs.getInt("sort_order"))
                        .build(),
                tableDef.getId());
        if (fields.isEmpty()) {
            // Legacy table: was deployed before rt_field_definitions existed.
            // Fall back to inferring fields from the physical table schema.
            List<RelationFieldDTO> inferred = inferFieldsFromPhysicalTable(tableDef.getTableName());
            if (inferred.isEmpty()) {
                throw new RelationTableNotFoundException(
                        "No field definitions found for table: " + tableDef.getId());
            }
            log.info("Legacy table '{}': inferred {} fields from physical schema",
                    tableDef.getTableName(), inferred.size());
            return inferred;
        }
        return fields;
    }

    /**
     * Reconstruct a field list by reading column metadata from information_schema.
     * Used as a fallback for legacy tables that have no rt_field_definitions records.
     */
    private List<RelationFieldDTO> inferFieldsFromPhysicalTable(String tableName) {
        String schema = schemaResolver.getSchema();
        try {
            Set<String> pkColumns = new HashSet<>(jdbcTemplate.queryForList(
                    "SELECT kcu.column_name "
                    + "FROM information_schema.table_constraints tc "
                    + "JOIN information_schema.key_column_usage kcu "
                    + "  ON tc.constraint_name = kcu.constraint_name "
                    + "  AND tc.table_schema   = kcu.table_schema "
                    + "WHERE tc.table_name    = ? "
                    + "  AND tc.table_schema  = ? "
                    + "  AND tc.constraint_type = 'PRIMARY KEY'",
                    String.class, tableName, schema));

            return jdbcTemplate.query(
                    "SELECT column_name, udt_name, data_type, "
                    + "character_maximum_length, numeric_precision, numeric_scale, "
                    + "ordinal_position "
                    + "FROM information_schema.columns "
                    + "WHERE table_name = ? AND table_schema = ? "
                    + "ORDER BY ordinal_position",
                    (rs, rowNum) -> {
                        String colName = rs.getString("column_name");
                        String pgType  = rs.getString("udt_name") != null
                                ? rs.getString("udt_name").toLowerCase()
                                : rs.getString("data_type").toLowerCase();
                        return RelationFieldDTO.builder()
                                .id(null)
                                .fieldName(colName)
                                .dataType(mapPostgresType(pgType))
                                .length(rs.getObject("character_maximum_length", Integer.class))
                                .precision(rs.getObject("numeric_precision", Integer.class))
                                .scale(rs.getObject("numeric_scale", Integer.class))
                                .nullable(true)
                                .isPrimaryKey(pkColumns.contains(colName))
                                .sortOrder(rs.getInt("ordinal_position") - 1)
                                .build();
                    },
                    tableName, schema);
        } catch (Exception e) {
            log.warn("Failed to infer fields from physical table '{}': {}", tableName, e.getMessage());
            return List.of();
        }
    }

    private static com.platform.common.enums.RelationDataType mapPostgresType(String pgType) {
        return switch (pgType) {
            case "int2", "int4", "integer"         -> com.platform.common.enums.RelationDataType.INTEGER;
            case "int8", "bigint"                  -> com.platform.common.enums.RelationDataType.BIGINT;
            case "numeric", "decimal"              -> com.platform.common.enums.RelationDataType.DECIMAL;
            case "bool", "boolean"                 -> com.platform.common.enums.RelationDataType.BOOLEAN;
            case "date"                            -> com.platform.common.enums.RelationDataType.DATE;
            case "timestamp", "timestamptz",
                 "timestamp without time zone",
                 "timestamp with time zone"        -> com.platform.common.enums.RelationDataType.TIMESTAMP;
            case "text", "json", "jsonb"           -> com.platform.common.enums.RelationDataType.TEXT;
            default                                -> com.platform.common.enums.RelationDataType.VARCHAR;
        };
    }

    /**
     * Virtual PK name used when no field has isPrimaryKey=true.
     * The actual identifier is the PostgreSQL ctid pseudo-column.
     */
    private static final String CTID_PK = "__ctid__";

    /**
     * Returns the name of the primary-key field, or {@link #CTID_PK} when no
     * field is marked as a primary key (ctid-based addressing is used instead).
     */
    private String findPrimaryKeyField(List<RelationFieldDTO> fields) {
        return fields.stream()
                .filter(f -> Boolean.TRUE.equals(f.getIsPrimaryKey()))
                .map(RelationFieldDTO::getFieldName)
                .findFirst()
                .orElse(CTID_PK);
    }

    /** Returns true when the table has no field marked isPrimaryKey. */
    private boolean usesCtid(String pkField) {
        return CTID_PK.equals(pkField);
    }

    /**
     * Type-cast the rowId to the correct Java type for JDBC binding.
     * When ctid is used the value is kept as a String and matched with
     * {@code ctid = ?::tid} in the SQL.
     */
    private Object castRowId(String rowId, List<RelationFieldDTO> fields) {
        RelationFieldDTO pkFieldDef = fields.stream()
                .filter(f -> Boolean.TRUE.equals(f.getIsPrimaryKey()))
                .findFirst().orElse(null);
        if (pkFieldDef != null && rowId != null) {
            var dt = pkFieldDef.getDataType();
            if (dt == com.platform.common.enums.RelationDataType.INTEGER) {
                return Integer.valueOf(rowId);
            } else if (dt == com.platform.common.enums.RelationDataType.BIGINT) {
                return Long.valueOf(rowId);
            }
        }
        return rowId;
    }

    /**
     * 构建搜索 WHERE 子句（对所有 VARCHAR/TEXT 字段进行 ILIKE 模糊匹配）
     */
    private String buildSearchWhereClause(List<RelationFieldDTO> fields, String search, List<Object> params) {
        if (search == null || search.isBlank()) {
            return "";
        }

        List<String> searchableFields = fields.stream()
                .filter(f -> isTextType(f))
                .map(RelationFieldDTO::getFieldName)
                .collect(Collectors.toList());

        if (searchableFields.isEmpty()) {
            return "";
        }

        String conditions = searchableFields.stream()
                .map(f -> "CAST(" + quoteIdentifier(f) + " AS TEXT) ILIKE ? ESCAPE '\\'")
                .collect(Collectors.joining(" OR "));

        String searchPattern = "%" + escapeLikePattern(search) + "%";
        for (int i = 0; i < searchableFields.size(); i++) {
            params.add(searchPattern);
        }

        return " WHERE (" + conditions + ")";
    }

    private String escapeLikePattern(String input) {
        return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * 判断字段是否为文本类型（可搜索）
     */
    private boolean isTextType(RelationFieldDTO field) {
        return switch (field.getDataType()) {
            case VARCHAR, TEXT -> true;
            default -> false;
        };
    }

    /**
     * 获取单行数据
     */
    private Map<String, Object> getRowData(String physicalTableName, List<RelationFieldDTO> fields,
                                            String pkField, String rowId) {
        String columnList = fields.stream()
                .map(f -> quoteIdentifier(f.getFieldName()))
                .collect(Collectors.joining(", "));

        String whereClause = usesCtid(pkField)
                ? " WHERE ctid = ?::tid"
                : " WHERE " + quoteIdentifier(pkField) + " = ?";
        Object param = usesCtid(pkField) ? rowId : castRowId(rowId, fields);

        String sql = "SELECT " + columnList + " FROM " + quoteIdentifier(physicalTableName) + whereClause;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, param);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 引用标识符（防止 SQL 注入和保留字冲突）
     */
    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    /**
     * 解析版本快照 JSON 数据
     */
    private List<RelationFieldDTO> parseSnapshotData(String snapshotData) {
        try {
            return objectMapper.readValue(snapshotData, new TypeReference<List<RelationFieldDTO>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse snapshot data", e);
        }
    }
}
