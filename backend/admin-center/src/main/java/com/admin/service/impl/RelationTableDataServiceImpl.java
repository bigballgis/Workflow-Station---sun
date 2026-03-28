package com.admin.service.impl;

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

    @Override
    @Transactional(readOnly = true)
    public List<RelationTableResponse> getDeployedTables() {
        return tableDefinitionRepository.findByStatusInAndEnabledTrue(
                        List.of(RelationTableStatus.DEPLOYED, RelationTableStatus.UPDATED, RelationTableStatus.ROLLBACK)).stream()
                .map(this::toDeployedTableResponse)
                .collect(Collectors.toList());
    }

    /**
     * 将表定义转换为响应 DTO，字段定义使用已部署版本的快照数据，
     * 而非当前实体中可能已被修改但尚未部署的字段定义。
     */
    private RelationTableResponse toDeployedTableResponse(RelationTableDefinition entity) {
        RelationTableResponse response = RelationTableResponse.fromEntity(entity);
        // 用已部署版本快照中的字段覆盖，避免展示未部署的修改
        try {
            List<RelationFieldDTO> deployedFields = getDeployedFields(entity);
            List<RelationTableResponse.FieldDefinitionResponse> fieldResponses = deployedFields.stream()
                    .map(f -> RelationTableResponse.FieldDefinitionResponse.builder()
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
            response.setFieldDefinitions(fieldResponses);
        } catch (Exception e) {
            log.warn("No deployed version found for table: {}, returning empty fields", entity.getId());
            response.setFieldDefinitions(Collections.emptyList());
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RelationTableDataRowDTO> queryData(Long tableId, String search, Pageable pageable) {
        RelationTableDefinition tableDef = getDeployedTableDefinition(tableId);
        List<RelationFieldDTO> fields = getDeployedFields(tableDef);
        String physicalTableName = tableDef.getTableName();

        // Find primary key field
        String pkField = findPrimaryKeyField(fields);

        // Build column list
        List<String> columnNames = fields.stream()
                .map(RelationFieldDTO::getFieldName)
                .collect(Collectors.toList());

        String columnList = columnNames.stream()
                .map(this::quoteIdentifier)
                .collect(Collectors.joining(", "));

        // Build WHERE clause for search
        List<Object> params = new ArrayList<>();
        String whereClause = buildSearchWhereClause(fields, search, params);

        // Count query
        String countSql = "SELECT COUNT(*) FROM " + quoteIdentifier(physicalTableName) + whereClause;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        if (total == null) total = 0L;

        // Data query with pagination
        String dataSql = "SELECT " + columnList + " FROM " + quoteIdentifier(physicalTableName) +
                whereClause + " LIMIT ? OFFSET ?";
        params.add(pageable.getPageSize());
        params.add(pageable.getOffset());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(dataSql, params.toArray());

        List<RelationTableDataRowDTO> dtoList = rows.stream()
                .map(row -> RelationTableDataRowDTO.builder()
                        .rowId(row.get(pkField) != null ? row.get(pkField).toString() : null)
                        .tableId(tableId)
                        .data(row)
                        .build())
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
                "SELECT column_name FROM information_schema.columns WHERE table_name = ? AND table_schema = 'public'",
                String.class, physicalTableName));

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
                "SELECT column_name FROM information_schema.columns WHERE table_name = ? AND table_schema = 'public'",
                String.class, physicalTableName));

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
        params.add(castRowId(rowId, fields));

        String sql = "UPDATE " + quoteIdentifier(physicalTableName) +
                " SET " + String.join(", ", setClauses) +
                " WHERE " + quoteIdentifier(pkField) + " = ?";
        jdbcTemplate.update(sql, params.toArray());

        // Get updated data
        Map<String, Object> newData = getRowData(physicalTableName, fields, pkField, rowId);

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

        String sql = "DELETE FROM " + quoteIdentifier(physicalTableName) +
                " WHERE " + quoteIdentifier(pkField) + " = ?";
        jdbcTemplate.update(sql, castRowId(rowId, fields));

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
            String sql = "UPDATE " + quoteIdentifier(physicalTableName) +
                    " SET \"status\" = ? WHERE " + quoteIdentifier(pkField) + " = ?";
            jdbcTemplate.update(sql, status, castRowId(rowId, fields));
        } else {
            // If no status column, we still log the status change intent
            oldStatus = "Unknown";
            log.warn("Table '{}' does not have a 'status' column, status change logged but not applied to physical table", physicalTableName);
        }

        // Audit log
        auditService.logStatusChange(tableId, physicalTableName, rowId, oldStatus, status);

        Map<String, Object> newData = getRowData(physicalTableName, fields, pkField, rowId);

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
     * 获取已部署的最新表结构字段列表（从版本快照中获取）
     */
    private List<RelationFieldDTO> getDeployedFields(RelationTableDefinition tableDef) {
        RelationTableVersion latestVersion = versionRepository.findLatestVersion(tableDef.getId())
                .orElseThrow(() -> new RelationTableNotFoundException(
                        "No deployed version found for table: " + tableDef.getId()));
        return parseSnapshotData(latestVersion.getSnapshotData());
    }

    /**
     * 查找主键字段名
     */
    private String findPrimaryKeyField(List<RelationFieldDTO> fields) {
        return fields.stream()
                .filter(f -> Boolean.TRUE.equals(f.getIsPrimaryKey()))
                .map(RelationFieldDTO::getFieldName)
                .findFirst()
                .orElse(fields.isEmpty() ? "id" : fields.get(0).getFieldName());
    }

    private Object castRowId(String rowId, List<RelationFieldDTO> fields) {
        RelationFieldDTO pkField = fields.stream()
                .filter(f -> Boolean.TRUE.equals(f.getIsPrimaryKey()))
                .findFirst().orElse(null);
        if (pkField != null && rowId != null) {
            var dt = pkField.getDataType();
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
                .map(f -> "CAST(" + quoteIdentifier(f) + " AS TEXT) ILIKE ?")
                .collect(Collectors.joining(" OR "));

        String searchPattern = "%" + search + "%";
        for (int i = 0; i < searchableFields.size(); i++) {
            params.add(searchPattern);
        }

        return " WHERE (" + conditions + ")";
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

        String sql = "SELECT " + columnList + " FROM " + quoteIdentifier(physicalTableName) +
                " WHERE " + quoteIdentifier(pkField) + " = ?";

        Object typedRowId = castRowId(rowId, fields);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, typedRowId);
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
