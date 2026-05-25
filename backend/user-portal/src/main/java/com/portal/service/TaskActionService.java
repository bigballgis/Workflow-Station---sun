package com.portal.service;

import com.portal.client.WorkflowEngineClient;
import com.portal.dto.TaskActionInfo;
import com.portal.util.WorkflowEnginePayloadHelper;
import com.portal.entity.ActionDefinition;
import com.portal.repository.ActionDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 任务操作服务
 * 负责从BPMN中解析任务的可用操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskActionService {
    
    private final WorkflowEngineClient workflowEngineClient;
    private final ActionDefinitionRepository actionDefinitionRepository;
    
    /**
     * 获取任务的可用操作列表
     * 通过 Workflow Engine API 获取任务的 actionIds，然后从数据库查询 action 定义
     */
    public List<TaskActionInfo> getTaskActions(String taskId) {
        log.debug("TaskActionService.getTaskActions called for taskId: {}", taskId);
        try {
            // 1. 从 Workflow Engine 获取任务的 actionIds
            Optional<Map<String, Object>> result = workflowEngineClient.getTaskById(taskId);
            
            if (result.isEmpty()) {
                log.warn("Task not found in workflow engine: {}", taskId);
                return Collections.emptyList();
            }
            
            Map<String, Object> responseBody = result.get();
            Map<String, Object> data = WorkflowEnginePayloadHelper.singleTaskFromPayload(responseBody);

            if (data == null) {
                log.warn("No task payload in workflow engine response for task: {}", taskId);
                return Collections.emptyList();
            }
            
            // 2. 提取 actionIds（引擎 JSON 常为数字数组 [1,2,3]，反序列化为 List<Integer>，不能直接强转为 List<String>）
            List<String> actionIds = normalizeActionIdList(data.get("actionIds"));
            
            if (actionIds == null || actionIds.isEmpty()) {
                log.info("No actions defined for task: {}", taskId);
                return Collections.emptyList();
            }
            
            log.info("Found {} action IDs for task {}: {}", actionIds.size(), taskId, actionIds);
            
            // 3. 仅解析当前节点 actionIds 对应的动作定义；不能回退为整条流程的全部动作，
            // 否则待办详情会把其它节点动作也渲染成底部按钮。
            return fetchActionDefinitions(actionIds);
            
        } catch (Exception e) {
            log.error("Error getting task actions for task: " + taskId, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 从数据库批量获取action定义
     */
    /**
     * 将引擎返回的 actionIds（Integer/Long/String 混合列表）规范为 String ID，供 JPA 查询。
     */
    private static List<String> normalizeActionIdList(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        if (!(raw instanceof List<?> list)) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o == null) {
                continue;
            }
            String s = String.valueOf(o).trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private List<TaskActionInfo> fetchActionDefinitions(List<String> actionIds) {
        try {
            log.info("Fetching {} action definitions from database", actionIds.size());

            Map<String, ActionDefinition> actionsById = new LinkedHashMap<>();
            List<ActionDefinition> directMatches = actionDefinitionRepository.findAllById(actionIds);
            for (ActionDefinition action : directMatches) {
                if (action != null && action.getId() != null) {
                    actionsById.put(action.getId(), action);
                }
            }

            List<String> missingIds = actionIds.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(id -> !id.isEmpty())
                    .distinct()
                    .filter(id -> !actionsById.containsKey(id))
                    .toList();

            if (!missingIds.isEmpty()) {
                log.info("Direct ID lookup missing {} actions, falling back to dw_action_definitions for IDs: {}",
                        missingIds.size(), missingIds);
                List<ActionDefinition> dwMatches = actionDefinitionRepository.findFromDwByIds(missingIds);
                for (ActionDefinition action : dwMatches) {
                    if (action != null && action.getId() != null) {
                        actionsById.putIfAbsent(action.getId(), action);
                    }
                }
            }

            if (actionsById.isEmpty()) {
                log.warn("No action definitions found for IDs: {}", actionIds);
                return Collections.emptyList();
            }

            List<String> unresolvedIds = actionIds.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(id -> !id.isEmpty())
                    .distinct()
                    .filter(id -> !actionsById.containsKey(id))
                    .toList();
            if (!unresolvedIds.isEmpty()) {
                log.warn("Could not resolve action definitions for IDs: {}", unresolvedIds);
            }

            List<TaskActionInfo> orderedActions = actionIds.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(id -> !id.isEmpty())
                    .distinct()
                    .map(actionsById::get)
                    .filter(Objects::nonNull)
                    .map(this::toTaskActionInfo)
                    .collect(Collectors.toList());

            log.info("Resolved {} action definitions from requested actionIds", orderedActions.size());
            return orderedActions;
                
        } catch (Exception e) {
            log.error("Error fetching action definitions from database", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 将ActionDefinition实体转换为TaskActionInfo DTO
     */
    private TaskActionInfo toTaskActionInfo(ActionDefinition action) {
        return TaskActionInfo.builder()
            .actionId(action.getId())
            .actionName(action.getActionName())
            .actionType(action.getActionType())
            .description(action.getDescription())
            .icon(action.getIcon())
            .buttonColor(action.getButtonColor())
            .configJson(action.getConfigJson())
            .build();
    }
}
