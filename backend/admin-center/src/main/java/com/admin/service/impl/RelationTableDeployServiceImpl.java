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
import com.admin.repository.RelationTableDefinitionRepository;
import com.admin.repository.RelationTableVersionRepository;
import com.admin.service.RelationTableDeployService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.RelationFieldDTO;
import com.platform.common.enums.RelationDataType;
import com.platform.common.enums.RelationTableStatus;
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
 * Relation Table 部署与回滚服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelationTableDeployServiceImpl implements RelationTableDeployService {

    private final RelationTableDefinitionRepository tableDefinitionRepository;
    private final RelationTableVersionRepository versionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DatabaseSchemaResolver schemaResolver;

    @Override
    @Transactional
    public RelationTableResponse deploy(Long tableId) {
        log.info("Deploying relation table: id={}", tableId);

        RelationTableDefinition tableDefinition = tableDefinitionRepository.findById(tableId)
                .orElseThrow(() -> new RelationTableNotFoundException(tableId));

        List<RelationFieldDefinition> fields = tableDefinition.getFieldDefinitions();
        if (fields == null || fields.isEmpty()) {
            throw new RelationTableDeploymentException("表 '" + tableDefinition.getTableName() + "' 没有定义任何字段");
        }

        // 自动补齐审计字段（如果尚未定义）
        ensureAuditFields(tableDefinition, fields);

        boolean isFirstDeploy = tableDefinition.getCurrentVersion() == 0;

        try {
            if (isFirstDeploy) {
                // 检查物理表是否已存在（可能是上次部署失败后遗留的）
                boolean tableExists = physicalTableExists(tableDefinition.getTableName());
                if (!tableExists) {
                    String createDdl = generateCreateTableDdl(tableDefinition.getTableName(), fields);
                    log.info("Executing CREATE TABLE DDL for table: {}", tableDefinition.getTableName());
                    jdbcTemplate.execute(createDdl);
                } else {
                    log.info("Physical table '{}' already exists, applying ALTER TABLE instead", tableDefinition.getTableName());
                    List<String> alterDdls = generateAlterTableDdlFromPhysical(tableDefinition.getTableName(), fields);
                    for (String ddl : alterDdls) {
                        log.info("Executing ALTER TABLE DDL: {}", ddl);
                        jdbcTemplate.execute(ddl);
                    }
                }
            } else {
                List<String> alterDdls = generateAlterTableDdl(tableDefinition.getTableName(), fields);
                for (String ddl : alterDdls) {
                    log.info("Executing ALTER TABLE DDL: {}", ddl);
                    jdbcTemplate.execute(ddl);
                }
            }
        } catch (Exception e) {
            log.error("DDL execution failed for table '{}': {}", tableDefinition.getTableName(), e.getMessage(), e);
            throw new RelationTableDeploymentException(
                    "部署表 '" + tableDefinition.getTableName() + "' 失败: " + e.getMessage(), e);
        }

        // 创建版本快照
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

        // 更新表状态和版本号
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
                        "目标版本不存在: " + request.getTargetVersionId()));

        // 验证版本属于该表
        if (!targetVersion.getTableDefinition().getId().equals(tableId)) {
            throw new RelationTableDeploymentException(
                    "版本 " + request.getTargetVersionId() + " 不属于表 " + tableId);
        }

        // 从快照数据恢复字段定义
        List<RelationFieldDTO> snapshotFields = parseSnapshotData(targetVersion.getSnapshotData());

        // 清除当前字段定义并用快照数据覆盖
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
                    .comment(dto.getComment())
                    .sortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : i)
                    .build();
            restoredFields.add(field);
        }
        tableDefinition.getFieldDefinitions().addAll(restoredFields);

        // 生成新版本号并创建回滚版本快照
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

        // 更新表状态为 ROLLBACK
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
        // 验证表存在
        if (!tableDefinitionRepository.existsById(tableId)) {
            throw new RelationTableNotFoundException(tableId);
        }

        return versionRepository.findByTableDefinitionIdOrderByVersionNumberDesc(tableId).stream()
                .map(RelationTableVersionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // ==================== DDL 生成 ====================

    /**
     * 生成 CREATE TABLE DDL
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
     * 生成 ALTER TABLE DDL 列表
     * 比较当前字段定义与上一版本快照，生成增删改列的 DDL
     */
    List<String> generateAlterTableDdl(String tableName, List<RelationFieldDefinition> currentFields) {
        List<String> ddls = new ArrayList<>();
        String quotedTable = quoteIdentifier(tableName);

        // 获取上一版本的快照数据
        RelationTableDefinition tableDef = tableDefinitionRepository.findByTableName(tableName)
                .orElseThrow(() -> new RelationTableDeploymentException("表定义不存在: " + tableName));

        RelationTableVersion latestVersion = versionRepository.findLatestVersion(tableDef.getId())
                .orElse(null);

        if (latestVersion == null) {
            // 没有历史版本，当作首次部署
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

        // 新增的字段（排除重命名的）
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

        // 删除的字段（排除重命名的）
        for (RelationFieldDTO prevField : previousFields) {
            if (!currentFieldMap.containsKey(prevField.getFieldName()) && !renamedFields.containsKey(prevField.getFieldName())) {
                ddls.add("ALTER TABLE " + quotedTable + " DROP COLUMN " +
                        quoteIdentifier(prevField.getFieldName()));
            }
        }

        // 修改的字段（类型或长度变化）
        for (RelationFieldDefinition field : currentFields) {
            RelationFieldDTO prevField = previousFieldMap.get(field.getFieldName());
            if (prevField != null && isFieldChanged(field, prevField)) {
                ddls.add("ALTER TABLE " + quotedTable + " ALTER COLUMN " +
                        quoteIdentifier(field.getFieldName()) + " TYPE " + mapDataType(field));
            }
        }

        return ddls;
    }

    // ==================== 辅助方法 ====================

    /**
     * 映射数据类型到 PostgreSQL DDL 类型
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
        };
    }

    /**
     * 获取数据类型的默认值（用于 NOT NULL 列添加到已有数据的表时）
     */
    private String getTypeDefault(RelationFieldDefinition field) {
        return switch (field.getDataType()) {
            case VARCHAR, TEXT -> "''";
            case INTEGER, BIGINT, DECIMAL -> "0";
            case BOOLEAN -> "false";
            case DATE -> "CURRENT_DATE";
            case TIMESTAMP -> "CURRENT_TIMESTAMP";
        };
    }

    /**
     * 检查字段是否发生变化
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
     * 确保表定义中包含审计字段: created_at, created_by, updated_at, updated_by
     * 如果缺少则自动追加并持久化
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
                    .comment("Created At").sortOrder(nextSortOrder++).build());
            added = true;
        }
        if (!existingNames.contains("created_by")) {
            fields.add(RelationFieldDefinition.builder()
                    .tableDefinition(tableDefinition).fieldName("created_by")
                    .dataType(RelationDataType.VARCHAR).length(64).nullable(true).isPrimaryKey(false)
                    .comment("Created By").sortOrder(nextSortOrder++).build());
            added = true;
        }
        if (!existingNames.contains("updated_at")) {
            fields.add(RelationFieldDefinition.builder()
                    .tableDefinition(tableDefinition).fieldName("updated_at")
                    .dataType(RelationDataType.TIMESTAMP).nullable(true).isPrimaryKey(false)
                    .comment("Updated At").sortOrder(nextSortOrder++).build());
            added = true;
        }
        if (!existingNames.contains("updated_by")) {
            fields.add(RelationFieldDefinition.builder()
                    .tableDefinition(tableDefinition).fieldName("updated_by")
                    .dataType(RelationDataType.VARCHAR).length(64).nullable(true).isPrimaryKey(false)
                    .comment("Updated By").sortOrder(nextSortOrder++).build());
            added = true;
        }

        if (added) {
            tableDefinitionRepository.save(tableDefinition);
            log.info("Auto-added audit fields to table: {}", tableDefinition.getTableName());
        }
    }

    /**
     * 检查物理表是否已存在于数据库中
     */
    private boolean physicalTableExists(String tableName) {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ? AND table_schema = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName, schemaResolver.getSchema());
        return count != null && count > 0;
    }

    /**
     * 根据物理表的实际列信息生成 ALTER TABLE DDL（用于首次部署但物理表已存在的场景）
     */
    List<String> generateAlterTableDdlFromPhysical(String tableName, List<RelationFieldDefinition> currentFields) {
        List<String> ddls = new ArrayList<>();
        String quotedTable = quoteIdentifier(tableName);

        // 查询物理表已有的列名
        String sql = "SELECT column_name FROM information_schema.columns WHERE table_name = ? AND table_schema = ?";
        List<String> existingColumns = jdbcTemplate.queryForList(sql, String.class, tableName, schemaResolver.getSchema());
        Set<String> existingColumnSet = existingColumns.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        // 添加缺失的列
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
     * 引用标识符（防止 SQL 注入和保留字冲突）
     */
    String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    /**
     * 创建版本快照 JSON 数据
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
                        .comment(f.getComment())
                        .sortOrder(f.getSortOrder())
                        .build())
                .collect(Collectors.toList());

        try {
            return objectMapper.writeValueAsString(fieldDtos);
        } catch (JsonProcessingException e) {
            throw new RelationTableDeploymentException("序列化快照数据失败", e);
        }
    }

    /**
     * 解析版本快照 JSON 数据
     */
    List<RelationFieldDTO> parseSnapshotData(String snapshotData) {
        try {
            return objectMapper.readValue(snapshotData, new TypeReference<List<RelationFieldDTO>>() {});
        } catch (JsonProcessingException e) {
            throw new RelationTableDeploymentException("解析快照数据失败", e);
        }
    }
}
