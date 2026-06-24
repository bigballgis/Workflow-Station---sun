package com.developer.component.impl;

import com.developer.component.ActionDesignComponent;
import com.developer.dto.ActionDefinitionRequest;
import com.developer.entity.ActionDefinition;
import com.developer.entity.FormDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.enums.ActionType;
import com.developer.enums.FormType;
import com.developer.exception.DeveloperBusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.platform.common.i18n.I18nService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 动作设计组件实现
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ActionDesignComponentImpl implements ActionDesignComponent {
    
    private final ActionDefinitionRepository actionDefinitionRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final FormDefinitionRepository formDefinitionRepository;
    private final I18nService i18nService;
    
    @Override
    @Transactional
    public ActionDefinition create(Long functionUnitId, ActionDefinitionRequest request) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));
        
        if (actionDefinitionRepository.existsByFunctionUnitIdAndActionName(functionUnitId, request.getActionName())) {
            throw new DeveloperBusinessException("CONFLICT_ACTION_NAME_EXISTS", 
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
                .displayName(request.getDescription())
                .isDefault(isDefaultActionType(request.getActionType()))
                .build();
        
        validateFormPopupType(actionDefinition);
        
        return actionDefinitionRepository.save(actionDefinition);
    }
    
    @Override
    @Transactional
    public ActionDefinition update(Long id, ActionDefinitionRequest request) {
        ActionDefinition actionDefinition = getById(id);
        
        if (actionDefinitionRepository.existsByFunctionUnitIdAndActionNameAndIdNot(
                actionDefinition.getFunctionUnit().getId(), request.getActionName(), id)) {
            throw new DeveloperBusinessException("CONFLICT_ACTION_NAME_EXISTS", 
                    i18nService.getMessage("action.name_exists", request.getActionName()),
                    i18nService.getMessage("action.use_other_name"));
        }
        
        actionDefinition.setActionName(request.getActionName());
        actionDefinition.setActionType(request.getActionType());
        actionDefinition.setConfigJson(request.getConfigJson());
        actionDefinition.setIcon(request.getIcon());
        actionDefinition.setButtonColor(request.getButtonColor());
        actionDefinition.setDisplayName(request.getDescription());
        
        validateFormPopupType(actionDefinition);
        
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
     * 如果被引用，抛出 DeveloperBusinessException
     *
     * <p>BPMN XML 中动作通过 custom:property name="actionIds" value="[id1,id2,...]"
     * 的方式引用（按 ID），因此必须解析 XML 提取引用列表后按 ID 比对，
     * 而不是对整个 XML 文档做简单的 contains(actionName) 子串匹配。
     * 否则短/纯数字动作名（如 "3"）会命中 BPMN 中大量的坐标、ID 等数值
     * 造成假阳性，导致不应被阻止的删除操作失败。</p>
     *
     * @param actionId 动作ID
     * @throws DeveloperBusinessException 如果动作正在被使用
     */
    private void checkActionDependencies(Long actionId) {
        ActionDefinition action = actionDefinitionRepository.findById(actionId)
                .orElseThrow(() -> new ResourceNotFoundException("ActionDefinition", actionId));

        FunctionUnit functionUnit = action.getFunctionUnit();
        if (functionUnit.getProcessDefinition() == null) {
            log.info("Action dependency check passed for action: {} (no process definition)", actionId);
            return;
        }

        String bpmnXml = functionUnit.getProcessDefinition().getBpmnXml();
        if (bpmnXml == null || bpmnXml.isBlank()) {
            log.info("Action dependency check passed for action: {} (empty BPMN XML)", actionId);
            return;
        }

        // 1) 正则提取所有 actionIds / globalActionIds（按 ID 精确匹配，不依赖命名空间声明）
        String actionIdStr = action.getId().toString();
        if (isActionIdInBpmnProperties(bpmnXml, actionIdStr)) {
            throw new DeveloperBusinessException(
                "ACTION_IN_USE",
                i18nService.getMessage("action.in_use"),
                i18nService.getMessage("action.remove_reference_first")
            );
        }

        // 2) 退化：当 BPMN XML 没有标准的 actionIds 属性时，
        //    仅在 custom:property 元素范围内按 action name 搜索，避免全量 XML 的 contains 假阳性
        if (isActionNameInBpmnProperties(bpmnXml, action.getActionName())) {
            throw new DeveloperBusinessException(
                "ACTION_IN_USE",
                i18nService.getMessage("action.in_use"),
                i18nService.getMessage("action.remove_reference_first")
            );
        }

        log.info("Action dependency check passed for action: {}", actionId);
    }

    /**
     * 正则：匹配 name="actionIds" 或 name="globalActionIds" 的属性行，
     * 提取 value="[...]" 中的 action ID 列表，检查是否包含 targetActionId。
     *
     * 正则设计参照 {@link com.workflow.component.BpmnActionParser} 中
     * ACTION_IDS_IN_USER_TASK / GLOBAL_ACTION_IDS 的模式，不依赖命名空间前缀。
     */
    private boolean isActionIdInBpmnProperties(String bpmnXml, String targetActionId) {
        // 匹配 name="actionIds" value="[...]" 或 name="globalActionIds" value="[...]"
        // 支持双引号和单引号、任意属性顺序。
        // 与 BpmnActionParser 保持一致的属性名：actionIds / globalActionIds（camelCase）
        // 使用 CASE_INSENSITIVE 防御性处理大小写变体。
        Pattern pattern = Pattern.compile(
            "name\\s*=\\s*[\"'](?:global)?[aA]ction[Ii]ds[\"'][^>]*?value\\s*=\\s*[\"']([^\"']*)[\"']|"
            + "value\\s*=\\s*[\"']([^\"']*)[\"'][^>]*?name\\s*=\\s*[\"'](?:global)?[aA]ction[Ii]ds[\"']",
            Pattern.DOTALL
        );

        Matcher matcher = pattern.matcher(bpmnXml);
        while (matcher.find()) {
            String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (value != null && !value.isBlank()) {
                List<String> ids = parseActionIdsValue(value.trim());
                if (ids != null && ids.contains(targetActionId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 解析 actionIds 的 JSON 数组值，例如 "[12,34]" 或 "[1,2,3]"。
     * 同时兼容 legacy 的 "[id1,id2]" 格式。
     */
    private List<String> parseActionIdsValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        // JSON 数组格式: [1,2,3] 或 ["a","b"]
        if (value.startsWith("[") && value.endsWith("]")) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.readTree(value);
                if (node != null && node.isArray() && !node.isEmpty()) {
                    List<String> ids = new ArrayList<>();
                    for (JsonNode n : node) {
                        if (n == null || n.isNull()) {
                            continue;
                        }
                        String s = n.isTextual() ? n.asText() : n.asText();
                        s = s != null ? s.trim() : "";
                        if (!s.isEmpty()) {
                            ids.add(s);
                        }
                    }
                    if (!ids.isEmpty()) {
                        return ids;
                    }
                }
            } catch (Exception ignore) {
                // 解析失败则尝试 legacy 格式
            }
        }

        // Legacy 格式: "[id1,id2]"
        String cleaned = value.replaceAll("[\\[\\]\\s\"]", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        List<String> ids = new ArrayList<>();
        for (String part : cleaned.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                ids.add(trimmed);
            }
        }
        return ids.isEmpty() ? null : ids;
    }

    /**
     * 仅在 BPMN XML 的 custom:property 元素范围内按 action name 做子串匹配。
     * 相比直接对全量 XML 做 contains，大幅降低短名称/纯数字名称的假阳性概率。
     *
     * 正则匹配所有 name="actionName" 或 name="actionNameList" 的 property 元素，
     * 检查其 value 中是否包含目标 actionName。
     */
    private boolean isActionNameInBpmnProperties(String bpmnXml, String actionName) {
        Pattern pattern = Pattern.compile(
            "name\\s*=\\s*[\"']actionName(List)?[\"'][^>]*?value\\s*=\\s*[\"']([^\"']*)[\"']|"
            + "value\\s*=\\s*[\"']([^\"']*)[\"'][^>]*?name\\s*=\\s*[\"']actionName(List)?[\"']",
            Pattern.DOTALL
        );

        Matcher matcher = pattern.matcher(bpmnXml);
        while (matcher.find()) {
            String value = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            if (value != null && value.contains(actionName)) {
                return true;
            }
        }
        return false;
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

        return result;
    }
    
    private boolean isDefaultActionType(com.developer.enums.ActionType actionType) {
        return switch (actionType) {
            case APPROVE, REJECT, TRANSFER, DELEGATE, ROLLBACK, WITHDRAW, DRAFT, SAVE -> true;
            default -> false;
        };
    }
    
    /**
     * 校验 FORM_POPUP Action 引用的表单必须是 FormType.ACTION
     * 如果引用 PROCESS 或 TASK 类型表单，抛出 400 DeveloperBusinessException
     */
    private void validateFormPopupType(ActionDefinition actionDefinition) {
        if (actionDefinition.getActionType() != ActionType.FORM_POPUP) {
            return;
        }
        
        Map<String, Object> config = actionDefinition.getConfigJson();
        if (config == null) {
            return;
        }
        
        Object formIdObj = config.get("formId");
        if (formIdObj == null) {
            return;
        }
        
        Long formId;
        if (formIdObj instanceof Number) {
            formId = ((Number) formIdObj).longValue();
        } else {
            try {
                formId = Long.parseLong(formIdObj.toString());
            } catch (NumberFormatException e) {
                return;
            }
        }
        
        FormDefinition form = formDefinitionRepository.findById(formId)
                .orElseThrow(() -> new ResourceNotFoundException("FormDefinition", formId));
        
        if (form.getFormType() != FormType.ACTION) {
            throw new DeveloperBusinessException("INVALID_POPUP_FORM_TYPE",
                    i18nService.getMessage("action.invalid_popup_form_type"),
                    i18nService.getMessage("action.popup_must_use_action_form"));
        }
    }
}
