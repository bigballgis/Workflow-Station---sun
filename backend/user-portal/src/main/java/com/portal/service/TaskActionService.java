package com.portal.service;

import com.portal.client.WorkflowEngineClient;
import com.portal.dto.TaskActionInfo;
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
        log.info("=== TaskActionService.getTaskActions called for taskId: {}", taskId);
        try {
            // 1. 从 Workflow Engine 获取任务的 actionIds
            Optional<Map<String, Object>> result = workflowEngineClient.getTaskById(taskId);
            
            if (result.isEmpty()) {
                log.warn("Task not found in workflow engine: {}", taskId);
                return Collections.emptyList();
            }
            
            Map<String, Object> responseBody = result.get();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
            
            if (data == null) {
                log.warn("No data in workflow engine response for task: {}", taskId);
                return Collections.emptyList();
            }
            
            // 2. 提取 actionIds
            @SuppressWarnings("unchecked")
            List<String> actionIds = (List<String>) data.get("actionIds");
            
            if (actionIds == null || actionIds.isEmpty()) {
                log.info("No actions defined for task: {}", taskId);
                return Collections.emptyList();
            }
            
            log.info("Found {} action IDs for task {}: {}", actionIds.size(), taskId, actionIds);
            
            // 3. 从数据库获取action定义
            return fetchActionDefinitions(actionIds);
            
        } catch (Exception e) {
            log.error("Error getting task actions for task: " + taskId, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 从数据库批量获取action定义
     */
    private List<TaskActionInfo> fetchActionDefinitions(List<String> actionIds) {
        try {
            log.info("Fetching {} action definitions from database", actionIds.size());
            
            List<ActionDefinition> actions = actionDefinitionRepository.findAllById(actionIds);
            
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
