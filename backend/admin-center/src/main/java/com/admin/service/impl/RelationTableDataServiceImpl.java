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
 * Relation Table row data management; rows are stored in rt_table_data_rows (JSONB), not per-table physical tables.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelationTableDataServiceImpl implements RelationTableDataService {

    private static final String DATA_ROWS_TABLE = "rt_table_data_rows";

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
     * Maps table definition entity to response DTO for Table Data.
     * Field list and table display name come from the latest deployed version snapshot / deployed_display_name,
     * not draft rt_field_definitions or display_name while status is UPDATED/ROLLBACK.
     */
    private RelationTableResponse toDeployedTableResponse(RelationTableDefinition entity) {
        String deployedDisplayName = resolveDeployedDisplayName(entity);

        if (entity.getCurrentVersion() != null && entity.getCurrentVersion() > 0) {
            Optional<RelationTableVersion> latestVersion =
                    versionRepository.findLatestVersion(entity.getId());
            if (latestVersion.isPresent()) {
                try {
                    List<RelationFieldDTO> snapshotFields =
                            parseSnapshotData(latestVersion.get().getSnapshotData());
                    if (!snapshotFields.isEmpty()) {
                        return buildResponseWithSnapshotFields(entity, snapshotFields, deployedDisplayName);
                    }
                } catch (Exception e) {
                    log.warn("Cannot parse snapshot for table {}, falling back to JPA fields",
                            entity.getId());
                }
            }
        }

        RelationTableResponse response = RelationTableResponse.fromEntity(entity);
        response.setDisplayName(deployedDisplayName);
        return response;
    }

    private String resolveDeployedDisplayName(RelationTableDefinition entity) {
        if (entity.getStatus() == RelationTableStatus.UPDATED
                || entity.getStatus() == RelationTableStatus.ROLLBACK) {
            if (entity.getDeployedDisplayName() != null
                    && !entity.getDeployedDisplayName().isBlank()) {
                return entity.getDeployedDisplayName();
            }
            // Legacy rows: avoid exposing draft display_name before next deploy
            return entity.getTableName();
        }
        return entity.getDisplayName();
    }

    private RelationTableResponse buildResponseWithSnapshotFields(
            RelationTableDefinition entity, List<RelationFieldDTO> snapshotFields, String displayName) {
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
                        .displayName(f.getDisplayName())
                        .sortOrder(f.getSortOrder())
                        .build())
                .collect(Collectors.toList());
        return RelationTableResponse.builder()
                .id(entity.getId())
                .tableName(entity.getTableName())
                .displayName(displayName)
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

        List<Object> params = new ArrayList<>();
        params.add(tableId);
        String searchClause = buildJsonSearchWhereClause(fields, search, params);

        String countSql = "SELECT COUNT(*) FROM " + DATA_ROWS_TABLE + " WHERE table_id = ?" + searchClause;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        if (total == null) total = 0L;

        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(pageable.getPageSize());
        dataParams.add(pageable.getOffset());
        String dataSql = "SELECT row_id, data FROM " + DATA_ROWS_TABLE
                + " WHERE table_id = ?" + searchClause
                + " ORDER BY id LIMIT ? OFFSET ?";

        List<RelationTableDataRowDTO> dtoList = jdbcTemplate.query(dataSql, (rs, rowNum) ->
                RelationTableDataRowDTO.builder()
                        .rowId(rs.getString("row_id"))
                        .tableId(tableId)
                        .data(parseRowData(rs.getString("data")))
                        .build(),
                dataParams.toArray());

        return new PageImpl<>(dtoList, pageable, total);
    }

    @Override
    @Transactional
    public RelationTableDataRowDTO addData(Long tableId, Map<String, Object> data) {
        RelationTableDefinition tableDef = getDeployedTableDefinition(tableId);
        List<RelationFieldDTO> fields = getDeployedFields(tableDef);
        String tableName = tableDef.getTableName();
        String pkField = findPrimaryKeyField(fields);

        Set<String> validFieldNames = fields.stream()
                .map(RelationFieldDTO::getFieldName)
                .collect(Collectors.toSet());
        Map<String, Object> filteredData = data.entrySet().stream()
                .filter(e -> validFieldNames.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, LinkedHashMap::new));

        String currentUser = SecurityContextUtils.getCurrentUsername().orElse("system");
        java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
        if (validFieldNames.contains("created_at")) filteredData.put("created_at", now);
        if (validFieldNames.contains("created_by")) filteredData.put("created_by", currentUser);
        if (validFieldNames.contains("updated_at")) filteredData.put("updated_at", now);
        if (validFieldNames.contains("updated_by")) filteredData.put("updated_by", currentUser);

        String rowId = resolveRowId(pkField, filteredData);
        String json = writeRowJson(filteredData);

        jdbcTemplate.update(
                "INSERT INTO " + DATA_ROWS_TABLE
                        + " (table_id, row_id, data, status, created_at, created_by, updated_at, updated_by)"
                        + " VALUES (?, ?, ?::jsonb, 'ACTIVE', ?, ?, ?, ?)",
                tableId, rowId, json, now, currentUser, now, currentUser);

        auditService.logAdd(tableId, tableName, rowId, filteredData);

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
        String tableName = tableDef.getTableName();
        String pkField = findPrimaryKeyField(fields);

        Map<String, Object> oldData = getRowData(tableId, rowId);
        if (oldData == null) {
            throw new RelationTableNotFoundException("Row not found: " + rowId);
        }

        Set<String> validFieldNames = fields.stream()
                .map(RelationFieldDTO::getFieldName)
                .collect(Collectors.toSet());
        Map<String, Object> filteredData = data.entrySet().stream()
                .filter(e -> validFieldNames.contains(e.getKey()))
                .filter(e -> !e.getKey().equals(pkField))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        String currentUser = SecurityContextUtils.getCurrentUsername().orElse("system");
        java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
        if (validFieldNames.contains("updated_at")) filteredData.put("updated_at", now);
        if (validFieldNames.contains("updated_by")) filteredData.put("updated_by", currentUser);

        if (filteredData.isEmpty()) {
            return RelationTableDataRowDTO.builder()
                    .rowId(rowId)
                    .tableId(tableId)
                    .data(oldData)
                    .build();
        }

        Map<String, Object> newData = new LinkedHashMap<>(oldData);
        newData.putAll(filteredData);
        jdbcTemplate.update(
                "UPDATE " + DATA_ROWS_TABLE
                        + " SET data = ?::jsonb, updated_at = ?, updated_by = ?"
                        + " WHERE table_id = ? AND row_id = ?",
                writeRowJson(newData), now, currentUser, tableId, rowId);

        auditService.logUpdate(tableId, tableName, rowId, oldData, newData);

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
        String tableName = tableDef.getTableName();

        Map<String, Object> oldData = getRowData(tableId, rowId);
        if (oldData == null) {
            throw new RelationTableNotFoundException("Row not found: " + rowId);
        }

        jdbcTemplate.update(
                "DELETE FROM " + DATA_ROWS_TABLE + " WHERE table_id = ? AND row_id = ?",
                tableId, rowId);

        auditService.logDelete(tableId, tableName, rowId, oldData);
    }

    @Override
    @Transactional
    public RelationTableDataRowDTO changeStatus(Long tableId, String rowId, String status) {
        RelationTableDefinition tableDef = getDeployedTableDefinition(tableId);
        List<RelationFieldDTO> fields = getDeployedFields(tableDef);
        String tableName = tableDef.getTableName();

        boolean hasStatusField = fields.stream()
                .anyMatch(f -> "status".equalsIgnoreCase(f.getFieldName()));

        Map<String, Object> oldData = getRowData(tableId, rowId);
        if (oldData == null) {
            throw new RelationTableNotFoundException("Row not found: " + rowId);
        }

        String oldStatus = oldData.get("status") != null ? oldData.get("status").toString() : "Unknown";
        Map<String, Object> newData = new LinkedHashMap<>(oldData);
        if (hasStatusField) {
            newData.put("status", status);
        }

        String currentUser = SecurityContextUtils.getCurrentUsername().orElse("system");
        java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "UPDATE " + DATA_ROWS_TABLE
                        + " SET status = ?, data = ?::jsonb, updated_at = ?, updated_by = ?"
                        + " WHERE table_id = ? AND row_id = ?",
                status, writeRowJson(newData), now, currentUser, tableId, rowId);

        auditService.logStatusChange(tableId, tableName, rowId, oldStatus, status);

        return RelationTableDataRowDTO.builder()
                .rowId(rowId)
                .tableId(tableId)
                .data(newData)
                .build();
    }

    // ==================== Helpers ====================

    /**
     * Exports table rows as CSV.
     */
    @Override
    @Transactional(readOnly = true)
    public String exportCsv(Long tableId, int maxRows) {
        RelationTableDefinition tableDef = getDeployedTableDefinition(tableId);
        List<RelationFieldDTO> fields = getDeployedFields(tableDef);

        List<String> columnNames = fields.stream()
                .map(RelationFieldDTO::getFieldName)
                .collect(Collectors.toList());

        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT data FROM " + DATA_ROWS_TABLE + " WHERE table_id = ? ORDER BY id LIMIT ?",
                (rs, rowNum) -> parseRowData(rs.getString("data")),
                tableId, maxRows);

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
     * Loads deployed table definition and validates status allows data access.
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
     * Field list for Data Management queries, aligned with deployed version snapshot when applicable.
     */
    private List<RelationFieldDTO> getDeployedFields(RelationTableDefinition tableDef) {
        if (tableDef.getCurrentVersion() != null && tableDef.getCurrentVersion() > 0) {
            Optional<RelationTableVersion> latestVersion =
                    versionRepository.findLatestVersion(tableDef.getId());
            if (latestVersion.isPresent()) {
                try {
                    List<RelationFieldDTO> snapshotFields =
                            parseSnapshotData(latestVersion.get().getSnapshotData());
                    if (!snapshotFields.isEmpty()) {
                        log.debug("Using snapshot fields for table '{}' (version {}) to match deployed schema",
                                tableDef.getTableName(), tableDef.getCurrentVersion());
                        return snapshotFields;
                    }
                } catch (Exception e) {
                    log.warn("Cannot parse snapshot for table {}, falling back to rt_field_definitions",
                            tableDef.getId());
                }
            }
        }
        List<RelationFieldDTO> fields = jdbcTemplate.query(
                "SELECT id, field_name, data_type, length, precision_value, scale, nullable, is_primary_key, default_value, display_name, sort_order "
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
                        .displayName(rs.getString("display_name"))
                        .sortOrder(rs.getInt("sort_order"))
                        .build(),
                tableDef.getId());
        if (fields.isEmpty()) {
            throw new RelationTableNotFoundException(
                    "No field definitions found for table: " + tableDef.getId());
        }
        return fields;
    }

    private Map<String, Object> getRowData(Long tableId, String rowId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT data FROM " + DATA_ROWS_TABLE + " WHERE table_id = ? AND row_id = ?",
                (rs, rowNum) -> parseRowData(rs.getString("data")),
                tableId, rowId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseRowData(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse row JSON data", e);
        }
    }

    private String writeRowJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize row JSON data", e);
        }
    }

    private String resolveRowId(String pkField, Map<String, Object> data) {
        if (!CTID_PK.equals(pkField) && data.get(pkField) != null) {
            return data.get(pkField).toString();
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Builds JSON search WHERE clause ({@code data->>'field' ILIKE}).
     */
    private String buildJsonSearchWhereClause(List<RelationFieldDTO> fields, String search, List<Object> params) {
        if (search == null || search.isBlank()) {
            return "";
        }
        List<String> searchableFields = fields.stream()
                .filter(this::isTextType)
                .map(RelationFieldDTO::getFieldName)
                .filter(this::isSafeFieldName)
                .collect(Collectors.toList());
        if (searchableFields.isEmpty()) {
            return "";
        }
        String searchPattern = "%" + escapeLikePattern(search) + "%";
        String conditions = searchableFields.stream()
                .map(f -> "data->>'" + f + "' ILIKE ? ESCAPE '\\'")
                .collect(Collectors.joining(" OR "));
        for (int i = 0; i < searchableFields.size(); i++) {
            params.add(searchPattern);
        }
        return " AND (" + conditions + ")";
    }

    private boolean isSafeFieldName(String fieldName) {
        return fieldName != null && fieldName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
    }

    /**
     * Virtual PK name used when no field has isPrimaryKey=true.
     */
    private static final String CTID_PK = "__uuid__";

    /**
     * Returns the name of the primary-key field, or a generated UUID key when none is marked PK.
     */
    private String findPrimaryKeyField(List<RelationFieldDTO> fields) {
        return fields.stream()
                .filter(f -> Boolean.TRUE.equals(f.getIsPrimaryKey()))
                .map(RelationFieldDTO::getFieldName)
                .findFirst()
                .orElse(CTID_PK);
    }

    private String escapeLikePattern(String input) {
        return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * Whether the field type is textual (searchable).
     */
    private boolean isTextType(RelationFieldDTO field) {
        return switch (field.getDataType()) {
            case VARCHAR, TEXT -> true;
            default -> false;
        };
    }

    /**
     * Parses version snapshot JSON into field DTOs.
     */
    private List<RelationFieldDTO> parseSnapshotData(String snapshotData) {
        try {
            return objectMapper.readValue(snapshotData, new TypeReference<List<RelationFieldDTO>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse snapshot data", e);
        }
    }
}
