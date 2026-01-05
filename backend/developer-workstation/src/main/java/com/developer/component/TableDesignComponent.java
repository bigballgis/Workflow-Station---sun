package com.developer.component;

import com.developer.dto.TableDefinitionRequest;
import com.developer.dto.ValidationResult;
import com.developer.entity.TableDefinition;
import com.developer.enums.DatabaseDialect;

import java.util.List;

/**
 * 表设计组件接口
 */
public interface TableDesignComponent {
    
    /**
     * 创建表定义
     */
    TableDefinition create(Long functionUnitId, TableDefinitionRequest request);
    
    /**
     * 更新表定义
     */
    TableDefinition update(Long id, TableDefinitionRequest request);
    
    /**
     * 删除表定义
     */
    void delete(Long id);
    
    /**
     * 根据ID获取表定义
     */
    TableDefinition getById(Long id);
    
    /**
     * 获取功能单元的所有表定义
     */
    List<TableDefinition> getByFunctionUnitId(Long functionUnitId);
    
    /**
     * 生成DDL语句
     */
    String generateDDL(Long id, DatabaseDialect dialect);
    
    /**
     * 验证表关系（检测循环依赖）
     */
    ValidationResult validateRelationships(Long functionUnitId);
    
    /**
     * 检测循环依赖
     */
    boolean hasCircularDependency(Long functionUnitId);
}
