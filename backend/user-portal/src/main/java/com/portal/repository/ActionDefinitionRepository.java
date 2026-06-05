package com.portal.repository;

import com.portal.entity.ActionDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    
    /**
     * 根据流程定义Key查找已启用功能单元的动作定义
     * 通过 sys_function_unit_contents.flowable_process_definition_id 关联
     */
    @Query(value = "SELECT a.* FROM sys_action_definitions a " +
            "JOIN sys_function_units fu ON fu.id = a.function_unit_id " +
            "JOIN sys_function_unit_contents c ON c.function_unit_id = fu.id " +
            "WHERE c.flowable_process_definition_id LIKE CONCAT(:processKey, ':%') " +
            "AND fu.enabled = true", nativeQuery = true)
    List<ActionDefinition> findByProcessDefinitionKey(@Param("processKey") String processDefinitionKey);

    /**
     * 从 dw_action_definitions 表按整数 ID 查找动作定义
     * BPMN 中存储的 actionIds 来自 developer-workstation 的 dw_action_definitions（bigint ID），
     * 当 sys_action_definitions（UUID ID）查不到时，回退到此查询。
     * 使用 CAST 将 bigint 列映射为 varchar 以匹配 ActionDefinition entity 的 String id。
     */
    @Query(value = "SELECT CAST(d.id AS VARCHAR) AS id, " +
            "CAST(d.function_unit_id AS VARCHAR) AS function_unit_id, " +
            "d.action_name, d.action_type, d.display_name AS description, d.config_json, " +
            "d.icon, d.button_color, d.is_default, " +
            "d.created_at, d.updated_at, " +
            "NULL AS created_by, NULL AS updated_by " +
            "FROM dw_action_definitions d " +
            "WHERE CAST(d.id AS VARCHAR) IN :ids", nativeQuery = true)
    List<ActionDefinition> findFromDwByIds(@Param("ids") List<String> ids);
}
