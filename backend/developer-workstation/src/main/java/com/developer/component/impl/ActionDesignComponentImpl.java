package com.developer.component.impl;

import com.developer.component.ActionDesignComponent;
import com.developer.dto.ActionDefinitionRequest;
import com.developer.entity.ActionDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.exception.BusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.platform.common.i18n.I18nService;
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
@RequiredArgsConstructor
public class ActionDesignComponentImpl implements ActionDesignComponent {
    
    private final ActionDefinitionRepository actionDefinitionRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final I18nService i18nService;
    
    @Override
    @Transactional
    public ActionDefinition create(Long functionUnitId, ActionDefinitionRequest request) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));
        
        if (actionDefinitionRepository.existsByFunctionUnitIdAndActionName(functionUnitId, request.getActionName())) {
            throw new BusinessException("CONFLICT_ACTION_NAME_EXISTS", 
                    i18nService.getMessage("action.name_exists", request.getActionName()),
                    i18nService.getMessage("action.use_other_name"));
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
                    i18nService.getMessage("action.name_exists", request.getActionName()),
                    i18nService.getMessage("action.use_other_name"));
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
        checkActionDependencies(id);
        actionDefinitionRepository.delete(actionDefinition);
    }
    
    /**
     * 检查动作是否被流程步骤引用
     * 如果被引用，抛出 BusinessException
     * 
     * @param actionId 动作ID
     * @throws BusinessException 如果动作正在被使用
     */
    private void checkActionDependencies(Long actionId) {
        ActionDefinition action = actionDefinitionRepository.findById(actionId)
                .orElseThrow(() -> new ResourceNotFoundException("ActionDefinition", actionId));
        
        // 获取该功能单元的流程定义
        FunctionUnit functionUnit = action.getFunctionUnit();
        if (functionUnit.getProcessDefinition() != null) {
            String bpmnXml = functionUnit.getProcessDefinition().getBpmnXml();
            
            // 简化检查：在 BPMN XML 中搜索动作名称
            // 注意：这是简化实现，完整实现需要解析 BPMN XML
            if (bpmnXml != null && bpmnXml.contains(action.getActionName())) {
                throw new BusinessException(
                    "ACTION_IN_USE",
                    i18nService.getMessage("action.in_use"),
                    i18nService.getMessage("action.remove_reference_first")
                );
            }
        }
        
        log.info("Action dependency check passed for action: {}", actionId);
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
        result.put("message", i18nService.getMessage("action.test_success"));
        
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
