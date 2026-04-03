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
            
            // 3. 从数据库获取action定义
            // 同时传入 processDefinitionKey 用于回退查找
            String processDefinitionKey = (String) data.get("processDefinitionKey");
            return fetchActionDefinitions(actionIds, processDefinitionKey);
            
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

    private List<TaskActionInfo> fetchActionDefinitions(List<String> actionIds, String processDefinitionKey) {
        try {
            log.info("Fetching {} action definitions from database", actionIds.size());
            
            List<ActionDefinition> actions = actionDefinitionRepository.findAllById(actionIds);
            
            // Fallback 1: BPMN stores dw_action_definitions IDs (integers like 17,18,23),
            // but sys_action_definitions uses UUID IDs. When direct ID lookup fails,
            // resolve the function unit from processDefinitionKey and return all its actions.
            if (actions.isEmpty() && processDefinitionKey != null) {
                log.info("Direct ID lookup returned empty, falling back to process key lookup: {}", processDefinitionKey);
                actions = actionDefinitionRepository.findByProcessDefinitionKey(processDefinitionKey);
                log.info("Fallback found {} actions for processDefinitionKey {}", actions.size(), processDefinitionKey);
            }
            
            // Fallback 2: 直接从 dw_action_definitions 表查找（BPMN 中的 actionIds 来自此表）
            if (actions.isEmpty()) {
                log.info("sys_action_definitions lookup returned empty, falling back to dw_action_definitions for IDs: {}", actionIds);
                actions = actionDefinitionRepository.findFromDwByIds(actionIds);
                log.info("dw_action_definitions fallback found {} actions", actions.size());
            }
            
            if (actions.isEmpty()) {
                log.warn("No action definitions found for IDs: {}", actionIds);
                return Collections.emptyList();
            }
            
            log.info("Found {} action definitions", actions.size());
            
            return actions.stream()
                .map(this::toTaskActionInfo)
                .collect(Collectors.toList());
                
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
