package com.admin.service.impl;

import com.admin.config.DatabaseSchemaResolver;
import com.admin.dto.request.RollbackRequest;
import com.admin.dto.response.RelationTableResponse;
import com.admin.dto.response.RelationTableVersionResponse;
import com.admin.entity.RelationFieldDefinition;
import com.admin.entity.RelationTableDefinition;
import com.admin.entity.RelationTableVersion;
import com.admin.exception.RelationTableDeploymentException;
import com.admin.exception.RelationTableNotFoundException;
import com.admin.repository.RelationFieldDefinitionRepository;
import com.admin.repository.RelationTableDefinitionRepository;
import com.admin.repository.RelationTableVersionRepository;
import com.admin.service.RelationTableDeployService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.RelationFieldDTO;
import com.platform.common.enums.RelationDataType;
import com.platform.common.enums.RelationTableStatus;
import com.platform.common.i18n.I18nService;
import com.platform.security.util.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implements relation table deployment and rollback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelationTableDeployServiceImpl implements RelationTableDeployService {

    private final RelationTableDefinitionRepository tableDefinitionRepository;
    private final RelationTableVersionRepository versionRepository;
    private final RelationFieldDefinitionRepository fieldDefinitionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DatabaseSchemaResolver schemaResolver;
    private final I18nService i18nService;

    @Override
    @Transactional
    public RelationTableResponse deploy(Long tableId) {
        log.info("Deploying relation table: id={}", tableId);

        RelationTableDefinition tableDefinition = tableDefinitionRepository.findById(tableId)
                .orElseThrow(() -> new RelationTableNotFoundException(tableId));

        List<RelationFieldDefinition> fields = tableDefinition.getFieldDefinitions();
        if (fields == null || fields.isEmpty()) {
            throw new RelationTableDeploymentException(
                    i18nService.getMessage("admin.rt.no_fields_defined", tableDefinition.getTableName()));
        }

        // Auto-add audit fields when they are missing
        ensureAuditFields(tableDefinition, fields);

        boolean isFirstDeploy = tableDefinition.getCurrentVersion() == 0;
        // Row payload lives in rt_table_data_rows (JSONB): deploy updates metadata/snapshots only, no CREATE/ALTER TABLE.
        log.info("Deploying relation table metadata (JSON row storage): table={}", tableDefinition.getTableName());

        // Create version snapshot
        int newVersion = tableDefinition.getCurrentVersion() + 1;
        String snapshotData = createSnapshotData(fields);
        String currentUser = SecurityContextUtils.getCurrentUsername().orElse("system");

        RelationTableVersion version = RelationTableVersion.builder()
                .tableDefinition(tableDefinition)
                .versionNumber(newVersion)
                .snapshotData(snapshotData)
                .deployedBy(currentUser)
                .deployedAt(Instant.now())
                .changeLog(isFirstDeploy ? "Initial deployment" : "Structure update deployment")
                .build();
        versionRepository.save(version);

        // Update table status and version number
        tableDefinition.setDeployedDisplayName(tableDefinition.getDisplayName());
        tableDefinition.setStatus(RelationTableStatus.DEPLOYED);
        tableDefinition.setCurrentVersion(newVersion);
        RelationTableDefinition saved = tableDefinitionRepository.save(tableDefinition);

        log.info("Deployed relation table: id={}, version={}", tableId, newVersion);
        return RelationTableResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public RelationTableResponse rollback(Long tableId, RollbackRequest request) {
        log.info("Rolling back relation table: id={}, targetVersionId={}", tableId, request.getTargetVersionId());

        RelationTableDefinition tableDefinition = tableDefinitionRepository.findById(tableId)
                .orElseThrow(() -> new RelationTableNotFoundException(tableId));

        RelationTableVersion targetVersion = versionRepository.findById(request.getTargetVersionId())
                .orElseThrow(() -> new RelationTableDeploymentException(
                        i18nService.getMessage("admin.rt.target_version_not_found", request.getTargetVersionId())));

        // Verify the version belongs to this table
        if (!targetVersion.getTableDefinition().getId().equals(tableId)) {
            throw new RelationTableDeploymentException(
                    i18nService.getMessage("admin.rt.version_not_belongs_to_table",
                            request.getTargetVersionId(), tableId));
        }

        // Restore field definitions from snapshot data
        List<RelationFieldDTO> snapshotFields = parseSnapshotData(targetVersion.getSnapshotData());

        // Clear current field definitions and replace with snapshot
        tableDefinition.getFieldDefinitions().clear();
        List<RelationFieldDefinition> restoredFields = new ArrayList<>();
        for (int i = 0; i < snapshotFields.size(); i++) {
            RelationFieldDTO dto = snapshotFields.get(i);
            RelationFieldDefinition field = RelationFieldDefinition.builder()
                    .tableDefinition(tableDefinition)
                    .fieldName(dto.getFieldName())
                    .dataType(dto.getDataType())
                    .length(dto.getLength())
                    .precision(dto.getPrecision())
                    .scale(dto.getScale())
                    .nullable(dto.getNullable() != null ? dto.getNullable() : true)
                    .isPrimaryKey(dto.getIsPrimaryKey() != null ? dto.getIsPrimaryKey() : false)
                    .defaultValue(dto.getDefaultValue())
                    .displayName(dto.getDisplayName())
                    .sortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : i)
                    .build();
            restoredFields.add(field);
        }
        tableDefinition.getFieldDefinitions().addAll(restoredFields);

        // Bump version number and persist rollback snapshot
        int newVersion = tableDefinition.getCurrentVersion() + 1;
        String currentUser = SecurityContextUtils.getCurrentUsername().orElse("system");

        RelationTableVersion rollbackVersion = RelationTableVersion.builder()
                .tableDefinition(tableDefinition)
                .versionNumber(newVersion)
                .snapshotData(targetVersion.getSnapshotData())
                .deployedBy(currentUser)
                .deployedAt(Instant.now())
                .changeLog("Rollback to version " + targetVersion.getVersionNumber())
                .build();
        versionRepository.save(rollbackVersion);

        // Set table status to ROLLBACK
        tableDefinition.setStatus(RelationTableStatus.ROLLBACK);
        tableDefinition.setCurrentVersion(newVersion);
        RelationTableDefinition saved = tableDefinitionRepository.save(tableDefinition);

        log.info("Rolled back relation table: id={}, newVersion={}, targetVersion={}",
                tableId, newVersion, targetVersion.getVersionNumber());
        return RelationTableResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RelationTableVersionResponse> getVersionHistory(Long tableId) {
        // Verify table exists
        if (!tableDefinitionRepository.existsById(tableId)) {
            throw new RelationTableNotFoundException(tableId);
        }

        return versionRepository.findByTableDefinitionIdOrderByVersionNumberDesc(tableId).stream()
                .map(RelationTableVersionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // ==================== DDL generation ====================

    /**
     * Builds a CREATE TABLE DDL statement.
     */
    String generateCreateTableDdl(String tableName, List<RelationFieldDefinition> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(quoteIdentifier(tableName)).append(" (\n");

        List<String> columnDefs = new ArrayList<>();
        List<String> primaryKeys = new ArrayList<>();

        for (RelationFieldDefinition field : fields) {
            StringBuilder colDef = new StringBuilder();
            colDef.append("  ").append(quoteIdentifier(field.getFieldName()));
            colDef.append(" ").append(mapDataType(field));

            if (Boolean.FALSE.equals(field.getNullable())) {
                colDef.append(" NOT NULL");
            }

            if (field.getDefaultValue() != null && !field.getDefaultValue().isEmpty()) {
                colDef.append(" DEFAULT ").append(field.getDefaultValue());
            }

            columnDefs.add(colDef.toString());

            if (Boolean.TRUE.equals(field.getIsPrimaryKey())) {
                primaryKeys.add(quoteIdentifier(field.getFieldName()));
            }
        }

        sb.append(String.join(",\n", columnDefs));

        if (!primaryKeys.isEmpty()) {
            sb.append(",\n  PRIMARY KEY (").append(String.join(", ", primaryKeys)).append(")");
        }

        sb.append("\n)");
        return sb.toString();
    }

    /**
     * Builds a list of ALTER TABLE DDL statements by diffing current field definitions
     * against the latest version snapshot (add/remove/rename/type changes).
     */
    List<String> generateAlterTableDdl(String tableName, List<RelationFieldDefinition> currentFields) {
        List<String> ddls = new ArrayList<>();
        String quotedTable = quoteIdentifier(tableName);

        // Load prior version snapshot metadata
        RelationTableDefinition tableDef = tableDefinitionRepository.findByTableName(tableName)
                .orElseThrow(() -> new RelationTableDeploymentException(
                        i18nService.getMessage("admin.rt.table_definition_not_found", tableName)));

        RelationTableVersion latestVersion = versionRepository.findLatestVersion(tableDef.getId())
                .orElse(null);

        if (latestVersion == null) {
            // No prior version — treat as first deployment
            return List.of(generateCreateTableDdl(tableName, currentFields));
        }

        List<RelationFieldDTO> previousFields = parseSnapshotData(latestVersion.getSnapshotData());

        // Build maps by field name
        var previousFieldMap = previousFields.stream()
                .collect(Collectors.toMap(RelationFieldDTO::getFieldName, f -> f));
        var currentFieldMap = currentFields.stream()
                .collect(Collectors.toMap(RelationFieldDefinition::getFieldName, f -> f));

        // Detect renames: same id, different fieldName
        Map<String, String> renamedFields = new HashMap<>(); // oldName -> newName
        if (previousFields.stream().anyMatch(f -> f.getId() != null)) {
            var previousById = previousFields.stream()
                    .filter(f -> f.getId() != null)
                    .collect(Collectors.toMap(RelationFieldDTO::getId, f -> f, (a, b) -> a));
            for (RelationFieldDefinition current : currentFields) {
                if (current.getId() != null && previousById.containsKey(current.getId())) {
                    RelationFieldDTO prev = previousById.get(current.getId());
                    if (!current.getFieldName().equals(prev.getFieldName())) {
                        renamedFields.put(prev.getFieldName(), current.getFieldName());
                        ddls.add("ALTER TABLE " + quotedTable + " RENAME COLUMN " +
                                quoteIdentifier(prev.getFieldName()) + " TO " +
                                quoteIdentifier(current.getFieldName()));
                    }
                }
            }
        }

        // Add new columns (excluding renames)
        for (RelationFieldDefinition field : currentFields) {
            if (!previousFieldMap.containsKey(field.getFieldName()) && !renamedFields.containsValue(field.getFieldName())) {
                String colDef = "ALTER TABLE " + quotedTable + " ADD COLUMN " +
                        quoteIdentifier(field.getFieldName()) + " " + mapDataType(field);
                // For NOT NULL columns on tables with existing data, must provide a DEFAULT
                boolean isNotNull = Boolean.FALSE.equals(field.getNullable());
                boolean hasDefault = field.getDefaultValue() != null && !field.getDefaultValue().isEmpty();
                if (isNotNull && !hasDefault) {
                    colDef += " DEFAULT " + getTypeDefault(field) + " NOT NULL";
                } else {
                    if (isNotNull) colDef += " NOT NULL";
                    if (hasDefault) colDef += " DEFAULT " + field.getDefaultValue();
                }
                ddls.add(colDef);
            }
        }

        // Drop removed columns (excluding renames)
        for (RelationFieldDTO prevField : previousFields) {
            if (!currentFieldMap.containsKey(prevField.getFieldName()) && !renamedFields.containsKey(prevField.getFieldName())) {
                ddls.add("ALTER TABLE " + quotedTable + " DROP COLUMN " +
                        quoteIdentifier(prevField.getFieldName()));
            }
        }

        // Alter columns whose type or size changed
        for (RelationFieldDefinition field : currentFields) {
            RelationFieldDTO prevField = previousFieldMap.get(field.getFieldName());
            if (prevField != null && isFieldChanged(field, prevField)) {
                ddls.add("ALTER TABLE " + quotedTable + " ALTER COLUMN " +
                        quoteIdentifier(field.getFieldName()) + " TYPE " + mapDataType(field));
            }
        }

        return ddls;
    }

    // ==================== Helpers ====================

    /**
     * Maps a logical field type to a PostgreSQL DDL type string.
     */
    String mapDataType(RelationFieldDefinition field) {
        return switch (field.getDataType()) {
            case VARCHAR -> "VARCHAR" + (field.getLength() != null ? "(" + field.getLength() + ")" : "(255)");
            case INTEGER -> "INTEGER";
            case BIGINT -> "BIGINT";
            case DECIMAL -> {
                int p = field.getPrecision() != null ? field.getPrecision() : 10;
                int s = field.getScale() != null ? field.getScale() : 2;
                yield "DECIMAL(" + p + "," + s + ")";
            }
            case BOOLEAN -> "BOOLEAN";
            case DATE -> "DATE";
            case TIMESTAMP -> "TIMESTAMP";
            case TEXT -> "TEXT";
            case JSON -> "JSONB";
            case TIME -> "TIME";
            case BYTEA -> "BYTEA";
            case FILE -> "VARCHAR(" + (field.getLength() != null ? field.getLength() : 500) + ")";
        };
    }

    /**
     * Default literal/expression per type when adding a NOT NULL column to a non-empty table.
     */
    private String getTypeDefault(RelationFieldDefinition field) {
        return switch (field.getDataType()) {
            case VARCHAR, TEXT -> "''";
            case INTEGER, BIGINT, DECIMAL -> "0";
            case BOOLEAN -> "false";
            case DATE -> "CURRENT_DATE";
            case TIMESTAMP -> "CURRENT_TIMESTAMP";
            case JSON -> "'{}'::jsonb";
            case TIME -> "'00:00:00'::time";
            case BYTEA -> "''::bytea";
            case FILE -> "''";
        };
    }

    /**
     * Returns whether the column definition differs from the previous snapshot field.
     */
    private boolean isFieldChanged(RelationFieldDefinition current, RelationFieldDTO previous) {
        if (current.getDataType() != previous.getDataType()) {
            return true;
        }
        if (!objectsEqual(current.getLength(), previous.getLength())) {
            return true;
        }
        if (!objectsEqual(current.getPrecision(), previous.getPrecision())) {
            return true;
        }
        if (!objectsEqual(current.getScale(), previous.getScale())) {
            return true;
        }
        return false;
    }

    private boolean objectsEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /**
     * Ensures audit columns exist (created_at, created_by, updated_at, updated_by);
     * appends missing ones and persists when any were added.
     */
    private void ensureAuditFields(RelationTableDefinition tableDefinition, List<RelationFieldDefinition> fields) {
        Set<String> existingNames = fields.stream()
                .map(RelationFieldDefinition::getFieldName)
                .collect(Collectors.toSet());

        int nextSortOrder = fields.stream()
                .mapToInt(RelationFieldDefinition::getSortOrder)
                .max().orElse(-1) + 1;

        boolean added = false;

        if (!existingNames.contains("created_at")) {
            fields.add(RelationFieldDefinition.builder()
                    .tableDefinition(tableDefinition).fieldName("created_at")
                    .dataType(RelationDataType.TIMESTAMP).nullable(true).isPrimaryKey(false)
                    .displayName("Created At").sortOrder(nextSortOrder++).build());
            added = true;
        }
        if (!existingNames.contains("created_by")) {
            fields.add(RelationFieldDefinition.builder()
                    .tableDefinition(tableDefinition).fieldName("created_by")
                    .dataType(RelationDataType.VARCHAR).length(64).nullable(true).isPrimaryKey(false)
                    .displayName("Created By").sortOrder(nextSortOrder++).build());
            added = true;
        }
        if (!existingNames.contains("updated_at")) {
            fields.add(RelationFieldDefinition.builder()
                    .tableDefinition(tableDefinition).fieldName("updated_at")
                    .dataType(RelationDataType.TIMESTAMP).nullable(true).isPrimaryKey(false)
                    .displayName("Updated At").sortOrder(nextSortOrder++).build());
            added = true;
        }
        if (!existingNames.contains("updated_by")) {
            fields.add(RelationFieldDefinition.builder()
                    .tableDefinition(tableDefinition).fieldName("updated_by")
                    .dataType(RelationDataType.VARCHAR).length(64).nullable(true).isPrimaryKey(false)
                    .displayName("Updated By").sortOrder(nextSortOrder++).build());
            added = true;
        }

        if (added) {
            tableDefinitionRepository.save(tableDefinition);
            log.info("Auto-added audit fields to table: {}", tableDefinition.getTableName());
        }
    }

    /**
     * Read column metadata from information_schema and create / persist
     * RelationFieldDefinition records for a legacy table that has none.
     * Returns the newly saved list so the caller can continue with deployment.
     */
    private List<RelationFieldDefinition> repairFieldDefinitionsFromPhysical(RelationTableDefinition tableDefinition) {
        String tableName = tableDefinition.getTableName();
        String schema    = schemaResolver.getSchema();

        // Fetch PK columns
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

        List<Map<String, Object>> cols = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, udt_name, "
                + "character_maximum_length, numeric_precision, numeric_scale, "
                + "ordinal_position "
                + "FROM information_schema.columns "
                + "WHERE table_name = ? AND table_schema = ? "
                + "ORDER BY ordinal_position",
                tableName, schema);

        List<RelationFieldDefinition> repaired = new ArrayList<>();
        for (Map<String, Object> col : cols) {
            String colName  = (String) col.get("column_name");
            String pgType   = col.get("udt_name") != null
                    ? ((String) col.get("udt_name")).toLowerCase()
                    : ((String) col.getOrDefault("data_type", "varchar")).toLowerCase();
            RelationDataType dt = mapPostgresType(pgType);
            int sortOrder = ((Number) col.get("ordinal_position")).intValue() - 1;

            RelationFieldDefinition field = RelationFieldDefinition.builder()
                    .tableDefinition(tableDefinition)
                    .fieldName(colName)
                    .dataType(dt)
                    .length(col.get("character_maximum_length") != null
                            ? ((Number) col.get("character_maximum_length")).intValue() : null)
                    .precision(col.get("numeric_precision") != null
                            ? ((Number) col.get("numeric_precision")).intValue() : null)
                    .scale(col.get("numeric_scale") != null
                            ? ((Number) col.get("numeric_scale")).intValue() : null)
                    .nullable(true)
                    .isPrimaryKey(pkColumns.contains(colName))
                    .displayName("(auto-recovered from physical schema)")
                    .sortOrder(sortOrder)
                    .build();
            repaired.add(field);
        }

        tableDefinition.setFieldDefinitions(repaired);
        tableDefinitionRepository.save(tableDefinition);
        log.info("Auto-repaired {} field definitions for legacy table '{}'", repaired.size(), tableName);
        return repaired;
    }

    /** Map a PostgreSQL udt_name / data_type string to the application enum. */
    private static RelationDataType mapPostgresType(String pgType) {
        return switch (pgType) {
            case "int2", "int4", "integer"         -> RelationDataType.INTEGER;
            case "int8", "bigint"                  -> RelationDataType.BIGINT;
            case "numeric", "decimal"              -> RelationDataType.DECIMAL;
            case "bool", "boolean"                 -> RelationDataType.BOOLEAN;
            case "date"                            -> RelationDataType.DATE;
            case "timestamp", "timestamptz",
                 "timestamp without time zone",
                 "timestamp with time zone"        -> RelationDataType.TIMESTAMP;
            case "text", "json", "jsonb"           -> RelationDataType.TEXT;
            default                                -> RelationDataType.VARCHAR;  // varchar, bpchar, unknown
        };
    }

    /**
     * Whether a physical table with this name exists in the configured schema.
     */
    private boolean physicalTableExists(String tableName) {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ? AND table_schema = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName, schemaResolver.getSchema());
        return count != null && count > 0;
    }

    /**
     * Builds ALTER TABLE DDL by comparing logical fields to columns that already exist physically
     * (when first deploy overlaps an existing legacy table).
     */
    List<String> generateAlterTableDdlFromPhysical(String tableName, List<RelationFieldDefinition> currentFields) {
        List<String> ddls = new ArrayList<>();
        String quotedTable = quoteIdentifier(tableName);

        // Columns already present on the physical table
        String sql = "SELECT column_name FROM information_schema.columns WHERE table_name = ? AND table_schema = ?";
        List<String> existingColumns = jdbcTemplate.queryForList(sql, String.class, tableName, schemaResolver.getSchema());
        Set<String> existingColumnSet = existingColumns.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        // ADD COLUMN for definitions missing from the physical table
        for (RelationFieldDefinition field : currentFields) {
            if (!existingColumnSet.contains(field.getFieldName().toLowerCase())) {
                String colDef = "ALTER TABLE " + quotedTable + " ADD COLUMN " +
                        quoteIdentifier(field.getFieldName()) + " " + mapDataType(field);
                boolean isNotNull = Boolean.FALSE.equals(field.getNullable());
                boolean hasDefault = field.getDefaultValue() != null && !field.getDefaultValue().isEmpty();
                if (isNotNull && !hasDefault) {
                    colDef += " DEFAULT " + getTypeDefault(field) + " NOT NULL";
                } else {
                    if (isNotNull) colDef += " NOT NULL";
                    if (hasDefault) colDef += " DEFAULT " + field.getDefaultValue();
                }
                ddls.add(colDef);
            }
        }

        return ddls;
    }

    /**
     * Double-quotes an identifier for DDL (reserved words and quoting rules).
     */
    String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    /**
     * Serializes field definitions into version snapshot JSON.
     */
    String createSnapshotData(List<RelationFieldDefinition> fields) {
        List<RelationFieldDTO> fieldDtos = fields.stream()
                .map(f -> RelationFieldDTO.builder()
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

        try {
            return objectMapper.writeValueAsString(fieldDtos);
        } catch (JsonProcessingException e) {
            throw new RelationTableDeploymentException("Failed to serialize snapshot data", e);
        }
    }

    /**
     * Deserializes version snapshot JSON into field DTOs.
     */
    List<RelationFieldDTO> parseSnapshotData(String snapshotData) {
        try {
            return objectMapper.readValue(snapshotData, new TypeReference<List<RelationFieldDTO>>() {});
        } catch (JsonProcessingException e) {
            throw new RelationTableDeploymentException("Failed to parse snapshot data", e);
        }
    }
}
