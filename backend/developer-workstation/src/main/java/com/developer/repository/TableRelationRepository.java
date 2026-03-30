package com.developer.repository;

import com.developer.entity.TableRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 表关系仓库
 */
@Repository
public interface TableRelationRepository extends JpaRepository<TableRelation, Long> {

    List<TableRelation> findByFunctionUnitId(Long functionUnitId);

    void deleteByFunctionUnitId(Long functionUnitId);

    void deleteBySourceTableIdOrTargetTableId(Long sourceTableId, Long targetTableId);
}
