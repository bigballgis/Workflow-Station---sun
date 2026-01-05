package com.developer.repository;

import com.developer.entity.TableDefinition;
import com.developer.enums.TableType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 表定义仓库
 */
@Repository
public interface TableDefinitionRepository extends JpaRepository<TableDefinition, Long> {
    
    List<TableDefinition> findByFunctionUnitId(Long functionUnitId);
    
    List<TableDefinition> findByFunctionUnitIdAndTableType(Long functionUnitId, TableType tableType);
    
    Optional<TableDefinition> findByFunctionUnitIdAndTableName(Long functionUnitId, String tableName);
    
    boolean existsByFunctionUnitIdAndTableName(Long functionUnitId, String tableName);
    
    boolean existsByFunctionUnitIdAndTableNameAndIdNot(Long functionUnitId, String tableName, Long id);
}
