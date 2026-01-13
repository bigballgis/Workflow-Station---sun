package com.developer.repository;

import com.developer.entity.TableDefinition;
import com.developer.enums.TableType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 表定义仓库
 */
@Repository
public interface TableDefinitionRepository extends JpaRepository<TableDefinition, Long> {
    
    List<TableDefinition> findByFunctionUnitId(Long functionUnitId);
    
    /**
     * 获取功能单元的所有表定义，同时加载字段定义
     */
    @Query("SELECT DISTINCT t FROM TableDefinition t LEFT JOIN FETCH t.fieldDefinitions WHERE t.functionUnit.id = :functionUnitId")
    List<TableDefinition> findByFunctionUnitIdWithFields(@Param("functionUnitId") Long functionUnitId);
    
    List<TableDefinition> findByFunctionUnitIdAndTableType(Long functionUnitId, TableType tableType);
    
    Optional<TableDefinition> findByFunctionUnitIdAndTableName(Long functionUnitId, String tableName);
    
    /**
     * 获取表定义，同时加载字段定义
     */
    @Query("SELECT t FROM TableDefinition t LEFT JOIN FETCH t.fieldDefinitions WHERE t.id = :id")
    Optional<TableDefinition> findByIdWithFields(@Param("id") Long id);
    
    boolean existsByFunctionUnitIdAndTableName(Long functionUnitId, String tableName);
    
    boolean existsByFunctionUnitIdAndTableNameAndIdNot(Long functionUnitId, String tableName, Long id);
}
