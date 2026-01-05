package com.developer.component;

import com.developer.dto.ActionDefinitionRequest;
import com.developer.entity.ActionDefinition;

import java.util.List;
import java.util.Map;

/**
 * 动作设计组件接口
 */
public interface ActionDesignComponent {
    
    /**
     * 创建动作定义
     */
    ActionDefinition create(Long functionUnitId, ActionDefinitionRequest request);
    
    /**
     * 更新动作定义
     */
    ActionDefinition update(Long id, ActionDefinitionRequest request);
    
    /**
     * 删除动作定义
     */
    void delete(Long id);
    
    /**
     * 根据ID获取动作定义
     */
    ActionDefinition getById(Long id);
    
    /**
     * 获取功能单元的所有动作定义
     */
    List<ActionDefinition> getByFunctionUnitId(Long functionUnitId);
    
    /**
     * 获取默认动作
     */
    List<ActionDefinition> getDefaultActions(Long functionUnitId);
    
    /**
     * 获取自定义动作
     */
    List<ActionDefinition> getCustomActions(Long functionUnitId);
    
    /**
     * 测试动作执行
     */
    Map<String, Object> test(Long id, Map<String, Object> parameters);
}
