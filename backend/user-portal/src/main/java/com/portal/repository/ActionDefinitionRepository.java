package com.portal.repository;

import com.portal.entity.ActionDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 动作定义Repository（只读）
 * 从 sys_action_definitions 表读取（所有环境可用）
 */
@Repository
public interface ActionDefinitionRepository extends JpaRepository<ActionDefinition, String> {
    
    /**
     * 根据功能单元ID查询动作列表
     */
    List<ActionDefinition> findByFunctionUnitId(String functionUnitId);
}
