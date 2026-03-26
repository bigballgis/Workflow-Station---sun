package com.admin.service.impl;

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
import com.platform.common.enums.RelationTableStatus;
import com.platform.security.util.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

        boolean isFirstDeploy = tableDefinition.getCurrentVersion() == 0;

        try {
            if (isFirstDeploy) {
                String createDdl = generateCreateTableDdl(tableDefinition.getTableName(), fields);
                log.info("Executing CREATE TABLE DDL for table: {}", tableDefinition.getTableName());
                jdbcTemplate.execute(createDdl);
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

        // 构建字段名映射
        var previousFieldMap = previousFields.stream()
                .collect(Collectors.toMap(RelationFieldDTO::getFieldName, f -> f));
        var currentFieldMap = currentFields.stream()
                .collect(Collectors.toMap(RelationFieldDefinition::getFieldName, f -> f));

        // 新增的字段
        for (RelationFieldDefinition field : currentFields) {
            if (!previousFieldMap.containsKey(field.getFieldName())) {
                ddls.add("ALTER TABLE " + quotedTable + " ADD COLUMN " +
                        quoteIdentifier(field.getFieldName()) + " " + mapDataType(field) +
                        (Boolean.FALSE.equals(field.getNullable()) ? " NOT NULL" : "") +
                        (field.getDefaultValue() != null && !field.getDefaultValue().isEmpty()
                                ? " DEFAULT " + field.getDefaultValue() : ""));
            }
        }

        // 删除的字段
        for (RelationFieldDTO prevField : previousFields) {
            if (!currentFieldMap.containsKey(prevField.getFieldName())) {
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
