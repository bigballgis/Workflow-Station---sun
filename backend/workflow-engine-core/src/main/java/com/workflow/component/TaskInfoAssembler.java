package com.workflow.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.client.AdminCenterClient;
import com.workflow.dto.response.TaskListResult;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;

import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Assembles TaskListResult.TaskInfo objects from Flowable tasks, historic tasks,
 * and ExtendedTaskInfo entities. Also handles user display-name resolution.
 * Extracted from TaskQueryService.
 */
@Slf4j
@Component
public class TaskInfoAssembler {

    private static final ObjectMapper USER_REF_OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    @Autowired
    private TaskService taskService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private AdminCenterClient adminCenterClient;

    @Autowired
    private BpmnActionParser bpmnActionParser;

    // ==================== User Display Name Resolution ====================

    public Map<String, String> resolveUserDisplayNames(Collection<String> userIds) {
        Map<String, String> out = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return out;
        }
        for (String userId : userIds) {
            if (userId == null || userId.isBlank()) {
                continue;
            }
            String key = userId.trim();
            out.computeIfAbsent(key, this::resolveUserDisplayName);
        }
        return out;
    }

    public String resolveUserDisplayName(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> userInfo = adminCenterClient.getUserInfo(userId);
            String resolved = pickDisplayNameFromUserInfo(userInfo, userId);
            return resolved != null ? resolved : userId;
        } catch (Exception e) {
            log.warn("Failed to resolve user display name for {}: {}", userId, e.getMessage());
        }
        return userId;
    }

    static String pickDisplayNameFromUserInfo(Map<String, Object> userInfo, String fallback) {
        if (userInfo == null) {
            return fallback;
        }
        String fullName = (String) userInfo.get("fullName");
        if (fullName != null && !fullName.isEmpty()) {
            return fullName;
        }
        String displayName = (String) userInfo.get("displayName");
        if (displayName != null && !displayName.isEmpty()) {
            return displayName;
        }
        String username = (String) userInfo.get("username");
        if (username != null && !username.isEmpty()) {
            return username;
        }
        return fallback;
    }

    // ==================== Task Info Builders ====================

    /** Lightweight converter for To Do list — skips full variable bag. */
    public TaskListResult.TaskInfo convertFlowableTaskToTaskInfo(Task task) {
        return buildTaskInfoFromFlowableTask(task, false);
    }

    /** Detail path — includes full process-variable bag. */
    public TaskListResult.TaskInfo buildTaskInfoFromFlowableTask(Task task) {
        return buildTaskInfoFromFlowableTask(task, true);
    }

    public TaskListResult.TaskInfo buildTaskInfoFromFlowableTask(Task task, boolean includeVariables) {
        String processDefinitionId = task.getProcessDefinitionId();
        String processDefinitionKey = extractProcessDefinitionKey(processDefinitionId);
        String processDefinitionName = getProcessDefinitionName(processDefinitionId);

        List<String> candidateUserIds = new ArrayList<>();
        List<String> candidateGroupIds = new ArrayList<>();
        for (IdentityLink link : taskService.getIdentityLinksForTask(task.getId())) {
            if (!"candidate".equals(link.getType())) {
                continue;
            }
            if (link.getUserId() != null && !link.getUserId().isBlank()) {
                candidateUserIds.add(link.getUserId());
            }
            if (link.getGroupId() != null && !link.getGroupId().isBlank()) {
                candidateGroupIds.add(link.getGroupId());
            }
        }

        AssignmentType assignmentType;
        String assignmentTarget;
        if (task.getAssignee() != null && !task.getAssignee().isEmpty()) {
            assignmentType = AssignmentType.USER;
            assignmentTarget = task.getAssignee();
        } else if (!candidateUserIds.isEmpty()) {
            assignmentType = AssignmentType.CANDIDATE_USERS;
            assignmentTarget = String.join(",", candidateUserIds);
        } else if (!candidateGroupIds.isEmpty()) {
            assignmentType = AssignmentType.VIRTUAL_GROUP;
            assignmentTarget = String.join(",", candidateGroupIds);
        } else {
            assignmentType = AssignmentType.VIRTUAL_GROUP;
            assignmentTarget = null;
        }

        Map<String, Object> variables = null;
        if (includeVariables && task.getProcessInstanceId() != null) {
            try {
                variables = runtimeService.getVariables(task.getProcessInstanceId());
                log.debug("Retrieved {} variables for task {}",
                    variables != null ? variables.size() : 0, task.getId());
            } catch (Exception e) {
                log.warn("Failed to get variables for process instance {}: {}",
                    task.getProcessInstanceId(), e.getMessage());
                variables = new HashMap<>();
            }

            if (task.getExecutionId() != null && variables != null) {
                try {
                    Object currentItemObj = runtimeService.getVariable(task.getExecutionId(), "currentItem");
                    if (currentItemObj instanceof Map) {
                        variables.put("_currentItem", currentItemObj);
                        log.debug("Injected _currentItem for MI sub-task {}: {}", task.getId(), currentItemObj);
                    }
                } catch (Exception e) {
                    log.debug("No currentItem for task {}: {}", task.getId(), e.getMessage());
                }
            }
        }

        String initiatorId = null;
        String initiatorName = null;
        if (task.getProcessInstanceId() != null) {
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .singleResult();
            if (processInstance != null) {
                initiatorId = processInstance.getStartUserId();
                if (initiatorId != null) {
                    initiatorName = resolveUserDisplayName(initiatorId);
                }
            }
        }
        if (!StringUtils.hasText(initiatorId)) {
            Object initiatorVar = null;
            if (variables != null) {
                initiatorVar = variables.get("initiator");
            } else if (task.getProcessInstanceId() != null) {
                try {
                    initiatorVar = runtimeService.getVariable(task.getProcessInstanceId(), "initiator");
                } catch (Exception e) {
                    log.debug("Failed to read initiator variable for {}: {}",
                            task.getProcessInstanceId(), e.getMessage());
                }
            }
            if (initiatorVar != null) {
                String iv = initiatorVar.toString().trim();
                if (StringUtils.hasText(iv)) {
                    initiatorId = iv;
                    initiatorName = resolveUserDisplayName(initiatorId);
                }
            }
        }

        String bpmnAssigneeType = null;
        String bpmnBusinessUnitId = null;
        if (processDefinitionId != null && task.getTaskDefinitionKey() != null) {
            try {
                bpmnAssigneeType = bpmnActionParser.getUserTaskExtensionPropertyValue(
                        processDefinitionId, task.getTaskDefinitionKey(), "assigneeType");
                String rawBu = bpmnActionParser.getUserTaskExtensionPropertyValue(
                        processDefinitionId, task.getTaskDefinitionKey(), "businessUnitId");
                if (StringUtils.hasText(rawBu)) {
                    bpmnBusinessUnitId = rawBu.trim();
                }
            } catch (Exception e) {
                log.debug("Read bpmn assigneeType for task {}: {}", task.getId(), e.getMessage());
            }
        }
        if (bpmnAssigneeType != null) {
            bpmnAssigneeType = bpmnAssigneeType.trim();
        }

        String currentAssignee = task.getAssignee();
        String currentAssigneeName = null;
        if (currentAssignee != null && !currentAssignee.isEmpty()) {
            currentAssigneeName = resolveUserDisplayName(currentAssignee);
        }

        if (TaskQueryService.isBpmnProcessInitiatorType(bpmnAssigneeType)
                && StringUtils.hasText(initiatorId)
                && !StringUtils.hasText(currentAssignee)
                && initiatorId != null) {
            assignmentType = AssignmentType.USER;
            assignmentTarget = initiatorId.trim();
            currentAssignee = initiatorId.trim();
            currentAssigneeName = initiatorName != null ? initiatorName : resolveUserDisplayName(initiatorId);
            candidateUserIds.clear();
            candidateGroupIds.clear();
            log.info("Normalized task {} JSON to USER/initiator from BPMN assigneeType={} (runtime had no assignee)",
                    task.getId(), bpmnAssigneeType);
        }

        List<String> extractedActionIds = bpmnActionParser.extractActionIds(task);

        return TaskListResult.TaskInfo.builder()
            .taskId(task.getId())
            .taskName(task.getName())
            .taskDescription(task.getDescription())
            .processInstanceId(task.getProcessInstanceId())
            .processDefinitionId(processDefinitionId)
            .processDefinitionKey(processDefinitionKey)
            .processDefinitionName(processDefinitionName)
            .taskDefinitionKey(task.getTaskDefinitionKey())
            .currentAssignee(currentAssignee)
            .currentAssigneeName(currentAssigneeName)
            .assignmentType(assignmentType)
            .bpmnAssigneeType(StringUtils.hasText(bpmnAssigneeType) ? bpmnAssigneeType : null)
            .bpmnBusinessUnitId(bpmnBusinessUnitId)
            .assignmentTarget(assignmentTarget)
            .priority(task.getPriority())
            .createdTime(task.getCreateTime() != null ?
                LocalDateTime.ofInstant(task.getCreateTime().toInstant(), java.time.ZoneId.systemDefault()) : null)
            .dueDate(task.getDueDate() != null ?
                LocalDateTime.ofInstant(task.getDueDate().toInstant(), java.time.ZoneId.systemDefault()) : null)
            .formKey(task.getFormKey())
            .status("PENDING")
            .initiatorId(initiatorId)
            .initiatorName(initiatorName)
            .variables(variables)
            .candidateUserIds(candidateUserIds.isEmpty() ? null : candidateUserIds)
            .candidateGroupIds(candidateGroupIds.isEmpty() ? null : candidateGroupIds)
            .actionIds(extractedActionIds)
            .build();
    }

    public TaskListResult.TaskInfo buildTaskInfoFromHistoricTask(HistoricTaskInstance task) {
        String processDefinitionId = task.getProcessDefinitionId();
        String processDefinitionKey = extractProcessDefinitionKey(processDefinitionId);
        String processDefinitionName = getProcessDefinitionName(processDefinitionId);
        String assignee = task.getAssignee();

        return TaskListResult.TaskInfo.builder()
            .taskId(task.getId())
            .taskName(task.getName())
            .taskDescription(task.getDescription())
            .processInstanceId(task.getProcessInstanceId())
            .processDefinitionId(processDefinitionId)
            .processDefinitionKey(processDefinitionKey)
            .processDefinitionName(processDefinitionName)
            .taskDefinitionKey(task.getTaskDefinitionKey())
            .currentAssignee(assignee)
            .currentAssigneeName(StringUtils.hasText(assignee) ? resolveUserDisplayName(assignee) : null)
            .assignmentType(StringUtils.hasText(assignee) ? AssignmentType.USER : AssignmentType.VIRTUAL_GROUP)
            .assignmentTarget(assignee)
            .priority(task.getPriority())
            .createdTime(task.getCreateTime() != null
                ? LocalDateTime.ofInstant(task.getCreateTime().toInstant(), java.time.ZoneId.systemDefault()) : null)
            .dueDate(task.getDueDate() != null
                ? LocalDateTime.ofInstant(task.getDueDate().toInstant(), java.time.ZoneId.systemDefault()) : null)
            .formKey(task.getFormKey())
            .status("COMPLETED")
            .build();
    }

    public TaskListResult.TaskInfo convertToTaskInfo(ExtendedTaskInfo extendedTaskInfo) {
        String processDefinitionName = getProcessDefinitionName(extendedTaskInfo.getProcessDefinitionId());
        String processDefinitionKey = extractProcessDefinitionKey(extendedTaskInfo.getProcessDefinitionId());

        return TaskListResult.TaskInfo.builder()
            .taskId(extendedTaskInfo.getTaskId())
            .taskName(extendedTaskInfo.getTaskName())
            .taskDescription(extendedTaskInfo.getTaskDescription())
            .processInstanceId(extendedTaskInfo.getProcessInstanceId())
            .processDefinitionId(extendedTaskInfo.getProcessDefinitionId())
            .processDefinitionKey(processDefinitionKey)
            .processDefinitionName(processDefinitionName)
            .taskDefinitionKey(extendedTaskInfo.getTaskDefinitionKey())
            .assignmentType(extendedTaskInfo.getAssignmentType())
            .assignmentTarget(extendedTaskInfo.getAssignmentTarget())
            .currentAssignee(extendedTaskInfo.getCurrentAssignee())
            .priority(extendedTaskInfo.getPriority())
            .dueDate(extendedTaskInfo.getDueDate())
            .status(extendedTaskInfo.getStatus())
            .createdTime(extendedTaskInfo.getCreatedTime())
            .isDelegated(extendedTaskInfo.isDelegated())
            .isClaimed(extendedTaskInfo.isClaimed())
            .isOverdue(extendedTaskInfo.isOverdue())
            .formKey(extendedTaskInfo.getFormKey())
            .businessKey(extendedTaskInfo.getBusinessKey())
            .build();
    }

    // ==================== Process Definition Helpers ====================

    String getProcessDefinitionName(String processDefinitionId) {
        if (processDefinitionId == null || processDefinitionId.isEmpty()) {
            return null;
        }
        try {
            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
            if (processDefinition != null) {
                return processDefinition.getName();
            }
        } catch (Exception e) {
            log.warn("Failed to get process definition name for id: {}", processDefinitionId, e);
        }
        return extractProcessDefinitionKey(processDefinitionId);
    }

    String extractProcessDefinitionKey(String processDefinitionId) {
        if (processDefinitionId == null || processDefinitionId.isEmpty()) {
            return null;
        }
        int colonIndex = processDefinitionId.indexOf(':');
        if (colonIndex > 0) {
            return processDefinitionId.substring(0, colonIndex);
        }
        try {
            ProcessDefinition pd = repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
            if (pd != null) {
                log.debug("Resolved process definition key via repositoryService: {} -> {}", processDefinitionId, pd.getKey());
                return pd.getKey();
            }
        } catch (Exception e) {
            log.warn("Failed to resolve process definition key for ID {}: {}", processDefinitionId, e.getMessage());
        }
        return processDefinitionId;
    }

    // ==================== User ID Normalization (static helpers) ====================

    static String normalizeFlowableUserIdValue(Object raw) {
        if (raw == null) {
            return null;
        }
        String id;
        if (raw instanceof Map<?, ?> m) {
            id = null;
            for (String k : new String[]{"id", "userId", "user_id", "value"}) {
                Object v = m.get(k);
                if (v != null) {
                    String s = String.valueOf(v).trim();
                    if (!s.isEmpty()) {
                        id = s;
                        break;
                    }
                }
            }
            if (id == null) {
                id = String.valueOf(raw);
            }
        } else {
            id = extractUserIdFromString(String.valueOf(raw));
        }
        if (id == null) {
            return null;
        }
        String t = id.trim();
        if (t.isEmpty() || "null".equalsIgnoreCase(t)) {
            return null;
        }
        if (t.length() > 255) {
            log.warn("Repair orphan MI task: skip assignee id longer than 255 chars");
            return null;
        }
        return t;
    }

    private static String extractUserIdFromString(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return value;
        }
        String mapLikeId = extractUserIdFromMapLikeString(value);
        if (mapLikeId != null) {
            return mapLikeId;
        }
        if (value.startsWith("\"")) {
            try {
                Object parsed = USER_REF_OBJECT_MAPPER.readValue(value, Object.class);
                if (parsed instanceof String parsedString) {
                    return extractUserIdFromString(parsedString);
                }
            } catch (Exception ignored) {
                // Not a JSON string literal; try the generic UUID fallback below.
            }
        }
        if (value.startsWith("{") || value.startsWith("[")) {
            try {
                Object parsed = USER_REF_OBJECT_MAPPER.readValue(value, Object.class);
                if (parsed instanceof Map<?, ?> map) {
                    String id = extractUserIdFromRefMap(map);
                    return id != null ? id : value;
                }
                if (parsed instanceof List<?> list && !list.isEmpty()) {
                    Object first = list.get(0);
                    if (first instanceof Map<?, ?> map) {
                        String id = extractUserIdFromRefMap(map);
                        return id != null ? id : value;
                    }
                    return first != null ? String.valueOf(first).trim() : value;
                }
            } catch (Exception ignored) {
                // Not JSON, keep the original string.
            }
        }
        Matcher matcher = UUID_PATTERN.matcher(value);
        if (matcher.find()) {
            return matcher.group();
        }
        return value;
    }

    private static String extractUserIdFromMapLikeString(String value) {
        if (value == null || !value.startsWith("{") || !value.endsWith("}") || !value.contains("=")) {
            return null;
        }
        for (String key : new String[]{"id", "userId", "user_id", "value"}) {
            Matcher matcher = Pattern.compile("(?i)(^|[,\\{]\\s*)" + Pattern.quote(key) + "\\s*=\\s*([^,}]+)")
                    .matcher(value);
            if (matcher.find()) {
                String id = matcher.group(2).trim();
                return id.isEmpty() || "null".equalsIgnoreCase(id) ? null : id;
            }
        }
        return null;
    }

    private static String extractUserIdFromRefMap(Map<?, ?> map) {
        for (String k : new String[]{"id", "userId", "user_id", "value"}) {
            Object v = map.get(k);
            if (v != null) {
                String s = String.valueOf(v).trim();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        }
        return null;
    }
}
