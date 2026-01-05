package com.developer.repository;

import com.developer.entity.ActionDefinition;
import com.developer.enums.ActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 动作定义仓库
 */
@Repository
public interface ActionDefinitionRepository extends JpaRepository<ActionDefinition, Long> {
    
    List<ActionDefinition> findByFunctionUnitId(Long functionUnitId);
    
    List<ActionDefinition> findByFunctionUnitIdAndActionType(Long functionUnitId, ActionType actionType);
    
    List<ActionDefinition> findByFunctionUnitIdAndIsDefaultTrue(Long functionUnitId);
    
    List<ActionDefinition> findByFunctionUnitIdAndIsDefaultFalse(Long functionUnitId);
    
    Optional<ActionDefinition> findByFunctionUnitIdAndActionName(Long functionUnitId, String actionName);
    
    boolean existsByFunctionUnitIdAndActionName(Long functionUnitId, String actionName);
    
    boolean existsByFunctionUnitIdAndActionNameAndIdNot(Long functionUnitId, String actionName, Long id);
}
