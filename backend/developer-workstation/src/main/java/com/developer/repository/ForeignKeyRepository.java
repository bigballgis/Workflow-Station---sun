package com.developer.repository;

import com.developer.entity.ForeignKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 外键仓库
 */
@Repository
public interface ForeignKeyRepository extends JpaRepository<ForeignKey, Long> {
    
    List<ForeignKey> findByTableDefinitionId(Long tableId);
    
    List<ForeignKey> findByRefTableDefinitionId(Long refTableId);
    
    @Query("SELECT fk FROM ForeignKey fk WHERE fk.tableDefinition.functionUnit.id = :functionUnitId")
    List<ForeignKey> findByFunctionUnitId(@Param("functionUnitId") Long functionUnitId);
}
