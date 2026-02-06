package com.admin.repository;

import com.admin.entity.ActionDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Action Definition Repository
 */
@Repository
public interface ActionDefinitionRepository extends JpaRepository<ActionDefinition, String> {
    
    /**
     * Find all actions by function unit ID
     */
    List<ActionDefinition> findByFunctionUnitId(String functionUnitId);
    
    /**
     * Delete all actions by function unit ID
     */
    void deleteByFunctionUnitId(String functionUnitId);
}
