package com.admin.service.impl;

import com.admin.component.RelationTableFieldMapper;
import com.admin.component.RelationTableFunctionUnitResolver;
import com.admin.dto.request.CreateRelationTableRequest;
import com.admin.dto.request.UpdateRelationTableRequest;
import com.admin.dto.response.RelationTableResponse;
import com.admin.entity.FunctionUnit;
import com.admin.entity.RelationFieldDefinition;
import com.admin.entity.RelationTableDefinition;
import com.admin.exception.FunctionUnitNotFoundException;
import com.admin.exception.RelationTableBindingExistsException;
import com.admin.exception.RelationTableNameDuplicateException;
import com.admin.exception.RelationTableNotFoundException;
import com.admin.entity.RelationTableFunctionUnit;
import com.admin.repository.FunctionUnitRepository;
import com.admin.repository.RelationFieldDefinitionRepository;
import com.admin.repository.RelationTableDefinitionRepository;
import com.admin.repository.RelationTableFunctionUnitRepository;
import com.admin.service.RelationComputedFieldValidator;
import com.admin.service.RelationTableStructureService;
import com.platform.common.enums.RelationDataType;
import com.platform.common.enums.RelationTableStatus;
import com.platform.common.relationtable.RelationTableStructureDiff;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Relation Table 表结构管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelationTableStructureServiceImpl implements RelationTableStructureService {

    private final RelationTableDefinitionRepository tableDefinitionRepository;
    private final RelationFieldDefinitionRepository fieldDefinitionRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final RelationTableFunctionUnitRepository relationTableFunctionUnitRepository;
    private final RelationTableFunctionUnitResolver relationTableFunctionUnitResolver;
    private final RelationComputedFieldValidator computedFieldValidator;
    private final RelationTableFieldMapper relationTableFieldMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public RelationTableResponse createTable(CreateRelationTableRequest request) {
        log.info("Creating relation table: {}", request.getTableName());

        assertTableNameAvailable(request.getTableName(), null);

        // 创建表定义
        RelationTableDefinition tableDefinition = RelationTableDefinition.builder()
                .tableName(request.getTableName())
                .displayName(request.getDisplayName())
                .description(request.getDescription())
                .status(RelationTableStatus.INIT)
                .enabled(true)
                .portalVisible(false)
                .currentVersion(0)
                .build();

        // 创建字段定义
        List<RelationFieldDefinition> fieldDefinitions = new ArrayList<>();
        if (request.getFieldDefinitions() != null) {
            for (int i = 0; i < request.getFieldDefinitions().size(); i++) {
                CreateRelationTableRequest.FieldDefinitionRequest fieldReq = request.getFieldDefinitions().get(i);
                RelationFieldDefinition field = RelationFieldDefinition.builder()
                        .tableDefinition(tableDefinition)
                        .fieldName(fieldReq.getFieldName())
                        .dataType(fieldReq.getDataType())
                        .length(fieldReq.getLength())
                        .precision(fieldReq.getPrecision())
                        .scale(fieldReq.getScale())
                        .nullable(fieldReq.getNullable() != null ? fieldReq.getNullable() : true)
                        .isPrimaryKey(fieldReq.getIsPrimaryKey() != null ? fieldReq.getIsPrimaryKey() : false)
                        .defaultValue(fieldReq.getDefaultValue())
                        .displayName(fieldReq.getDisplayName())
                        .pkGenerationJson(fieldReq.getPkGeneration())
                        .isForeignKey(fieldReq.getIsForeignKey() != null ? fieldReq.getIsForeignKey() : false)
                        .refTableId(fieldReq.getRefTableId())
                        .refPrimaryKeyFields(fieldReq.getRefPrimaryKeyFields())
                        .fkDisplayMode(fieldReq.getFkDisplayMode() != null ? fieldReq.getFkDisplayMode() : "readonly")
                        .lookupConfig(fieldReq.getLookupConfig())
                        .isComputed(Boolean.TRUE.equals(fieldReq.getIsComputed()))
                        .computedFieldJson(Boolean.TRUE.equals(fieldReq.getIsComputed())
                                ? fieldReq.getComputedField() : null)
                        .sortOrder(fieldReq.getSortOrder() != null ? fieldReq.getSortOrder() : i)
                        .build();
                fieldDefinitions.add(field);
            }
        }

        // 自动追加审计字段: created_at, created_by, updated_at, updated_by
        int nextSortOrder = fieldDefinitions.size();
        Set<String> existingFieldNames = fieldDefinitions.stream()
                .map(RelationFieldDefinition::getFieldName)
                .collect(Collectors.toSet());

        if (!existingFieldNames.contains("created_at")) {
            fieldDefinitions.add(RelationFieldDefinition.builder()
                    .tableDefinition(tableDefinition)
                    .fieldName("created_at").dataType(RelationDataType.TIMESTAMP)
                    .nullable(true).isPrimaryKey(false)
                    .displayName("Created At").sortOrder(nextSortOrder++)
                    .build());
        }
        if (!existingFieldNames.contains("created_by")) {
            fieldDefinitions.add(RelationFieldDefinition.builder()
                    .tableDefinition(tableDefinition)
                    .fieldName("created_by").dataType(RelationDataType.VARCHAR).length(64)
                    .nullable(true).isPrimaryKey(false)
                    .displayName("Created By").sortOrder(nextSortOrder++)
                    .build());
        }
        if (!existingFieldNames.contains("updated_at")) {
            fieldDefinitions.add(RelationFieldDefinition.builder()
                    .tableDefinition(tableDefinition)
                    .fieldName("updated_at").dataType(RelationDataType.TIMESTAMP)
                    .nullable(true).isPrimaryKey(false)
                    .displayName("Updated At").sortOrder(nextSortOrder++)
                    .build());
        }
        if (!existingFieldNames.contains("updated_by")) {
            fieldDefinitions.add(RelationFieldDefinition.builder()
                    .tableDefinition(tableDefinition)
                    .fieldName("updated_by").dataType(RelationDataType.VARCHAR).length(64)
                    .nullable(true).isPrimaryKey(false)
                    .displayName("Updated By").sortOrder(nextSortOrder++)
                    .build());
        }

        validateComputedFields(fieldDefinitions);
        tableDefinition.setFieldDefinitions(fieldDefinitions);

        RelationTableDefinition saved = tableDefinitionRepository.save(tableDefinition);
        replaceFunctionUnitLinks(saved.getId(), request.getFunctionUnitIds());
        log.info("Created relation table: id={}, tableName={}", saved.getId(), saved.getTableName());

        return withFunctionUnits(RelationTableResponse.fromEntity(saved));
    }

    @Override
    @Transactional
    public RelationTableResponse updateTable(Long id, UpdateRelationTableRequest request) {
        log.info("Updating relation table: id={}", id);

        RelationTableDefinition tableDefinition = tableDefinitionRepository.findById(id)
                .orElseThrow(() -> new RelationTableNotFoundException(id));

        // 仅当当前状态为 DEPLOYED，且本次改动确实改变了展示名/描述/字段结构时才转 UPDATED；
        // 未改变结构的保存（比如只是打开又原样保存）不应把已部署表打上"待重新部署"标记。
        // 注意：快照必须在下面任何字段被修改之前取——displayName/description 的 setter 和
        // updateFieldDefinitions 都会就地改写实体（updateFieldDefinitions 还会就地改写已有的
        // RelationFieldDefinition 实例，而非替换成新对象），晚取快照会让"之前"的值已经被污染。
        boolean wasDeployed = tableDefinition.getStatus() == RelationTableStatus.DEPLOYED;
        String beforeDisplayName = tableDefinition.getDisplayName();
        String beforeDescription = tableDefinition.getDescription();
        List<Map<String, Object>> currentFieldMaps = wasDeployed
                ? relationTableFieldMapper.fromEntities(tableDefinition.getFieldDefinitions())
                : null;

        // 如果更新了表名，验证唯一性
        if (request.getTableName() != null && !request.getTableName().equals(tableDefinition.getTableName())) {
            assertTableNameAvailable(request.getTableName(), id);
            tableDefinition.setTableName(request.getTableName());
        }

        // 更新基本信息
        if (request.getDisplayName() != null) {
            tableDefinition.setDisplayName(request.getDisplayName());
        }
        if (request.getDescription() != null) {
            tableDefinition.setDescription(request.getDescription());
        }
        // Function Unit assignment is organizational metadata, not deployable structure — reassigning
        // it must not participate in the DEPLOYED→UPDATED diff gate below.
        if (request.getFunctionUnitIds() != null) {
            replaceFunctionUnitLinks(id, request.getFunctionUnitIds());
        }

        if (request.getFieldDefinitions() != null) {
            updateFieldDefinitions(tableDefinition, request.getFieldDefinitions());
        }

        if (wasDeployed) {
            List<Map<String, Object>> incomingFieldMaps = relationTableFieldMapper.fromEntities(tableDefinition.getFieldDefinitions());
            boolean unchanged = RelationTableStructureDiff.unchanged(
                    beforeDisplayName, beforeDescription, currentFieldMaps,
                    tableDefinition.getDisplayName(), tableDefinition.getDescription(), incomingFieldMaps);
            if (!unchanged) {
                tableDefinition.setStatus(RelationTableStatus.UPDATED);
            }
        }

        RelationTableDefinition saved = tableDefinitionRepository.save(tableDefinition);
        log.info("Updated relation table: id={}, tableName={}", saved.getId(), saved.getTableName());

        return withFunctionUnits(RelationTableResponse.fromEntity(saved));
    }

    @Override
    @Transactional
    public void deleteTable(Long id) {
        log.info("Deleting relation table: id={}", id);

        RelationTableDefinition tableDefinition = tableDefinitionRepository.findById(id)
                .orElseThrow(() -> new RelationTableNotFoundException(id));

        // 检查是否有绑定关系（查询 developer-workstation 的 dw_form_table_bindings 表）
        if (hasBindings(tableDefinition.getTableName())) {
            throw new RelationTableBindingExistsException(id);
        }

        tableDefinitionRepository.delete(tableDefinition);
        log.info("Deleted relation table: id={}, tableName={}", id, tableDefinition.getTableName());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RelationTableResponse> getTableList() {
        List<RelationTableDefinition> tables = tableDefinitionRepository.findAll();
        List<Long> tableIds = tables.stream().map(RelationTableDefinition::getId).toList();
        Map<Long, List<RelationTableFunctionUnit>> linksByTable = relationTableFunctionUnitResolver.loadLinksByTable(tableIds);
        Map<String, FunctionUnit> functionUnitsById = relationTableFunctionUnitResolver.loadFunctionUnitsById(linksByTable);
        return tables.stream()
                .map(RelationTableResponse::fromEntity)
                .peek(r -> r.applyFunctionUnits(relationTableFunctionUnitResolver.resolve(linksByTable.get(r.getId()), functionUnitsById)))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public RelationTableResponse getTableById(Long id) {
        RelationTableDefinition tableDefinition = tableDefinitionRepository.findById(id)
                .orElseThrow(() -> new RelationTableNotFoundException(id));
        return withFunctionUnits(RelationTableResponse.fromEntity(tableDefinition));
    }

    private RelationTableResponse withFunctionUnits(RelationTableResponse response) {
        response.applyFunctionUnits(relationTableFunctionUnitResolver.resolveOne(response.getId()));
        return response;
    }

    /**
     * Replaces the full set of Function Unit links for a table. Empty/null list clears to Common.
     */
    private void replaceFunctionUnitLinks(Long tableId, List<String> functionUnitIds) {
        relationTableFunctionUnitRepository.deleteByRelationTableId(tableId);
        if (functionUnitIds == null || functionUnitIds.isEmpty()) {
            return;
        }
        List<String> distinctIds = functionUnitIds.stream().distinct().toList();
        for (String fuId : distinctIds) {
            if (!functionUnitRepository.existsById(fuId)) {
                throw new FunctionUnitNotFoundException(fuId);
            }
        }
        List<RelationTableFunctionUnit> links = distinctIds.stream()
                .map(fuId -> RelationTableFunctionUnit.builder()
                        .id(java.util.UUID.randomUUID().toString())
                        .relationTableId(tableId)
                        .functionUnitId(fuId)
                        .build())
                .toList();
        relationTableFunctionUnitRepository.saveAll(links);
    }

    @Override
    @Transactional
    public RelationTableResponse toggleEnabled(Long id, Boolean enabled) {
        log.info("Toggling enabled for relation table: id={}, enabled={}", id, enabled);

        RelationTableDefinition tableDefinition = tableDefinitionRepository.findById(id)
                .orElseThrow(() -> new RelationTableNotFoundException(id));

        tableDefinition.setEnabled(enabled);
        RelationTableDefinition saved = tableDefinitionRepository.save(tableDefinition);

        log.info("Toggled enabled for relation table: id={}, enabled={}", id, enabled);
        return withFunctionUnits(RelationTableResponse.fromEntity(saved));
    }

    @Override
    @Transactional
    public RelationTableResponse togglePortalVisibility(Long id, Boolean portalVisible) {
        log.info("Toggling portal visibility for relation table: id={}, portalVisible={}", id, portalVisible);

        RelationTableDefinition tableDefinition = tableDefinitionRepository.findById(id)
                .orElseThrow(() -> new RelationTableNotFoundException(id));

        tableDefinition.setPortalVisible(portalVisible);
        RelationTableDefinition saved = tableDefinitionRepository.save(tableDefinition);

        log.info("Toggled portal visibility for relation table: id={}, portalVisible={}", id, portalVisible);
        return withFunctionUnits(RelationTableResponse.fromEntity(saved));
    }

    /**
     * 更新字段定义列表
     * 支持新增、更新和删除字段
     */
    private void updateFieldDefinitions(RelationTableDefinition tableDefinition,
                                         List<UpdateRelationTableRequest.FieldDefinitionRequest> fieldRequests) {
        // 构建现有字段的 ID 映射
        Map<Long, RelationFieldDefinition> existingFieldMap = tableDefinition.getFieldDefinitions().stream()
                .filter(f -> f.getId() != null)
                .collect(Collectors.toMap(RelationFieldDefinition::getId, Function.identity()));

        List<RelationFieldDefinition> updatedFields = new ArrayList<>();

        for (int i = 0; i < fieldRequests.size(); i++) {
            UpdateRelationTableRequest.FieldDefinitionRequest fieldReq = fieldRequests.get(i);

            if (fieldReq.getId() != null && existingFieldMap.containsKey(fieldReq.getId())) {
                // 更新已有字段
                RelationFieldDefinition existing = existingFieldMap.get(fieldReq.getId());
                if (fieldReq.getFieldName() != null) {
                    existing.setFieldName(fieldReq.getFieldName());
                }
                if (fieldReq.getDataType() != null) {
                    existing.setDataType(fieldReq.getDataType());
                }
                if (fieldReq.getLength() != null) {
                    existing.setLength(fieldReq.getLength());
                }
                if (fieldReq.getPrecision() != null) {
                    existing.setPrecision(fieldReq.getPrecision());
                }
                if (fieldReq.getScale() != null) {
                    existing.setScale(fieldReq.getScale());
                }
                if (fieldReq.getNullable() != null) {
                    existing.setNullable(fieldReq.getNullable());
                }
                if (fieldReq.getIsPrimaryKey() != null) {
                    existing.setIsPrimaryKey(fieldReq.getIsPrimaryKey());
                }
                if (fieldReq.getDefaultValue() != null) {
                    existing.setDefaultValue(fieldReq.getDefaultValue());
                }
                if (fieldReq.getDisplayName() != null) {
                    existing.setDisplayName(fieldReq.getDisplayName());
                }
                if (fieldReq.getIsPrimaryKey() != null && !Boolean.TRUE.equals(fieldReq.getIsPrimaryKey())) {
                    existing.setPkGenerationJson(null);
                } else if (fieldReq.getPkGeneration() != null) {
                    existing.setPkGenerationJson(fieldReq.getPkGeneration());
                }
                if (fieldReq.getIsForeignKey() != null) {
                    existing.setIsForeignKey(fieldReq.getIsForeignKey());
                    if (Boolean.TRUE.equals(fieldReq.getIsForeignKey())) {
                        existing.setRefTableId(fieldReq.getRefTableId());
                        existing.setRefPrimaryKeyFields(fieldReq.getRefPrimaryKeyFields());
                        existing.setFkDisplayMode(fieldReq.getFkDisplayMode() != null ? fieldReq.getFkDisplayMode() : "readonly");
                    } else {
                        existing.setRefTableId(null);
                        existing.setRefPrimaryKeyFields(null);
                    }
                }
                // LOOKUP config: keep only for LOOKUP columns, clear when switched away.
                existing.setLookupConfig(
                        RelationDataType.LOOKUP.equals(existing.getDataType()) ? fieldReq.getLookupConfig() : null);
                if (fieldReq.getIsComputed() != null) {
                    existing.setIsComputed(fieldReq.getIsComputed());
                }
                // Formula and flag move together: clearing the flag must not leave a stale formula
                // behind for the recalculator to find.
                if (Boolean.TRUE.equals(existing.getIsComputed())) {
                    if (fieldReq.getComputedField() != null) {
                        existing.setComputedFieldJson(fieldReq.getComputedField());
                    }
                } else {
                    existing.setComputedFieldJson(null);
                }
                existing.setSortOrder(fieldReq.getSortOrder() != null ? fieldReq.getSortOrder() : i);
                updatedFields.add(existing);
            } else {
                // 新增字段
                RelationFieldDefinition newField = RelationFieldDefinition.builder()
                        .tableDefinition(tableDefinition)
                        .fieldName(fieldReq.getFieldName())
                        .dataType(fieldReq.getDataType())
                        .length(fieldReq.getLength())
                        .precision(fieldReq.getPrecision())
                        .scale(fieldReq.getScale())
                        .nullable(fieldReq.getNullable() != null ? fieldReq.getNullable() : true)
                        .isPrimaryKey(fieldReq.getIsPrimaryKey() != null ? fieldReq.getIsPrimaryKey() : false)
                        .defaultValue(fieldReq.getDefaultValue())
                        .displayName(fieldReq.getDisplayName())
                        .pkGenerationJson(fieldReq.getPkGeneration())
                        .isForeignKey(fieldReq.getIsForeignKey() != null ? fieldReq.getIsForeignKey() : false)
                        .refTableId(fieldReq.getRefTableId())
                        .refPrimaryKeyFields(fieldReq.getRefPrimaryKeyFields())
                        .fkDisplayMode(fieldReq.getFkDisplayMode() != null ? fieldReq.getFkDisplayMode() : "readonly")
                        .lookupConfig(fieldReq.getLookupConfig())
                        .isComputed(Boolean.TRUE.equals(fieldReq.getIsComputed()))
                        .computedFieldJson(Boolean.TRUE.equals(fieldReq.getIsComputed())
                                ? fieldReq.getComputedField() : null)
                        .sortOrder(fieldReq.getSortOrder() != null ? fieldReq.getSortOrder() : i)
                        .build();
                updatedFields.add(newField);
            }
        }

        validateComputedFields(updatedFields);

        // 使用 orphanRemoval 自动删除不在新列表中的字段
        tableDefinition.getFieldDefinitions().clear();
        tableDefinition.getFieldDefinitions().addAll(updatedFields);
    }

    /**
     * Validates the computed fields of a table structure that is about to be persisted.
     *
     * <p>Runs against the merged entity list rather than the request, because an update request is
     * a partial patch: a column whose data type is untouched arrives as null, and validating that
     * would compare the formula's result against nothing.
     */
    private void validateComputedFields(List<RelationFieldDefinition> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        List<RelationComputedFieldValidator.IncomingField> incoming = new ArrayList<>(fields.size());
        for (RelationFieldDefinition field : fields) {
            if (field == null) {
                continue;
            }
            incoming.add(new RelationComputedFieldValidator.IncomingField(
                    field.getFieldName(),
                    field.getDataType(),
                    field.getIsComputed(),
                    field.getComputedFieldJson(),
                    field.getIsPrimaryKey(),
                    field.getIsForeignKey(),
                    field.getDefaultValue(),
                    field.getPkGenerationJson()));
        }
        computedFieldValidator.validateIncomingFields(incoming);
    }

    /**
     * 检查 Relation Table 是否有绑定关系
     * 通过查询 developer-workstation 的 dw_form_table_bindings 表
     * 其中 binding_type = 'RELATED' 且 table 的 table_name 匹配
     */
    private boolean hasBindings(String tableName) {
        try {
            String sql = "SELECT COUNT(*) FROM dw_form_table_bindings ftb " +
                    "JOIN dw_table_definitions td ON ftb.table_id = td.id " +
                    "WHERE ftb.binding_type = 'RELATED' AND td.table_name = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("Failed to check bindings for table '{}': {}", tableName, e.getMessage());
            // If the table doesn't exist yet or query fails, assume no bindings
            return false;
        }
    }

    @Override
    public boolean isTableNameAvailable(String tableName, Long excludeTableId) {
        if (tableName == null || tableName.isBlank()) {
            return false;
        }
        return !isTableNameTaken(tableName, excludeTableId);
    }

    private void assertTableNameAvailable(String tableName, Long excludeTableId) {
        if (isTableNameTaken(tableName, excludeTableId)) {
            throw new RelationTableNameDuplicateException(tableName);
        }
    }

    private boolean isTableNameTaken(String tableName, Long excludeTableId) {
        if (excludeTableId != null) {
            if (tableDefinitionRepository.existsByTableNameIgnoreCaseAndIdNot(tableName, excludeTableId)) {
                return true;
            }
        } else if (tableDefinitionRepository.existsByTableNameIgnoreCase(tableName)) {
            return true;
        }
        return existsInDwTables(tableName);
    }

    /**
     * Case-insensitive: Postgres unquoted DDL folds identifiers to lowercase, so a case-only-different
     * name here would still collide with an existing Table Design table at the physical layer.
     */
    private boolean existsInDwTables(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dw_table_definitions WHERE lower(table_name) = lower(?)",
                Integer.class,
                tableName);
        return count != null && count > 0;
    }
}
