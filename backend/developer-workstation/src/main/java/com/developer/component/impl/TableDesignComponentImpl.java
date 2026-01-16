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
import com.developer.exception.BusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.*;
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
    
    private final TableDefinitionRepository tableDefinitionRepository;
    private final FieldDefinitionRepository fieldDefinitionRepository;
    private final ForeignKeyRepository foreignKeyRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final FormDefinitionRepository formDefinitionRepository;
    private final FormTableBindingRepository formTableBindingRepository;
    
    @Override
    @Transactional
    public TableDefinition create(Long functionUnitId, TableDefinitionRequest request) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));
        
        if (tableDefinitionRepository.existsByFunctionUnitIdAndTableName(functionUnitId, request.getTableName())) {
            throw new BusinessException("CONFLICT_TABLE_NAME_EXISTS", 
                    "表名已存在: " + request.getTableName(),
                    "请使用其他表名");
        }
        
        TableDefinition tableDefinition = TableDefinition.builder()
                .functionUnit(functionUnit)
                .tableName(request.getTableName())
                .tableType(request.getTableType())
                .description(request.getDescription())
                .build();
        
        tableDefinition = tableDefinitionRepository.save(tableDefinition);
        
        // 添加字段
        if (request.getFields() != null) {
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
            throw new BusinessException("CONFLICT_TABLE_NAME_EXISTS", 
                    "表名已存在: " + request.getTableName(),
                    "请使用其他表名");
        }
        
        // 更新表基本信息
        tableDefinition.setTableName(request.getTableName());
        tableDefinition.setTableType(request.getTableType());
        tableDefinition.setDescription(request.getDescription());
        
        // 更新字段定义
        // 由于使用了 cascade = CascadeType.ALL, orphanRemoval = true
        // 先清空内存中的集合，然后显式删除旧的字段记录，避免唯一约束冲突
        // 注意：必须先清空集合，避免 Hibernate 尝试加载已删除的实体
        tableDefinition.getFieldDefinitions().clear();
        // 使用 @Modifying 和 @Query 直接通过 SQL 删除，确保删除操作立即执行
        log.info("Deleting existing fields for table {}", id);
        fieldDefinitionRepository.deleteByTableDefinitionId(id);
        fieldDefinitionRepository.flush(); // 确保删除操作立即执行
        
        if (request.getFields() != null && !request.getFields().isEmpty()) {
            log.info("Updating table {} with {} fields", id, request.getFields().size());
            int sortOrder = 0;
            int skippedCount = 0;
            for (FieldDefinitionRequest fieldRequest : request.getFields()) {
                log.debug("Processing field request: fieldName={}, dataType={}, nullable={}", 
                    fieldRequest.getFieldName(), fieldRequest.getDataType(), fieldRequest.getNullable());
                
                // 验证字段名不为空
                if (fieldRequest.getFieldName() == null || fieldRequest.getFieldName().trim().isEmpty()) {
                    log.warn("Skipping field with empty name at index {}", sortOrder);
                    skippedCount++;
                    continue; // 跳过空字段名
                }
                
                // 验证数据类型不为空
                if (fieldRequest.getDataType() == null) {
                    log.warn("Skipping field {} with null dataType", fieldRequest.getFieldName());
                    skippedCount++;
                    continue;
                }
                
                log.info("Creating field: name={}, type={}, sortOrder={}", 
                    fieldRequest.getFieldName(), fieldRequest.getDataType(), sortOrder);
                try {
                    FieldDefinition field = createField(tableDefinition, fieldRequest, sortOrder++);
                    tableDefinition.getFieldDefinitions().add(field);
                    log.debug("Field added successfully: {}", field.getFieldName());
                } catch (Exception e) {
                    log.error("Failed to create field {}: {}", fieldRequest.getFieldName(), e.getMessage(), e);
                    skippedCount++;
                }
            }
            log.info("Total fields processed: {}, added: {}, skipped: {}", 
                request.getFields().size(), tableDefinition.getFieldDefinitions().size(), skippedCount);
        } else {
            log.warn("No fields in request for table {} (fields is null or empty)", id);
        }
        
        TableDefinition saved = tableDefinitionRepository.save(tableDefinition);
        log.info("Table saved with {} fields in memory", saved.getFieldDefinitions().size());
        
        // 刷新EntityManager，确保字段被持久化
        tableDefinitionRepository.flush();
        
        // 强制初始化字段集合，确保字段被加载（在事务内）
        // 访问 size() 和遍历会触发懒加载
        int fieldCount = saved.getFieldDefinitions().size();
        log.info("Field count after flush: {}", fieldCount);
        
        // 遍历字段，强制加载所有字段数据
        saved.getFieldDefinitions().forEach(field -> {
            field.getFieldName(); // 访问字段属性，确保被加载
            field.getDataType();
        });
        
        // 重新加载包含字段的数据，确保返回的数据包含所有字段
        // 因为保存后立即序列化时，懒加载的字段可能没有被加载
        TableDefinition result = tableDefinitionRepository.findByIdWithFields(saved.getId())
                .orElse(saved);
        
        // 再次强制初始化字段集合
        int reloadedFieldCount = result.getFieldDefinitions().size();
        log.info("Reloaded table {} with {} fields from database", result.getId(), reloadedFieldCount);
        
        // 遍历重新加载的字段，确保所有字段数据被加载
        result.getFieldDefinitions().forEach(field -> {
            field.getFieldName();
            field.getDataType();
        });
        
        // 如果重新加载后字段数量为0，但内存中有字段，说明可能是事务问题
        if (reloadedFieldCount == 0 && fieldCount > 0) {
            log.warn("Reloaded table has 0 fields but memory has {} fields. Using in-memory data.", fieldCount);
            // 使用内存中的数据
            result = saved;
        }
        
        return result;
    }
    
    @Override
    @Transactional
    public void delete(Long id) {
        TableDefinition tableDefinition = getById(id);
        
        // 检查是否被表单引用（旧的单表绑定方式）
        if (formDefinitionRepository.existsByBoundTable_Id(id)) {
            throw new BusinessException("BIZ_TABLE_IN_USE", 
                    "表被表单引用，无法删除",
                    "请先解除表单绑定");
        }
        
        // 检查是否被表单多表绑定引用
        if (formTableBindingRepository.existsByTableId(id)) {
            throw new BusinessException("BIZ_TABLE_IN_USE", 
                    "表被表单绑定引用，无法删除",
                    "请先解除表单绑定");
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
            result.addError("CIRCULAR_DEPENDENCY", "检测到循环依赖", null);
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
    
    private String generateDDLForDialect(TableDefinition table, DatabaseDialect dialect) {
        StringBuilder ddl = new StringBuilder();
        String tableName = table.getTableName();
        
        ddl.append("CREATE TABLE ").append(tableName).append(" (\n");
        
        List<String> columnDefs = new ArrayList<>();
        List<String> primaryKeys = new ArrayList<>();
        
        for (FieldDefinition field : table.getFieldDefinitions()) {
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
        };
    }
}
