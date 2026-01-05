package com.developer.component.impl;

import com.developer.component.TableDesignComponent;
import com.developer.dto.FieldDefinitionRequest;
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

/**
 * 表设计组件实现
 */
@Component
@Slf4j
public class TableDesignComponentImpl implements TableDesignComponent {
    
    private final TableDefinitionRepository tableDefinitionRepository;
    private final FieldDefinitionRepository fieldDefinitionRepository;
    private final ForeignKeyRepository foreignKeyRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final FormDefinitionRepository formDefinitionRepository;
    
    /**
     * 简化构造函数，用于测试
     */
    public TableDesignComponentImpl(
            TableDefinitionRepository tableDefinitionRepository,
            FieldDefinitionRepository fieldDefinitionRepository,
            ForeignKeyRepository foreignKeyRepository) {
        this(tableDefinitionRepository, fieldDefinitionRepository, foreignKeyRepository, null, null);
    }
    
    /**
     * 完整构造函数
     */
    public TableDesignComponentImpl(
            TableDefinitionRepository tableDefinitionRepository,
            FieldDefinitionRepository fieldDefinitionRepository,
            ForeignKeyRepository foreignKeyRepository,
            FunctionUnitRepository functionUnitRepository,
            FormDefinitionRepository formDefinitionRepository) {
        this.tableDefinitionRepository = tableDefinitionRepository;
        this.fieldDefinitionRepository = fieldDefinitionRepository;
        this.foreignKeyRepository = foreignKeyRepository;
        this.functionUnitRepository = functionUnitRepository;
        this.formDefinitionRepository = formDefinitionRepository;
    }
    
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
        
        tableDefinition.setTableName(request.getTableName());
        tableDefinition.setTableType(request.getTableType());
        tableDefinition.setDescription(request.getDescription());
        
        return tableDefinitionRepository.save(tableDefinition);
    }
    
    @Override
    @Transactional
    public void delete(Long id) {
        TableDefinition tableDefinition = getById(id);
        
        // 检查是否被表单引用
        if (formDefinitionRepository.existsByBoundTableId(id)) {
            throw new BusinessException("BIZ_TABLE_IN_USE", 
                    "表被表单引用，无法删除",
                    "请先解除表单绑定");
        }
        
        tableDefinitionRepository.delete(tableDefinition);
    }
    
    @Override
    @Transactional(readOnly = true)
    public TableDefinition getById(Long id) {
        return tableDefinitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TableDefinition", id));
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<TableDefinition> getByFunctionUnitId(Long functionUnitId) {
        return tableDefinitionRepository.findByFunctionUnitId(functionUnitId);
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
