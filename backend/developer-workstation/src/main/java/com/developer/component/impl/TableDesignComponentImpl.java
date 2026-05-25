package com.developer.component.impl;

import com.developer.component.TableDesignComponent;
import com.developer.dto.FieldDefinitionRequest;
import com.developer.dto.ForeignKeyDTO;
import com.developer.dto.TableDefinitionRequest;
import com.developer.dto.ValidationResult;
import com.developer.entity.FieldDefinition;
import com.developer.entity.ForeignKey;
import com.developer.entity.FunctionUnit;
import com.developer.entity.TableDefinition;
import com.developer.enums.DataType;
import com.developer.enums.DatabaseDialect;
import com.developer.exception.DeveloperBusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.entity.FormDefinition;
import com.developer.repository.*;
import com.developer.service.FormConfigFieldRenamer;
import com.developer.util.DeveloperWorkstationSequenceSynchronizer;
import com.platform.common.i18n.I18nService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 表设计组件实现
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TableDesignComponentImpl implements TableDesignComponent {
    
    // DOS 防护: 字段定义数量限制
    private static final int MAX_FIELD_DEFINITIONS = 200;

    private final TableDefinitionRepository tableDefinitionRepository;
    private final FieldDefinitionRepository fieldDefinitionRepository;
    private final ForeignKeyRepository foreignKeyRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final FormDefinitionRepository formDefinitionRepository;
    private final FormTableBindingRepository formTableBindingRepository;
    private final I18nService i18nService;
    private final DeveloperWorkstationSequenceSynchronizer sequenceSynchronizer;
    
    @Override
    @Transactional
    public TableDefinition create(Long functionUnitId, TableDefinitionRequest request) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));
        
        if (tableDefinitionRepository.existsByFunctionUnitIdAndTableName(functionUnitId, request.getTableName())) {
            throw new DeveloperBusinessException("CONFLICT_TABLE_NAME_EXISTS", 
                    i18nService.getMessage("table.name_exists", request.getTableName()),
                    i18nService.getMessage("table.use_other_name"));
        }
        
        TableDefinition tableDefinition = TableDefinition.builder()
                .functionUnit(functionUnit)
                .tableName(request.getTableName())
                .tableDisplayName(request.getTableDisplayName())
                .tableType(request.getTableType())
                .description(request.getDescription())
                .build();
        
        tableDefinition = tableDefinitionRepository.save(tableDefinition);
        
        // 添加字段
        if (request.getFields() != null) {
            // 防止 DOS 攻击: 限制字段定义数量
            if (request.getFields().size() > MAX_FIELD_DEFINITIONS) {
                throw new DeveloperBusinessException("FIELD_COUNT_EXCEEDED",
                        "字段定义数量超过限制: " + request.getFields().size() + ", 最大 " + MAX_FIELD_DEFINITIONS);
            }
            sequenceSynchronizer.synchronizeFieldDefinitions();
            int sortOrder = 0;
            for (FieldDefinitionRequest fieldRequest : request.getFields()) {
                FieldDefinition field = createField(tableDefinition, fieldRequest, sortOrder++);
                tableDefinition.getFieldDefinitions().add(field);
            }
        }
        
        return tableDefinitionRepository.save(tableDefinition);
    }
    
    @Override
    @Transactional
    public TableDefinition update(Long id, TableDefinitionRequest request) {
        TableDefinition tableDefinition = getById(id);
        
        if (tableDefinitionRepository.existsByFunctionUnitIdAndTableNameAndIdNot(
                tableDefinition.getFunctionUnit().getId(), request.getTableName(), id)) {
            throw new DeveloperBusinessException("CONFLICT_TABLE_NAME_EXISTS", 
                    i18nService.getMessage("table.name_exists", request.getTableName()),
                    i18nService.getMessage("table.use_other_name"));
        }

        // 在删除旧字段前，按 id 快照 (fieldName, description)，供保存后与请求做 diff
        // 以便把字段重命名 / Display Name 变更同步到所有引用此表的 Form rule + fieldPermissions。
        Map<Long, OldFieldSnapshot> originals = new HashMap<>();
        for (FieldDefinition existing : tableDefinition.getFieldDefinitions()) {
            if (existing.getId() == null) continue;
            originals.put(existing.getId(), new OldFieldSnapshot(
                    existing.getFieldName(),
                    existing.getDescription()
            ));
        }
        Long functionUnitId = tableDefinition.getFunctionUnit().getId();

        // 更新表基本信息
        tableDefinition.setTableName(request.getTableName());
        tableDefinition.setTableDisplayName(request.getTableDisplayName());
        tableDefinition.setTableType(request.getTableType());
        tableDefinition.setDescription(request.getDescription());
        
        // 更新字段定义
        // 由于使用了 cascade = CascadeType.ALL, orphanRemoval = true
        // 先清空内存中的集合，然后显式删除旧的字段记录，避免唯一约束冲突
        tableDefinition.getFieldDefinitions().clear();
        fieldDefinitionRepository.deleteByTableDefinitionId(id);
        fieldDefinitionRepository.flush();
        
        if (request.getFields() != null && !request.getFields().isEmpty()) {
            // 防止 DOS 攻击: 限制字段定义数量
            if (request.getFields().size() > MAX_FIELD_DEFINITIONS) {
                throw new DeveloperBusinessException("FIELD_COUNT_EXCEEDED",
                        "字段定义数量超过限制: " + request.getFields().size() + ", 最大 " + MAX_FIELD_DEFINITIONS);
            }
            // 导入/init 脚本若写入较大 id 而未推进序列，delete+reinsert 会触发主键冲突
            sequenceSynchronizer.synchronizeFieldDefinitions();
            int sortOrder = 0;
            for (FieldDefinitionRequest fieldRequest : request.getFields()) {
                // Skip fields with empty name or null dataType
                if (fieldRequest.getFieldName() == null || fieldRequest.getFieldName().trim().isEmpty()) {
                    continue;
                }
                if (fieldRequest.getDataType() == null) {
                    log.warn("Skipping field {} with null dataType", fieldRequest.getFieldName());
                    continue;
                }
                
                FieldDefinition field = createField(tableDefinition, fieldRequest, sortOrder++);
                tableDefinition.getFieldDefinitions().add(field);
            }
        }
        
        TableDefinition saved = tableDefinitionRepository.save(tableDefinition);
        tableDefinitionRepository.flush();

        // 计算字段重命名 / Display Name 变更，同步到该 FunctionUnit 的所有相关 Form。
        List<FormConfigFieldRenamer.Rename> renames = computeRenames(originals, request.getFields());
        if (!renames.isEmpty()) {
            propagateFieldRenamesToForms(functionUnitId, saved, renames);
        }

        // Reload with fields to ensure consistent state for serialization
        return tableDefinitionRepository.findByIdWithFields(saved.getId())
                .orElse(saved);
    }

    /** 在事务内：扫描该 FunctionUnit 下所有表单，重写引用该表的 rule.field / rule.title 与 fieldPermissions。 */
    private void propagateFieldRenamesToForms(Long functionUnitId,
                                              TableDefinition table,
                                              List<FormConfigFieldRenamer.Rename> renames) {
        List<FormDefinition> forms = formDefinitionRepository.findByFunctionUnitIdWithBindings(functionUnitId);
        List<FormDefinition> dirty = FormConfigFieldRenamer.apply(table, forms, renames);
        if (dirty.isEmpty()) return;
        formDefinitionRepository.saveAll(dirty);
        log.info("Propagated {} field rename(s) on table {} to {} form(s)",
                renames.size(), table.getId(), dirty.size());
    }

    private List<FormConfigFieldRenamer.Rename> computeRenames(
            Map<Long, OldFieldSnapshot> originals,
            List<FieldDefinitionRequest> incoming) {
        if (originals.isEmpty() || incoming == null || incoming.isEmpty()) {
            return Collections.emptyList();
        }
        List<FormConfigFieldRenamer.Rename> out = new ArrayList<>();
        for (FieldDefinitionRequest f : incoming) {
            if (f == null || f.getId() == null) continue;
            OldFieldSnapshot orig = originals.get(f.getId());
            if (orig == null) continue;
            String oldName = orig.fieldName();
            String newName = f.getFieldName();
            String oldDesc = orig.description();
            String newDesc = f.getDescription();
            if (oldName == null || oldName.isBlank()) continue;
            if (Objects.equals(oldName, newName) && Objects.equals(oldDesc, newDesc)) continue;
            out.add(new FormConfigFieldRenamer.Rename(oldName, newName, oldDesc, newDesc));
        }
        return out;
    }

    private record OldFieldSnapshot(String fieldName, String description) {}
    
    @Override
    @Transactional
    public void delete(Long id) {
        TableDefinition tableDefinition = getById(id);
        
        // 检查是否被表单引用（旧的单表绑定方式）
        if (formDefinitionRepository.existsByBoundTable_Id(id)) {
            throw new DeveloperBusinessException("BIZ_TABLE_IN_USE", 
                    i18nService.getMessage("table.in_use_by_form"),
                    i18nService.getMessage("table.unbind_form_first"));
        }
        
        // 检查是否被表单多表绑定引用
        if (formTableBindingRepository.existsByTableId(id)) {
            throw new DeveloperBusinessException("BIZ_TABLE_IN_USE", 
                    i18nService.getMessage("table.in_use_by_binding"),
                    i18nService.getMessage("table.unbind_form_first"));
        }
        
        tableDefinitionRepository.delete(tableDefinition);
    }
    
    @Override
    @Transactional(readOnly = true)
    public TableDefinition getById(Long id) {
        return tableDefinitionRepository.findByIdWithFields(id)
                .orElseThrow(() -> new ResourceNotFoundException("TableDefinition", id));
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<TableDefinition> getByFunctionUnitId(Long functionUnitId) {
        return tableDefinitionRepository.findByFunctionUnitIdWithFields(functionUnitId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public String generateDDL(Long id, DatabaseDialect dialect) {
        TableDefinition tableDefinition = getById(id);
        return generateDDLForDialect(tableDefinition, dialect);
    }
    
    @Override
    @Transactional(readOnly = true)
    public ValidationResult validateRelationships(Long functionUnitId) {
        ValidationResult result = new ValidationResult();
        
        if (hasCircularDependency(functionUnitId)) {
            result.addError("CIRCULAR_DEPENDENCY", i18nService.getMessage("table.circular_dependency"), null);
        }
        
        return result;
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean hasCircularDependency(Long functionUnitId) {
        List<ForeignKey> foreignKeys = foreignKeyRepository.findByFunctionUnitId(functionUnitId);
        
        // 构建依赖图
        Map<Long, Set<Long>> graph = new HashMap<>();
        for (ForeignKey fk : foreignKeys) {
            Long fromTableId = fk.getTableDefinition().getId();
            Long toTableId = fk.getRefTableDefinition().getId();
            graph.computeIfAbsent(fromTableId, k -> new HashSet<>()).add(toTableId);
        }
        
        // DFS检测环
        Set<Long> visited = new HashSet<>();
        Set<Long> recursionStack = new HashSet<>();
        
        for (Long tableId : graph.keySet()) {
            if (hasCycle(tableId, graph, visited, recursionStack)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean hasCycle(Long node, Map<Long, Set<Long>> graph, 
                            Set<Long> visited, Set<Long> recursionStack) {
        if (recursionStack.contains(node)) {
            return true;
        }
        if (visited.contains(node)) {
            return false;
        }
        
        visited.add(node);
        recursionStack.add(node);
        
        Set<Long> neighbors = graph.getOrDefault(node, Collections.emptySet());
        for (Long neighbor : neighbors) {
            if (hasCycle(neighbor, graph, visited, recursionStack)) {
                return true;
            }
        }
        
        recursionStack.remove(node);
        return false;
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<ForeignKeyDTO> getForeignKeys(Long functionUnitId) {
        List<ForeignKey> foreignKeys = foreignKeyRepository.findByFunctionUnitId(functionUnitId);
        return foreignKeys.stream()
                .map(ForeignKeyDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    private FieldDefinition createField(TableDefinition tableDefinition, 
                                        FieldDefinitionRequest request, int sortOrder) {
        return FieldDefinition.builder()
                .tableDefinition(tableDefinition)
                .fieldName(request.getFieldName())
                .dataType(request.getDataType())
                .length(request.getLength())
                .precision(request.getPrecision())
                .scale(request.getScale())
                .nullable(request.getNullable())
                .defaultValue(request.getDefaultValue())
                .isPrimaryKey(request.getIsPrimaryKey())
                .isUnique(request.getIsUnique())
                .description(request.getDescription())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : sortOrder)
                .build();
    }
    
    private void validateIdentifier(String name) {
        if (name == null || !name.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new DeveloperBusinessException("INVALID_IDENTIFIER", "Invalid identifier: " + name);
        }
    }

    private String generateDDLForDialect(TableDefinition table, DatabaseDialect dialect) {
        StringBuilder ddl = new StringBuilder();
        String tableName = table.getTableName();
        validateIdentifier(tableName);
        
        ddl.append("CREATE TABLE ").append(tableName).append(" (\n");
        
        List<String> columnDefs = new ArrayList<>();
        List<String> primaryKeys = new ArrayList<>();
        
        for (FieldDefinition field : table.getFieldDefinitions()) {
            validateIdentifier(field.getFieldName());
            String columnDef = "    " + field.getFieldName() + " " + 
                    mapDataType(field.getDataType(), field.getLength(), field.getPrecision(), field.getScale(), dialect);
            
            if (Boolean.FALSE.equals(field.getNullable())) {
                columnDef += " NOT NULL";
            }
            
            if (field.getDefaultValue() != null && !field.getDefaultValue().isEmpty()) {
                columnDef += " DEFAULT " + field.getDefaultValue();
            }
            
            if (Boolean.TRUE.equals(field.getIsUnique())) {
                columnDef += " UNIQUE";
            }
            
            columnDefs.add(columnDef);
            
            if (Boolean.TRUE.equals(field.getIsPrimaryKey())) {
                primaryKeys.add(field.getFieldName());
            }
        }
        
        // 为 SUB 类型的表自动添加 row_version 列（用于乐观锁）
        if (table.getTableType() == com.developer.enums.TableType.SUB) {
            String rowVersionType = switch (dialect) {
                case POSTGRESQL -> "BIGINT";
                case MYSQL -> "BIGINT";
                case ORACLE -> "NUMBER(19)";
                case SQLSERVER -> "BIGINT";
            };
            columnDefs.add("    row_version " + rowVersionType + " NOT NULL DEFAULT 1");
        }
        
        ddl.append(String.join(",\n", columnDefs));
        
        if (!primaryKeys.isEmpty()) {
            ddl.append(",\n    PRIMARY KEY (").append(String.join(", ", primaryKeys)).append(")");
        }
        
        ddl.append("\n)");
        
        // 添加方言特定的后缀
        if (dialect == DatabaseDialect.MYSQL) {
            ddl.append(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
        
        ddl.append(";");
        
        return ddl.toString();
    }
    
    private String mapDataType(DataType dataType, Integer length, Integer precision, Integer scale, DatabaseDialect dialect) {
        return switch (dialect) {
            case POSTGRESQL -> mapToPostgreSQL(dataType, length, precision, scale);
            case MYSQL -> mapToMySQL(dataType, length, precision, scale);
            case ORACLE -> mapToOracle(dataType, length, precision, scale);
            case SQLSERVER -> mapToSQLServer(dataType, length, precision, scale);
        };
    }
    
    private String mapToPostgreSQL(DataType dataType, Integer length, Integer precision, Integer scale) {
        return switch (dataType) {
            case VARCHAR -> "VARCHAR(" + (length != null ? length : 255) + ")";
            case TEXT -> "TEXT";
            case INTEGER -> "INTEGER";
            case BIGINT -> "BIGINT";
            case DECIMAL -> "DECIMAL(" + (precision != null ? precision : 10) + "," + (scale != null ? scale : 2) + ")";
            case BOOLEAN -> "BOOLEAN";
            case DATE -> "DATE";
            case TIME -> "TIME";
            case TIMESTAMP -> "TIMESTAMP";
            case JSON -> "JSONB";
            case BYTEA -> "BYTEA";
            case FILE -> "VARCHAR(500)";
        };
    }
    
    private String mapToMySQL(DataType dataType, Integer length, Integer precision, Integer scale) {
        return switch (dataType) {
            case VARCHAR -> "VARCHAR(" + (length != null ? length : 255) + ")";
            case TEXT -> "TEXT";
            case INTEGER -> "INT";
            case BIGINT -> "BIGINT";
            case DECIMAL -> "DECIMAL(" + (precision != null ? precision : 10) + "," + (scale != null ? scale : 2) + ")";
            case BOOLEAN -> "TINYINT(1)";
            case DATE -> "DATE";
            case TIME -> "TIME";
            case TIMESTAMP -> "DATETIME";
            case JSON -> "JSON";
            case BYTEA -> "BLOB";
            case FILE -> "VARCHAR(500)";
        };
    }
    
    private String mapToOracle(DataType dataType, Integer length, Integer precision, Integer scale) {
        return switch (dataType) {
            case VARCHAR -> "VARCHAR2(" + (length != null ? length : 255) + ")";
            case TEXT -> "CLOB";
            case INTEGER -> "NUMBER(10)";
            case BIGINT -> "NUMBER(19)";
            case DECIMAL -> "NUMBER(" + (precision != null ? precision : 10) + "," + (scale != null ? scale : 2) + ")";
            case BOOLEAN -> "NUMBER(1)";
            case DATE -> "DATE";
            case TIME -> "TIMESTAMP";
            case TIMESTAMP -> "TIMESTAMP";
            case JSON -> "CLOB";
            case BYTEA -> "BLOB";
            case FILE -> "VARCHAR2(500)";
        };
    }
    
    private String mapToSQLServer(DataType dataType, Integer length, Integer precision, Integer scale) {
        return switch (dataType) {
            case VARCHAR -> "NVARCHAR(" + (length != null ? length : 255) + ")";
            case TEXT -> "NVARCHAR(MAX)";
            case INTEGER -> "INT";
            case BIGINT -> "BIGINT";
            case DECIMAL -> "DECIMAL(" + (precision != null ? precision : 10) + "," + (scale != null ? scale : 2) + ")";
            case BOOLEAN -> "BIT";
            case DATE -> "DATE";
            case TIME -> "TIME";
            case TIMESTAMP -> "DATETIME2";
            case JSON -> "NVARCHAR(MAX)";
            case BYTEA -> "VARBINARY(MAX)";
            case FILE -> "NVARCHAR(500)";
        };
    }
}
