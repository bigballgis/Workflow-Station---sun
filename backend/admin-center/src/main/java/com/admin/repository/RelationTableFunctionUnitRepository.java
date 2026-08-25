package com.admin.repository;

import com.admin.entity.RelationTableFunctionUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Relation Table <-> Function Unit link repository.
 */
@Repository
public interface RelationTableFunctionUnitRepository extends JpaRepository<RelationTableFunctionUnit, String> {

    List<RelationTableFunctionUnit> findByRelationTableId(Long relationTableId);

    List<RelationTableFunctionUnit> findByRelationTableIdIn(Collection<Long> relationTableIds);

    void deleteByRelationTableId(Long relationTableId);
}
