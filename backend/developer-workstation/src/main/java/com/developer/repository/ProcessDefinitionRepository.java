package com.developer.repository;

import com.developer.entity.ProcessDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 流程定义仓库
 */
@Repository
public interface ProcessDefinitionRepository extends JpaRepository<ProcessDefinition, Long> {
    
    Optional<ProcessDefinition> findByFunctionUnitId(Long functionUnitId);
    
    void deleteByFunctionUnitId(Long functionUnitId);
}
