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
 * Resolves task-level actions from BPMN metadata.
 * Loads available actions for a task from the workflow engine and local definitions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskActionService {
    
    private final WorkflowEngineClient workflowEngineClient;
    private final ActionDefinitionRepository actionDefinitionRepository;
    
    /**
     * Returns the list of actions enabled for a task.
     * Fetches {@code actionIds} from the workflow engine, then loads definitions from the database.
     */
    public List<TaskActionInfo> getTaskActions(String taskId) {
        log.debug("TaskActionService.getTaskActions called for taskId: {}", taskId);
        try {
            // 1. Load actionIds for the task from the workflow engine.
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
            
            // 2. Normalize actionIds (engine JSON often uses numeric arrays deserialized as List<Integer>, not List<String>).
            List<String> actionIds = normalizeActionIdList(data.get("actionIds"));
            
            if (actionIds == null || actionIds.isEmpty()) {
                log.info("No actions defined for task: {}", taskId);
                return Collections.emptyList();
            }
            
            log.info("Found {} action IDs for task {}: {}", actionIds.size(), taskId, actionIds);
            
            // 3. Resolve definitions only for this node's actionIds; do not fall back to all process actions
            // or todo detail would show buttons for unrelated nodes.
            return fetchActionDefinitions(actionIds);
            
        } catch (Exception e) {
            log.error("Error getting task actions for task: " + taskId, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Normalizes engine-returned actionIds (Integer/Long/String list) to String IDs for JPA lookup.
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
    
    /** Maps an ActionDefinition entity to TaskActionInfo. */
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
