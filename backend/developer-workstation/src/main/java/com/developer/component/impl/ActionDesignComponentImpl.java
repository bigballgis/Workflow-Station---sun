package com.developer.component.impl;

import com.developer.component.ActionDesignComponent;
import com.developer.dto.ActionDefinitionRequest;
import com.developer.entity.ActionDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.exception.BusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.repository.FunctionUnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动作设计组件实现
 */
@Component
@Slf4j
public class ActionDesignComponentImpl implements ActionDesignComponent {
    
    private final ActionDefinitionRepository actionDefinitionRepository;
    private final FunctionUnitRepository functionUnitRepository;
    
    /**
     * 简化构造函数，用于测试
     */
    public ActionDesignComponentImpl(ActionDefinitionRepository actionDefinitionRepository) {
        this(actionDefinitionRepository, null);
    }
    
    /**
     * 完整构造函数
     */
    public ActionDesignComponentImpl(
            ActionDefinitionRepository actionDefinitionRepository,
            FunctionUnitRepository functionUnitRepository) {
        this.actionDefinitionRepository = actionDefinitionRepository;
        this.functionUnitRepository = functionUnitRepository;
    }
    
    @Override
    @Transactional
    public ActionDefinition create(Long functionUnitId, ActionDefinitionRequest request) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));
        
        if (actionDefinitionRepository.existsByFunctionUnitIdAndActionName(functionUnitId, request.getActionName())) {
            throw new BusinessException("CONFLICT_ACTION_NAME_EXISTS", 
                    "动作名已存在: " + request.getActionName(),
                    "请使用其他动作名");
        }
        
        ActionDefinition actionDefinition = ActionDefinition.builder()
                .functionUnit(functionUnit)
                .actionName(request.getActionName())
                .actionType(request.getActionType())
                .configJson(request.getConfigJson())
                .icon(request.getIcon())
                .buttonColor(request.getButtonColor())
                .description(request.getDescription())
                .isDefault(isDefaultActionType(request.getActionType()))
                .build();
        
        return actionDefinitionRepository.save(actionDefinition);
    }
    
    @Override
    @Transactional
    public ActionDefinition update(Long id, ActionDefinitionRequest request) {
        ActionDefinition actionDefinition = getById(id);
        
        if (actionDefinitionRepository.existsByFunctionUnitIdAndActionNameAndIdNot(
                actionDefinition.getFunctionUnit().getId(), request.getActionName(), id)) {
            throw new BusinessException("CONFLICT_ACTION_NAME_EXISTS", 
                    "动作名已存在: " + request.getActionName(),
                    "请使用其他动作名");
        }
        
        actionDefinition.setActionName(request.getActionName());
        actionDefinition.setActionType(request.getActionType());
        actionDefinition.setConfigJson(request.getConfigJson());
        actionDefinition.setIcon(request.getIcon());
        actionDefinition.setButtonColor(request.getButtonColor());
        actionDefinition.setDescription(request.getDescription());
        
        return actionDefinitionRepository.save(actionDefinition);
    }
    
    @Override
    @Transactional
    public void delete(Long id) {
        ActionDefinition actionDefinition = getById(id);
        // TODO: 检查是否被流程步骤绑定
        actionDefinitionRepository.delete(actionDefinition);
    }
    
    @Override
    @Transactional(readOnly = true)
    public ActionDefinition getById(Long id) {
        return actionDefinitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ActionDefinition", id));
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<ActionDefinition> getByFunctionUnitId(Long functionUnitId) {
        return actionDefinitionRepository.findByFunctionUnitId(functionUnitId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<ActionDefinition> getDefaultActions(Long functionUnitId) {
        return actionDefinitionRepository.findByFunctionUnitIdAndIsDefaultTrue(functionUnitId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<ActionDefinition> getCustomActions(Long functionUnitId) {
        return actionDefinitionRepository.findByFunctionUnitIdAndIsDefaultFalse(functionUnitId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> test(Long id, Map<String, Object> parameters) {
        ActionDefinition actionDefinition = getById(id);
        Map<String, Object> result = new HashMap<>();
        
        result.put("actionId", id);
        result.put("actionName", actionDefinition.getActionName());
        result.put("actionType", actionDefinition.getActionType());
        result.put("status", "SUCCESS");
        result.put("message", "动作测试执行成功");
        
        // TODO: 根据动作类型执行实际测试
        
        return result;
    }
    
    private boolean isDefaultActionType(com.developer.enums.ActionType actionType) {
        return switch (actionType) {
            case APPROVE, REJECT, TRANSFER, DELEGATE, ROLLBACK, WITHDRAW -> true;
            default -> false;
        };
    }
}
