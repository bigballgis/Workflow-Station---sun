package com.workflow.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.aspect.WorkflowAuditAspect.Auditable;
import com.workflow.client.AdminCenterClient;
import com.workflow.dto.request.TaskAssignmentRequest;
import com.workflow.dto.request.TaskClaimRequest;
import com.workflow.dto.request.TaskDelegationRequest;
import com.workflow.dto.request.TaskReturnRequest;
import com.workflow.dto.response.TaskAssignmentResult;
import com.workflow.dto.response.TaskListResult;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import com.workflow.enums.AuditOperationType;
import com.workflow.enums.AuditResourceType;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;
import com.workflow.repository.ExtendedTaskInfoRepository;
import com.workflow.service.UserPermissionService;
import com.workflow.util.InitiatorOrphanRepairEligibility;
import com.workflow.util.RollbackAssigneeFallbackSupport;

import com.platform.messaging.support.NotificationDispatchHelper;
import com.platform.common.i18n.I18nService;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Task Manager Component
 * Responsible for multi-dimension task assignment, query, delegation and completion
 * Supports three assignment types: user, virtual group, department role
 */
@Slf4j
@Component
@Transactional
public class TaskManagerComponent {

    private static final ObjectMapper USER_REF_OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    @Autowired
    private TaskService taskService;
    
    @Autowired
    private RuntimeService runtimeService;
    
    @Autowired
    private HistoryService historyService;
    
    @Autowired
    private RepositoryService repositoryService;
    
    @Autowired
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;
    
    @Autowired
    private UserPermissionService userPermissionService;
    
    @Autowired
    private AdminCenterClient adminCenterClient;
    
    @Autowired
    private BpmnActionParser bpmnActionParser;
    
    @Autowired
    private SubTableDataInjector subTableDataInjector;
    
    @Autowired
    private MultiInstanceDataResolver multiInstanceDataResolver;
    
    @Autowired
    private MultiInstanceCanceller multiInstanceCanceller;
    
    @Autowired(required = false)
    private com.workflow.messaging.SubTableUpdatePublisher updatePublisher;

    @Autowired
    private NotificationDispatchHelper notificationDispatchHelper;

    @Autowired
    private I18nService i18nService;

    // ==================== Task Query ====================

    /**
     * Query user pending tasks (including directly assigned and candidate tasks)
     * Supports multi-dimension task assignment types
     */
    public TaskListResult getUserTasks(String userId, int page, int size) {
        return getUserTasks(userId, page, size, null);
    }

    /**
     * @param activeBusinessUnitId portal current workspace BU (optional); filters out FIXED_BU_ROLE tasks mismatching current workspace when non-null
     */
    public TaskListResult getUserTasks(String userId, int page, int size, String activeBusinessUnitId) {
        try {
            validateUserId(userId);
            
            int fetchLimit = (page + 1) * size;
            repairOrphanMultiInstanceTasks(fetchLimit);
            
            List<Task> assignedTasks = taskService.createTaskQuery()
                .taskAssignee(userId)
                .orderByTaskCreateTime().desc()
                .listPage(0, fetchLimit);
            
            List<Task> candidateTasks = taskService.createTaskQuery()
                .taskCandidateUser(userId)
                .orderByTaskCreateTime().desc()
                .listPage(0, fetchLimit);
            
            java.util.LinkedHashMap<String, Task> taskMap = new java.util.LinkedHashMap<>();
            for (Task t : assignedTasks) taskMap.putIfAbsent(t.getId(), t);
            for (Task t : candidateTasks) taskMap.putIfAbsent(t.getId(), t);
            mergeOrphanInitiatorTasksRepair(userId, fetchLimit, taskMap);
            
            List<Task> uniqueTasks = new ArrayList<>(taskMap.values());
            uniqueTasks.sort((t1, t2) -> t2.getCreateTime().compareTo(t1.getCreateTime()));
            uniqueTasks = applyActiveWorkspaceBuTaskFilter(uniqueTasks, activeBusinessUnitId, userId);
            
            long totalCount;
            if (uniqueTasks.size() < fetchLimit) {
                totalCount = uniqueTasks.size();
            } else {
                long assignedCount = taskService.createTaskQuery()
                    .taskAssignee(userId).count();
                long candidateCount = taskService.createTaskQuery()
                    .taskCandidateUser(userId).count();
                totalCount = assignedCount + candidateCount;
            }
            
            int start = page * size;
            int end = Math.min(start + size, uniqueTasks.size());
            List<Task> pagedTasks = start < uniqueTasks.size() 
                ? uniqueTasks.subList(start, end) 
                : Collections.emptyList();
            
            List<TaskListResult.TaskInfo> taskInfos = pagedTasks.stream()
                .map(this::convertFlowableTaskToTaskInfo)
                .toList();
            
            int totalPages = (int) Math.ceil((double) totalCount / size);
            
            return TaskListResult.builder()
                .tasks(taskInfos)
                .totalCount(totalCount)
                .currentPage(page)
                .pageSize(size)
                .totalPages(totalPages)
                .build();
                
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_QUERY_ERROR", 
                "Failed to query user pending tasks: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert Flowable Task to TaskInfo (shared logic with detail query, includes candidate users/groups)
     */
    private TaskListResult.TaskInfo convertFlowableTaskToTaskInfo(Task task) {
        return buildTaskInfoFromFlowableTask(task);
    }
    
    /**
     * Resolve user display name
     * Returns fullName first, then displayName, then username, finally userId
     */
    private String resolveUserDisplayName(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> userInfo = adminCenterClient.getUserInfo(userId);
            if (userInfo != null) {
                // Prefer fullName
                String fullName = (String) userInfo.get("fullName");
                if (fullName != null && !fullName.isEmpty()) {
                    return fullName;
                }
                // Then displayName
                String displayName = (String) userInfo.get("displayName");
                if (displayName != null && !displayName.isEmpty()) {
                    return displayName;
                }
                // Then username
                String username = (String) userInfo.get("username");
                if (username != null && !username.isEmpty()) {
                    return username;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to resolve user display name for {}: {}", userId, e.getMessage());
        }
        return userId;
    }
    
    /**
     * Get process definition name
     */
    private String getProcessDefinitionName(String processDefinitionId) {
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
    
    /**
     * Extract processDefinitionKey from processDefinitionId
     * Format: key:version:uuid (e.g. Process_PurchaseRequest:2:b550b1fe-f0b0-11f0-b82f-00ff197375e0)
     * Flowable 7.0 may return UUID only; falls back to repositoryService for actual key
     */
    private String extractProcessDefinitionKey(String processDefinitionId) {
        if (processDefinitionId == null || processDefinitionId.isEmpty()) {
            return null;
        }
        // Standard format: key:version:uuid
        int colonIndex = processDefinitionId.indexOf(':');
        if (colonIndex > 0) {
            return processDefinitionId.substring(0, colonIndex);
        }
        // Flowable 7.0 may return UUID only; query repositoryService for actual key
        try {
            org.flowable.engine.repository.ProcessDefinition pd = repositoryService
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
    
    /**
     * Query tasks by process instance ID
     */
    public TaskListResult getTasksByProcessInstance(String processInstanceId, int page, int size) {
        try {
            // Query all tasks for the process instance
            List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .orderByTaskCreateTime()
                .desc()
                .listPage(page * size, size);
            
            long totalCount = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .count();
            
            // Convert to result object
            List<TaskListResult.TaskInfo> taskInfos = tasks.stream()
                .map(this::convertFlowableTaskToTaskInfo)
                .toList();
            
            int totalPages = (int) Math.ceil((double) totalCount / size);
            
            return TaskListResult.builder()
                .tasks(taskInfos)
                .totalCount(totalCount)
                .currentPage(page)
                .pageSize(size)
                .totalPages(totalPages)
                .build();
                
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_QUERY_ERROR", 
                "Failed to query tasks by process instance: " + e.getMessage(), e);
        }
    }
    /**
     * Query all visible tasks for user (including virtual group and department role tasks)
     * 
     * Query tasks directly from Flowable TaskService
     */
    public TaskListResult getUserAllVisibleTasks(String userId, List<String> groupIds, 
                                               List<String> deptRoles, int page, int size) {
        return getUserAllVisibleTasks(userId, groupIds, deptRoles, page, size, null);
    }

    /**
     * @param activeBusinessUnitId portal current workspace BU (optional); filters out FIXED_BU_ROLE tasks mismatching current workspace when non-null
     */
    public TaskListResult getUserAllVisibleTasks(String userId, List<String> groupIds, 
                                               List<String> deptRoles, int page, int size,
                                               String activeBusinessUnitId) {
        try {
            validateUserId(userId);
            
            int fetchLimit = (page + 1) * size;
            repairOrphanBuRolePoolTasks(fetchLimit);
            repairOrphanMultiInstanceTasks(fetchLimit);
            
            List<Task> assignedTasks = taskService.createTaskQuery()
                .taskAssignee(userId)
                .orderByTaskCreateTime().desc()
                .listPage(0, fetchLimit);
            
            List<Task> candidateTasks = taskService.createTaskQuery()
                .taskCandidateUser(userId)
                .orderByTaskCreateTime().desc()
                .listPage(0, fetchLimit);
            
            java.util.LinkedHashMap<String, Task> taskMap = new java.util.LinkedHashMap<>();
            for (Task t : assignedTasks) taskMap.putIfAbsent(t.getId(), t);
            for (Task t : candidateTasks) taskMap.putIfAbsent(t.getId(), t);
            
            if (groupIds != null && !groupIds.isEmpty()) {
                List<Task> groupTasks = taskService.createTaskQuery()
                        .taskCandidateGroupIn(groupIds)
                        .orderByTaskCreateTime().desc()
                        .listPage(0, fetchLimit);
                for (Task t : groupTasks) taskMap.putIfAbsent(t.getId(), t);
            }
            mergeOrphanInitiatorTasksRepair(userId, fetchLimit, taskMap);
            
            List<Task> uniqueTasks = new ArrayList<>(taskMap.values());
            uniqueTasks.sort((t1, t2) -> t2.getCreateTime().compareTo(t1.getCreateTime()));
            uniqueTasks = applyActiveWorkspaceBuTaskFilter(uniqueTasks, activeBusinessUnitId, userId);
            
            long totalCount;
            if (uniqueTasks.size() < fetchLimit) {
                totalCount = uniqueTasks.size();
            } else {
                long assignedCount = taskService.createTaskQuery()
                    .taskAssignee(userId).count();
                long candidateCount = taskService.createTaskQuery()
                    .taskCandidateUser(userId).count();
                totalCount = assignedCount + candidateCount;
                if (groupIds != null && !groupIds.isEmpty()) {
                    totalCount += taskService.createTaskQuery()
                        .taskCandidateGroupIn(groupIds).count();
                }
            }
            
            int start = page * size;
            int end = Math.min(start + size, uniqueTasks.size());
            List<Task> pagedTasks = start < uniqueTasks.size() 
                ? uniqueTasks.subList(start, end) 
                : Collections.emptyList();
            
            List<TaskListResult.TaskInfo> taskInfos = pagedTasks.stream()
                .map(this::convertFlowableTaskToTaskInfo)
                .toList();
            
            int totalPages = (int) Math.ceil((double) totalCount / size);
            
            return TaskListResult.builder()
                .tasks(taskInfos)
                .totalCount(totalCount)
                .currentPage(page)
                .pageSize(size)
                .totalPages(totalPages)
                .build();
                
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_QUERY_ERROR", 
                "Failed to query user visible tasks: " + e.getMessage(), e);
        }
    }

    /**
     * Repair legacy orphan pool tasks:
     * Some historical tasks were created before assignee listener fixes and ended up with
     * no assignee + no candidate identity links, even though BPMN assigneeType is BU_ROLE.
     * This method backfills candidate users/assignee from BPMN extension so task query works.
     */
    private void repairOrphanBuRolePoolTasks(int fetchLimit) {
        List<Task> unassigned = taskService.createTaskQuery()
                .taskUnassigned()
                .orderByTaskCreateTime().desc()
                .listPage(0, fetchLimit);
        for (Task t : unassigned) {
            try {
                List<IdentityLink> links = taskService.getIdentityLinksForTask(t.getId());
                boolean hasCandidate = false;
                for (IdentityLink l : links) {
                    if ("candidate".equals(l.getType())
                            && ((l.getUserId() != null && !l.getUserId().isBlank())
                            || (l.getGroupId() != null && !l.getGroupId().isBlank()))) {
                        hasCandidate = true;
                        break;
                    }
                }
                if (hasCandidate) {
                    continue;
                }
                String pdId = t.getProcessDefinitionId();
                String defKey = t.getTaskDefinitionKey();
                String at = bpmnActionParser.getUserTaskExtensionPropertyValue(pdId, defKey, "assigneeType");
                if (at == null || at.isBlank()) {
                    continue;
                }
                String u = at.trim().toUpperCase(java.util.Locale.ROOT);
                if (!"BU_ROLE".equals(u) && !"FIXED_BU_ROLE".equals(u)) {
                    continue;
                }
                String roleId = bpmnActionParser.getUserTaskExtensionPropertyValue(pdId, defKey, "roleId");
                String buId = bpmnActionParser.getUserTaskExtensionPropertyValue(pdId, defKey, "businessUnitId");
                if (roleId == null || roleId.isBlank() || buId == null || buId.isBlank()) {
                    log.warn("Orphan BU_ROLE task {} has missing roleId/businessUnitId; skip repair", t.getId());
                    continue;
                }
                if (!adminCenterClient.isEligibleRole(buId.trim(), roleId.trim())) {
                    log.warn("Orphan BU_ROLE task {} role {} not eligible for bu {}; skip repair",
                            t.getId(), roleId, buId);
                    continue;
                }
                List<String> users = adminCenterClient.getUsersByBusinessUnitAndRole(buId.trim(), roleId.trim());
                if (users == null || users.isEmpty()) {
                    log.warn("Orphan BU_ROLE task {} resolved no users for bu={} role={}", t.getId(), buId, roleId);
                    continue;
                }
                if (users.size() == 1) {
                    taskService.setAssignee(t.getId(), users.get(0).trim());
                    log.info("Repaired orphan BU_ROLE task {} with direct assignee {}", t.getId(), users.get(0));
                } else {
                    for (String uid : users) {
                        if (uid != null && !uid.isBlank()) {
                            taskService.addCandidateUser(t.getId(), uid.trim());
                        }
                    }
                    log.info("Repaired orphan BU_ROLE task {} with {} candidate users", t.getId(), users.size());
                }
            } catch (Exception ex) {
                log.warn("Repair orphan BU_ROLE task {} failed: {}", t.getId(), ex.getMessage());
            }
        }
    }

    /**
     * Repair multi-instance user tasks where TaskAssignmentListener failed to set the assignee.
     * Reads the execution-scoped {@code currentItem} variable and extracts the assignee from the
     * BPMN {@code assigneeField} (or falls back to {@code assignee_user_id}).
     */
    @SuppressWarnings("unchecked")
    private void repairOrphanMultiInstanceTasks(int fetchLimit) {
        List<Task> unassigned = taskService.createTaskQuery()
                .taskUnassigned()
                .orderByTaskCreateTime().desc()
                .listPage(0, fetchLimit);
        for (Task t : unassigned) {
            try {
                String pdId = t.getProcessDefinitionId();
                String defKey = t.getTaskDefinitionKey();
                String at = bpmnActionParser.getUserTaskExtensionPropertyValue(pdId, defKey, "assigneeType");
                if (!"ELEMENT_VARIABLE".equalsIgnoreCase(at != null ? at.trim() : "")) {
                    continue;
                }
                Object currentItemObj = runtimeService.getVariable(t.getExecutionId(), "currentItem");
                if (!(currentItemObj instanceof Map)) {
                    continue;
                }
                Map<String, Object> currentItem = (Map<String, Object>) currentItemObj;
                String assigneeField = bpmnActionParser.getUserTaskExtensionPropertyValue(pdId, defKey, "assigneeField");
                Object assigneeObj = null;
                if (assigneeField != null && !assigneeField.isBlank()) {
                    assigneeObj = currentItem.get(assigneeField.trim());
                }
                if (assigneeObj == null) {
                    assigneeObj = currentItem.get("assigneeId");
                }
                if (assigneeObj == null) {
                    assigneeObj = currentItem.get("assignee_user_id");
                }
                if (assigneeObj == null) {
                    continue;
                }
                String assigneeId = normalizeFlowableUserIdValue(assigneeObj);
                if (assigneeId == null || assigneeId.isBlank()) {
                    continue;
                }
                taskService.setAssignee(t.getId(), assigneeId);
                log.info("Repaired orphan MI task {} assigned to {} (defKey={})",
                        t.getId(), assigneeId, defKey);
            } catch (Exception ex) {
                log.warn("Repair orphan MI task {} failed: {}", t.getId(), ex.getMessage());
            }
        }
    }

    private static String normalizeFlowableUserIdValue(Object raw) {
        if (raw == null) {
            return null;
        }
        String id;
        if (raw instanceof java.util.Map<?, ?> m) {
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
                if (parsed instanceof java.util.Map<?, ?> map) {
                    String id = extractUserIdFromRefMap(map);
                    return id != null ? id : value;
                }
                if (parsed instanceof List<?> list && !list.isEmpty()) {
                    Object first = list.get(0);
                    if (first instanceof java.util.Map<?, ?> map) {
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

    private static String extractUserIdFromRefMap(java.util.Map<?, ?> map) {
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

    /**
     * Merge tasks unassigned but with initiator variable matching current user, and idempotently write back assignee.
     * Covers cases where listener did not execute, variable type is Long, or assignee was not written causing taskAssignee query misses.
     * <p><b>Only applies when BPMN node is initiator handling (INITIATOR / PROCESS_INITIATOR or equivalent flowable:assignee)</b>;
     * non-initiator nodes like BU_ROLE will not have initiator written back here, avoiding misassignment.</p>
     * <p>If assignee was previously misassigned to initiator by old logic, use transfer, dev DB UPDATE, or purge+rerun the instance; this method does not auto-correct.</p>
     * <p>When BU_ROLE resolves to single user, engine assigns directly with no Claim, consistent with portal display.</p>
     */
    private void mergeOrphanInitiatorTasksRepair(String userId, int fetchLimit,
            java.util.LinkedHashMap<String, Task> taskMap) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        String uid = userId.trim();
        try {
            appendUnassignedInitiatorTasks(uid, fetchLimit, taskMap, false);
            if (uid.matches("^-?\\d+$")) {
                appendUnassignedInitiatorTasks(uid, fetchLimit, taskMap, true);
            }
        } catch (Exception e) {
            log.warn("mergeOrphanInitiatorTasksRepair for user {}: {}", uid, e.getMessage());
        }
    }

    private void appendUnassignedInitiatorTasks(String userId, int fetchLimit,
            java.util.LinkedHashMap<String, Task> taskMap, boolean initiatorVarAsLong) {
        var query = taskService.createTaskQuery()
                .taskUnassigned()
                .orderByTaskCreateTime().desc();
        if (initiatorVarAsLong) {
            query.processVariableValueEquals("initiator", Long.parseLong(userId));
        } else {
            query.processVariableValueEquals("initiator", userId);
        }
        List<Task> orphans = query.listPage(0, fetchLimit);
        for (Task t : orphans) {
            try {
                String assigneeType = bpmnActionParser.getUserTaskExtensionPropertyValue(
                        t.getProcessDefinitionId(), t.getTaskDefinitionKey(), "assigneeType");
                String flowableAssignee = null;
                if (!StringUtils.hasText(assigneeType)) {
                    flowableAssignee = readUserTaskAssigneeExpression(
                            t.getProcessDefinitionId(), t.getTaskDefinitionKey());
                }
                if (!InitiatorOrphanRepairEligibility.shouldRepair(assigneeType, flowableAssignee)) {
                    log.debug(
                            "Skip initiator orphan repair for task {} (not initiator user task per BPMN; assigneeType={}, flowableAssignee={})",
                            t.getId(), assigneeType, flowableAssignee);
                    continue;
                }
                taskService.setAssignee(t.getId(), userId);
                Task refreshed = taskService.createTaskQuery().taskId(t.getId()).singleResult();
                if (refreshed != null) {
                    taskMap.putIfAbsent(refreshed.getId(), refreshed);
                }
            } catch (Exception ex) {
                log.warn("Could not repair assignee for orphan task {}: {}", t.getId(), ex.getMessage());
                taskMap.putIfAbsent(t.getId(), t);
            }
        }
    }

    /**
     * Standard assignee expression on UserTask in Flowable BpmnModel (fallback when no extension assigneeType).
     */
    private String readUserTaskAssigneeExpression(String processDefinitionId, String taskDefinitionKey) {
        if (!StringUtils.hasText(processDefinitionId) || !StringUtils.hasText(taskDefinitionKey)) {
            return null;
        }
        try {
            BpmnModel model = repositoryService.getBpmnModel(processDefinitionId);
            if (model == null) {
                return null;
            }
            FlowElement el = model.getFlowElement(taskDefinitionKey);
            if (!(el instanceof UserTask ut)) {
                return null;
            }
            String a = ut.getAssignee();
            return StringUtils.hasText(a) ? a.trim() : null;
        } catch (Exception e) {
            log.debug("readUserTaskAssigneeExpression: {}", e.getMessage());
            return null;
        }
    }
    
    // ==================== Task Assignment, Delegation, Claim ====================

    /**
     * Assign task (supports multiple assignment types)
     */
    @Auditable(
        operationType = AuditOperationType.ASSIGN_TASK,
        resourceType = AuditResourceType.TASK,
        description = "Assign task",
        captureArgs = true,
        captureResult = true
    )
    public TaskAssignmentResult assignTask(String taskId, TaskAssignmentRequest request) {
        try {
            // Validate request parameters
            validateTaskAssignmentRequest(request);
            
            // Verify task exists
            Task flowableTask = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
            
            if (flowableTask == null) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "Task not found", taskId)));
            }
            
            // Find or create extended task info
            ExtendedTaskInfo extendedTaskInfo = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId)
                .orElse(createExtendedTaskInfo(flowableTask, request));
            
            // Update assignment info
            updateTaskAssignment(extendedTaskInfo, request);
            
            // Update Flowable task by assignment type
            updateFlowableTaskAssignment(flowableTask, request);
            
            // Save extended task info
            extendedTaskInfo = extendedTaskInfoRepository.save(extendedTaskInfo);
            
            // Publish task assignment event
            publishTaskAssignmentEvent(extendedTaskInfo, request);
            
            return TaskAssignmentResult.success(
                taskId, 
                request.getAssignmentType(), 
                request.getAssignmentTarget(),
                request.getOperatorUserId(),
                "Task assigned successfully");
                
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_ASSIGN_ERROR",
                "Task assignment failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Delegate task (tasks of any assignment type can be delegated)
     */
    public TaskAssignmentResult delegateTask(String taskId, TaskDelegationRequest request) {
        try {
            // Validate request parameters
            validateTaskDelegationRequest(request);
            
            // Find extended task info
            ExtendedTaskInfo extendedTaskInfo = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId)
                .orElseThrow(() -> new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "Task not found", taskId))));
            
            // Verify delegation permission
            validateDelegationPermission(extendedTaskInfo, request.getDelegatedBy());
            
            // Check if task is already completed
            if (extendedTaskInfo.isCompleted()) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "Task already completed, cannot delegate", taskId)));
            }
            
            // Execute delegation operation
            extendedTaskInfo.delegateTask(
                request.getDelegatedTo(), 
                request.getDelegatedBy(), 
                request.getEffectiveDelegationReason());
            
            // Update Flowable task assignee
            String previousActor = Authentication.getAuthenticatedUserId();
            try {
                Authentication.setAuthenticatedUserId(request.getDelegatedBy());
                taskService.setAssignee(taskId, request.getDelegatedTo());
            } finally {
                Authentication.setAuthenticatedUserId(previousActor);
            }
            
            // Save extended task info
            extendedTaskInfo = extendedTaskInfoRepository.save(extendedTaskInfo);
            
            // Publish task delegation event
            publishTaskDelegationEvent(extendedTaskInfo, request);
            
            return TaskAssignmentResult.success(
                taskId, 
                AssignmentType.USER, // becomes USER after delegation
                request.getDelegatedTo(),
                request.getDelegatedBy(),
                "Task delegated successfully");
                
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_DELEGATION_ERROR", 
                "Task delegation failed: " + e.getMessage(), e);
        }
    }
    /**
     * Claim task (virtual group and department role tasks)
     * Queries Flowable TaskService first to ensure all tasks can be claimed
     * Can claim tasks even if not present in ExtendedTaskInfo table
     */
    public TaskAssignmentResult claimTask(String taskId, TaskClaimRequest request) {
        try {
            // Validate request parameters
            validateTaskClaimRequest(request);
            
            // First check if task exists in Flowable
            Task flowableTask = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
            
            if (flowableTask == null) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "Task not found", taskId)));
            }
            
            // Check if task is already claimed (has assignee)
            if (flowableTask.getAssignee() != null && !flowableTask.getAssignee().isEmpty()) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "Task already claimed", taskId)));
            }
            
            // Find extended task info (optional)
            Optional<ExtendedTaskInfo> extendedTaskInfoOpt = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId);
            
            // If extended task info exists, perform additional validation
            if (extendedTaskInfoOpt.isPresent()) {
                ExtendedTaskInfo extendedTaskInfo = extendedTaskInfoOpt.get();
                
                // Verify claim permission
                validateClaimPermission(extendedTaskInfo, request.getClaimedBy());
                
                // Check if task is already completed
                if (extendedTaskInfo.isCompleted()) {
                    throw new WorkflowValidationException(Collections.singletonList(
                        new WorkflowValidationException.ValidationError(
                            "taskId", "Task already completed, cannot claim", taskId)));
                }
                
                // Execute claim operation
                extendedTaskInfo.claimTask(request.getClaimedBy());
                extendedTaskInfoRepository.save(extendedTaskInfo);
                
                // Publish task claim event
                publishTaskClaimEvent(extendedTaskInfo, request);
            }
            
            // Update Flowable task assignee
            taskService.claim(taskId, request.getClaimedBy());
            
            return TaskAssignmentResult.success(
                taskId, 
                AssignmentType.USER, // becomes USER after claim
                request.getClaimedBy(),
                request.getClaimedBy(),
                "Task claimed successfully");
                
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_CLAIM_ERROR", 
                "Task claim failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Unclaim task
     * Symmetric to {@link #claimTask}: uses Flowable runtime task as source of truth, extended table is optional sync
     */
    public TaskAssignmentResult unclaimTask(String taskId, String userId) {
        try {
            validateUserId(userId);

            Task flowableTask = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();

            if (flowableTask == null) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "Task not found", taskId)));
            }

            String assignee = flowableTask.getAssignee();
            if (assignee == null || assignee.isEmpty()) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "Task not claimed", taskId)));
            }
            if (!engineActorMatchesPortalUser(assignee, userId)) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "userId", "Only assignee can unclaim", userId)));
            }

            Optional<ExtendedTaskInfo> extendedTaskInfoOpt = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId);

            AssignmentType resultType = AssignmentType.CANDIDATE_USERS;
            String resultTarget = null;

            if (extendedTaskInfoOpt.isPresent()) {
                ExtendedTaskInfo extendedTaskInfo = extendedTaskInfoOpt.get();
                if (extendedTaskInfo.isCompleted()) {
                    throw new WorkflowValidationException(Collections.singletonList(
                        new WorkflowValidationException.ValidationError(
                            "taskId", "Task already completed, cannot unclaim", taskId)));
                }
                if (extendedTaskInfo.isClaimed() && extendedTaskInfo.getClaimedBy() != null
                        && !engineActorMatchesPortalUser(extendedTaskInfo.getClaimedBy(), userId)) {
                    throw new WorkflowValidationException(Collections.singletonList(
                        new WorkflowValidationException.ValidationError(
                            "userId", "Only the claimer can unclaim", userId)));
                }
                if (extendedTaskInfo.isClaimed()) {
                    extendedTaskInfo.unclaimTask();
                    extendedTaskInfoRepository.save(extendedTaskInfo);
                }
                resultType = extendedTaskInfo.getAssignmentType();
                resultTarget = extendedTaskInfo.getAssignmentTarget();
            }

            taskService.unclaim(taskId);

            return TaskAssignmentResult.success(
                taskId,
                resultType,
                resultTarget,
                userId,
                "Task unclaimed successfully");

        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_UNCLAIM_ERROR",
                "Task unclaim failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Transfer task
     */
    public TaskAssignmentResult transferTask(String taskId, String fromUserId, String toUserId, String reason) {
        try {
            // Validate parameters
            validateUserId(fromUserId);
            validateUserId(toUserId);
            
            // First check if task exists in Flowable
            Task flowableTask = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
            
            if (flowableTask == null) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "Task not found", taskId)));
            }
            
            // Find extended task info (optional)
            Optional<ExtendedTaskInfo> extendedTaskInfoOpt = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId);
            
            if (extendedTaskInfoOpt.isPresent()) {
                ExtendedTaskInfo extendedTaskInfo = extendedTaskInfoOpt.get();
                
                // Check if task is already completed
                if (extendedTaskInfo.isCompleted()) {
                    throw new WorkflowValidationException(Collections.singletonList(
                        new WorkflowValidationException.ValidationError(
                            "taskId", "Task already completed, cannot transfer", taskId)));
                }
                
                // Verify transfer permission
                validateCompletePermission(extendedTaskInfo, fromUserId);
                
                // Execute transfer - directly change assignee
                extendedTaskInfo.setAssignmentType(AssignmentType.USER);
                extendedTaskInfo.setAssignmentTarget(toUserId);
                extendedTaskInfo.setClaimedBy(null);
                extendedTaskInfo.setClaimedTime(null);
                extendedTaskInfo.setDelegatedTo(null);
                extendedTaskInfo.setDelegatedBy(null);
                extendedTaskInfo.setDelegatedTime(null);
                extendedTaskInfo.setDelegationReason(null);
                extendedTaskInfo.updateStatus("ASSIGNED", fromUserId);
                extendedTaskInfoRepository.save(extendedTaskInfo);
            }
            
            // Update Flowable task assignee and record transfer reason in ACT_HI_COMMENT
            String processInstanceId = flowableTask.getProcessInstanceId();
            String previousActor = Authentication.getAuthenticatedUserId();
            try {
                Authentication.setAuthenticatedUserId(fromUserId);
                taskService.setAssignee(taskId, toUserId);
                // Typed comment "transfer" — userId is captured from Authentication context,
                // so flow-history can resolve the originating user via Comment.getUserId().
                taskService.addComment(taskId, processInstanceId, "transfer",
                        reason != null && !reason.isBlank() ? reason : "");
            } finally {
                Authentication.setAuthenticatedUserId(previousActor);
            }

            String taskLabel = flowableTask.getName() != null ? flowableTask.getName() : taskId;
            String reasonText = reason != null && !reason.isBlank()
                    ? i18nService.getMessage("workflow.notification.transfer_reason", reason)
                    : "";
            notificationDispatchHelper.publishToUserAfterCommit(
                    toUserId,
                    "TASK",
                    i18nService.getMessage("workflow.notification.transferred_title"),
                    i18nService.getMessage("workflow.notification.transferred_body",
                            fromUserId, taskLabel, reasonText).trim(),
                    taskLink(taskId),
                    "workflow-engine");
            
            return TaskAssignmentResult.success(
                taskId, 
                AssignmentType.USER,
                toUserId,
                fromUserId,
                "Task transferred successfully");
                
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_TRANSFER_ERROR", 
                "Task transfer failed: " + e.getMessage(), e);
        }
    }
    
    // ==================== Task Completion and Rollback ====================

    /**
     * Complete task (supports delegate completing on behalf of original assignee)
     * 
     * Queries Flowable TaskService first to ensure all tasks can be completed
     * Can complete tasks even if not present in ExtendedTaskInfo table
     */
    public TaskAssignmentResult completeTask(String taskId, String userId,
                                           java.util.Map<String, Object> variables) {
        return completeTask(taskId, userId, variables, true);
    }

    /**
     * Complete task, optionally sending notification to process initiator.
     */
    public TaskAssignmentResult completeTask(String taskId, String userId,
                                           java.util.Map<String, Object> variables,
                                           boolean sendNotification) {
        try {
            // Validate parameters
            validateUserId(userId);
            
            // First check if task exists in Flowable
            Task flowableTask = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
            
            if (flowableTask == null) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "Task not found", taskId)));
            }

            ensureAssigneeForOrphanInitiatorTaskIfNeeded(flowableTask, userId);
            flowableTask = taskService.createTaskQuery()
                    .taskId(taskId)
                    .singleResult();
            if (flowableTask == null) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                            "taskId", "Task not found after assignee repair", taskId)));
            }

            // BPMN initiator node with only candidate chain: claim/setAssignee before complete
            // to avoid API displaying USER-normalized while the DB still has no assignee
            ensureProcessInitiatorAssigneeFromBpmnIfNeeded(flowableTask, userId);
            flowableTask = taskService.createTaskQuery()
                    .taskId(taskId)
                    .singleResult();
            if (flowableTask == null) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                            "taskId", "Task not found after initiator assignee repair", taskId)));
            }

            String taskDisplayName = flowableTask.getName() != null ? flowableTask.getName() : taskId;
            
            // Find extended task info (optional, for recording additional info)
            Optional<ExtendedTaskInfo> extendedTaskInfoOpt = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId);
            
            // If extended task info exists, verify permission and status
            if (extendedTaskInfoOpt.isPresent()) {
                ExtendedTaskInfo extendedTaskInfo = extendedTaskInfoOpt.get();

                // Flowable runtime assignee/candidates take priority; extended table may lag (e.g. initiator node still marked VIRTUAL_GROUP), avoid incorrectly rejecting complete
                if (!flowableRuntimeAuthorizesComplete(flowableTask, userId)) {
                    validateCompletePermission(extendedTaskInfo, userId);
                }

                // Check if task is already completed
                if (extendedTaskInfo.isCompleted()) {
                    throw new WorkflowValidationException(Collections.singletonList(
                        new WorkflowValidationException.ValidationError(
                            "taskId", "Task already completed", taskId)));
                }
                
                // [Multi-Instance Extension] Detect if current task is multi-instance sub-task, if so write back data to sub-table
                if (isMultiInstanceSubTask(extendedTaskInfo)) {
                    log.info("Detected multi-instance sub-task, preparing to write back to sub-table: taskId={}", taskId);
                    handleMultiInstanceSubTaskCompletion(taskId, variables, extendedTaskInfo);
                }
            } else if (!flowableRuntimeAuthorizesComplete(flowableTask, userId)) {
                throw new WorkflowValidationException(Collections.singletonList(
                        new WorkflowValidationException.ValidationError(
                                "userId", "User does not have permission to complete this task", userId)));
            }

            String processInstanceId = flowableTask.getProcessInstanceId();
            String initiatorUserId = resolveInitiatorUserId(processInstanceId);
            if (processInstanceId != null) {
                // Read by TaskAssignmentListener when next task is created, for "current handler" semantics like CURRENT_BU_ROLE
                // Uniformly write portal user primary key (UUID), avoiding process variable pollution when Flowable assignee uses username
                runtimeService.setVariable(processInstanceId, "currentUserId", normalizePortalUserIdForVariable(userId));
            }

            // Merge and preserve initiator: form variables from portal completing first task may lack initiator, preventing subsequent INITIATOR node from resolving assignee
            if (variables != null && !variables.isEmpty() && processInstanceId != null) {
                Object existingInitiator = runtimeService.getVariable(processInstanceId, "initiator");
                if (existingInitiator != null
                        && (variables.get("initiator") == null
                        || variables.get("initiator").toString().isBlank())) {
                    variables.put("initiator", existingInitiator);
                }
            }
            
            // Set process variables on process instance (before task completion)
            if (variables != null && !variables.isEmpty()) {
                if (processInstanceId != null) {
                    log.debug("Setting {} variable keys on process instance {} before completing task {}",
                        variables.size(), processInstanceId, taskId);
                    runtimeService.setVariables(processInstanceId, variables);
                }
            } else {
                log.debug("No variables provided for task completion. TaskId: {}, UserId: {}", taskId, userId);
            }
            
            // [Multi-Instance Extension] Detect if next node is multi-instance sub-process, if so inject sub-table data
            String processDefinitionId = flowableTask.getProcessDefinitionId();
            String taskDefinitionKey = flowableTask.getTaskDefinitionKey();
            
            detectAndInjectMultiInstanceData(processInstanceId, processDefinitionId, taskDefinitionKey);

            // Persist approver comment via Flowable's native comment system so
            // it shows up in flow history queries (ACT_HI_COMMENT).
            if (variables != null) {
                Object approverComment = variables.get("approverComments");
                if (approverComment != null && !approverComment.toString().isBlank()) {
                    taskService.addComment(taskId, processInstanceId, approverComment.toString());
                }
            }

            // Flowable complete permission defaults to authenticatedUserId (especially for candidate tasks)
            String previousActor = Authentication.getAuthenticatedUserId();
            try {
                Authentication.setAuthenticatedUserId(userId);
                if (variables != null && !variables.isEmpty()) {
                    log.info("Completing task {} with variables: {}", taskId, variables);
                    taskService.complete(taskId, variables);
                } else {
                    log.info("Completing task {} without variables", taskId);
                    taskService.complete(taskId);
                }
            } finally {
                Authentication.setAuthenticatedUserId(previousActor);
            }
            
            // Update extended task info (if exists)
            AssignmentType assignmentType = AssignmentType.USER;
            String currentAssignee = userId;
            
            if (extendedTaskInfoOpt.isPresent()) {
                ExtendedTaskInfo extendedTaskInfo = extendedTaskInfoOpt.get();
                extendedTaskInfo.completeTask(userId);
                extendedTaskInfoRepository.save(extendedTaskInfo);
                assignmentType = extendedTaskInfo.getAssignmentType();
                currentAssignee = extendedTaskInfo.getCurrentAssignee();
                
                // Publish task completion event
                publishTaskCompleteEvent(extendedTaskInfo, userId, variables, sendNotification,
                        taskDisplayName, initiatorUserId, processInstanceId, taskId);
            } else if (sendNotification) {
                publishTaskCompleteEvent(null, userId, variables, true,
                        taskDisplayName, initiatorUserId, processInstanceId, taskId);
            }
            
            return TaskAssignmentResult.success(
                taskId, 
                assignmentType,
                currentAssignee,
                userId,
                "Task completed successfully");
                
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (MultiInstanceDataResolver.OptimisticLockException e) {
            // Throw optimistic lock exception directly, no wrapping
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_COMPLETE_ERROR", 
                "Task completion failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Rollback task to specified historic node
     * Uses Flowable createChangeActivityStateBuilder for task rollback
     */
    @Auditable(
        operationType = AuditOperationType.RETURN_TASK,
        resourceType = AuditResourceType.TASK,
        description = "Return task",
        captureArgs = true,
        captureResult = true
    )
    public TaskAssignmentResult returnTask(String taskId, TaskReturnRequest request) {
        try {
            // Validate request parameters
            validateTaskReturnRequest(request);
            
            // Find current task
            Task currentTask = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
            
            if (currentTask == null) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "Task not found", taskId)));
            }
            
            String processInstanceId = currentTask.getProcessInstanceId();
            String currentActivityId = currentTask.getTaskDefinitionKey();
            String targetActivityId = request.getTargetActivityId();
            
            // Verify target node is a historic node
            List<HistoricActivityInstance> historicActivities = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityId(targetActivityId)
                .finished()
                .orderByHistoricActivityInstanceEndTime()
                .desc()
                .list();
            
            if (historicActivities.isEmpty()) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "targetActivityId", "Target activity is not a valid historic activity", targetActivityId)));
            }
            
            // Check if rollback target is before multi-instance sub-process; if so, cascade cancel multi-instance sub-tasks
            if (isReturnTargetBeforeMultiInstance(processInstanceId, currentActivityId, targetActivityId)) {
                log.info("Rollback target is before multi-instance sub-process, starting cascade cancel: processInstanceId={}, targetActivityId={}", 
                    processInstanceId, targetActivityId);
                multiInstanceCanceller.cancelMultiInstanceTasks(processInstanceId);
            }
            
            // Record typed Flowable comment so portal flow history shows RETURN (not generic APPROVE)
            recordReturnTaskComment(taskId, processInstanceId, currentTask, targetActivityId, request);

            // Signal TaskAssignmentListener to fall back to previous handler if BPMN resolve fails
            runtimeService.setVariable(processInstanceId, RollbackAssigneeFallbackSupport.VAR_FALLBACK_ACTIVE, Boolean.TRUE);
            runtimeService.setVariable(processInstanceId, RollbackAssigneeFallbackSupport.VAR_TARGET_ACTIVITY_ID, targetActivityId);

            // Use Flowable createChangeActivityStateBuilder to perform rollback
            runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(processInstanceId)
                .moveActivityIdTo(currentActivityId, targetActivityId)
                .changeState();
            
            // Find extended task info and update status
            ExtendedTaskInfo extendedTaskInfo = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId)
                .orElse(null);
            
            if (extendedTaskInfo != null) {
                extendedTaskInfo.updateStatus("RETURNED", request.getUserId());
                extendedTaskInfo.setIsDeleted(true);
                extendedTaskInfoRepository.save(extendedTaskInfo);
            }
            
            // Publish task rollback event
            publishTaskReturnEvent(taskId, processInstanceId, currentActivityId, targetActivityId, request);
            
            return TaskAssignmentResult.success(
                taskId,
                AssignmentType.USER,
                targetActivityId,
                request.getUserId(),
                "Task returned successfully, returned to activity: " + targetActivityId);
                
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_RETURN_ERROR", 
                "Task return failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if rollback target is before multi-instance sub-process
     *
     * Simplified implementation: judge by historic execution time
     * - If active multi-instance sub-tasks exist
     * - And target activity's last completion time is earlier than multi-instance sub-task creation time
     * - Then rollback target is considered before multi-instance sub-process
     * 
     * @param processInstanceId Process instance ID
     * @param currentActivityId Current activity ID
     * @param targetActivityId Target activity ID
     * @return true if rollback target is before multi-instance sub-process
     */
    private boolean isReturnTargetBeforeMultiInstance(String processInstanceId, 
                                                      String currentActivityId, 
                                                      String targetActivityId) {
        try {
            // Query all active multi-instance sub-tasks in process instance
            List<ExtendedTaskInfo> activeMultiInstanceTasks = extendedTaskInfoRepository
                .findByProcessInstanceIdAndIsDeletedFalse(processInstanceId)
                .stream()
                .filter(this::isMultiInstanceTask)
                .filter(task -> !"COMPLETED".equals(task.getStatus()) && !"CANCELLED".equals(task.getStatus()))
                .toList();
            
            if (activeMultiInstanceTasks.isEmpty()) {
                log.debug("No active multi-instance sub-tasks in process instance {}", processInstanceId);
                return false;
            }
            
            // Get earliest multi-instance sub-task creation time
            LocalDateTime earliestMultiInstanceTaskTime = activeMultiInstanceTasks.stream()
                .map(ExtendedTaskInfo::getCreatedTime)
                .min(LocalDateTime::compareTo)
                .orElse(null);
            
            if (earliestMultiInstanceTaskTime == null) {
                log.warn("Cannot get creation time of multi-instance sub-task");
                return false;
            }
            
            // Query target activity's last completion time
            List<HistoricActivityInstance> targetActivities = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityId(targetActivityId)
                .finished()
                .orderByHistoricActivityInstanceEndTime()
                .desc()
                .list();
            
            if (targetActivities.isEmpty()) {
                log.warn("No history record found for target activity: {}", targetActivityId);
                return false;
            }
            
            // Get target activity's last completion time
            java.util.Date targetEndDate = targetActivities.get(0).getEndTime();
            if (targetEndDate == null) {
                log.warn("Target activity {} has no completion time", targetActivityId);
                return false;
            }
            
            LocalDateTime targetEndTime = LocalDateTime.ofInstant(
                targetEndDate.toInstant(), 
                java.time.ZoneId.systemDefault()
            );
            
            // If target activity completion time is earlier than multi-instance sub-task creation time, rollback target is before multi-instance
            boolean isBeforeMultiInstance = targetEndTime.isBefore(earliestMultiInstanceTaskTime);
            
            if (isBeforeMultiInstance) {
                log.info("Detected rollback target {} (completed: {}) before multi-instance sub-process (created: {})", 
                    targetActivityId, targetEndTime, earliestMultiInstanceTaskTime);
            }
            
            return isBeforeMultiInstance;
            
        } catch (Exception e) {
            log.error("Exception checking whether rollback target is before multi-instance sub-process: processInstanceId={}", processInstanceId, e);
            // Conservative handling on exception: do not execute cascade cancel
            return false;
        }
    }
    
    /**
     * Check if task is a multi-instance sub-task
     */
    private boolean isMultiInstanceTask(ExtendedTaskInfo task) {
        String extendedProperties = task.getExtendedProperties();
        if (extendedProperties == null || extendedProperties.trim().isEmpty()) {
            return false;
        }
        
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> properties = objectMapper.readValue(
                extendedProperties, 
                new TypeReference<Map<String, Object>>() {}
            );
            
            Object multiInstance = properties.get("multiInstance");
            return multiInstance != null && Boolean.TRUE.equals(multiInstance);
        } catch (Exception e) {
            log.warn("Failed to parse extendedProperties: taskId={}", task.getTaskId(), e);
            return false;
        }
    }
    
    /**
     * Get list of rollback-able historic nodes
     */
    public List<TaskListResult.TaskInfo> getReturnableActivities(String taskId) {
        try {
            // Find current task
            Task currentTask = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
            
            if (currentTask == null) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "Task not found", taskId)));
            }
            
            String processInstanceId = currentTask.getProcessInstanceId();
            
            // Query historic user task nodes
            List<HistoricActivityInstance> historicActivities = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityType("userTask")
                .finished()
                .orderByHistoricActivityInstanceEndTime()
                .desc()
                .list();
            
            // Convert to task info list (deduplicated)
            List<TaskListResult.TaskInfo> returnableActivities = new ArrayList<>();
            java.util.Set<String> seenActivityIds = new java.util.HashSet<>();
            
            for (HistoricActivityInstance activity : historicActivities) {
                if (!seenActivityIds.contains(activity.getActivityId())) {
                    seenActivityIds.add(activity.getActivityId());
                    
                    TaskListResult.TaskInfo taskInfo = TaskListResult.TaskInfo.builder()
                        .taskId(activity.getActivityId())
                        .taskName(activity.getActivityName())
                        .processInstanceId(processInstanceId)
                        .status("COMPLETED")
                        .build();
                    
                    returnableActivities.add(taskInfo);
                }
            }
            
            return returnableActivities;
            
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_QUERY_ERROR", 
                "Failed to query returnable activities: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get task detail
     * Query Flowable TaskService first, fall back to extended table if not found
     */
    public TaskListResult.TaskInfo getTaskInfo(String taskId) {
        try {
            // 1. First try querying task directly from Flowable
            Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
            
            if (task != null) {
                // Build TaskInfo from Flowable task
                return buildTaskInfoFromFlowableTask(task);
            }
            
            // 2. If not found in Flowable, try querying extended table (may be completed task)
            Optional<ExtendedTaskInfo> extendedTaskInfoOpt = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId);
            
            if (extendedTaskInfoOpt.isPresent()) {
                return convertToTaskInfo(extendedTaskInfoOpt.get());
            }

            // 3. Completed Tasks list may link to Flowable historic task IDs that
            // were not mirrored into wf_extended_task_info. Return a read-only
            // TaskInfo shape so portal /tasks/:id can reuse the To Do detail renderer.
            HistoricTaskInstance historicTask = historyService.createHistoricTaskInstanceQuery()
                .taskId(taskId)
                .singleResult();
            if (historicTask != null) {
                return buildTaskInfoFromHistoricTask(historicTask);
            }
            
            // 4. Not found anywhere, throw exception
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "taskId", "Task not found", taskId)));
            
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_QUERY_ERROR", 
                "Failed to query task details: " + e.getMessage(), e);
        }
    }
    
    /**
     * Build TaskInfo from Flowable Task
     */
    private TaskListResult.TaskInfo buildTaskInfoFromFlowableTask(Task task) {
        // Extract processDefinitionKey from processDefinitionId
        // Flowable 7.0 may return UUID only; extractProcessDefinitionKey auto-queries repositoryService
        String processDefinitionId = task.getProcessDefinitionId();
        String processDefinitionKey = extractProcessDefinitionKey(processDefinitionId);
        
        // Get process definition name
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

        // Get process variables (before initiator resolution, so variables.initiator can fallback if startUserId is empty)
        Map<String, Object> variables = null;
        if (task.getProcessInstanceId() != null) {
            try {
                variables = runtimeService.getVariables(task.getProcessInstanceId());
                log.debug("Retrieved {} variables for task {}",
                    variables != null ? variables.size() : 0, task.getId());
            } catch (Exception e) {
                log.warn("Failed to get variables for process instance {}: {}",
                    task.getProcessInstanceId(), e.getMessage());
                variables = new HashMap<>();
            }
        }

        // Multi-instance sub-task: include execution-scoped currentItem so the frontend
        // can determine which sub-table row belongs to this specific sub-task instance.
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

        // Get process initiator info
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
        if (!StringUtils.hasText(initiatorId) && variables != null && variables.get("initiator") != null) {
            String iv = variables.get("initiator").toString().trim();
            if (StringUtils.hasText(iv)) {
                initiatorId = iv;
                initiatorName = resolveUserDisplayName(initiatorId);
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

        // Get current assignee name
        String currentAssignee = task.getAssignee();
        String currentAssigneeName = null;
        if (currentAssignee != null && !currentAssignee.isEmpty()) {
            currentAssigneeName = resolveUserDisplayName(currentAssignee);
        }

        // When BPMN is initiator direct handling but runtime has no assignee, correct API: avoid falsely reporting VIRTUAL_GROUP empty pool
        if (isBpmnProcessInitiatorType(bpmnAssigneeType)
                && StringUtils.hasText(initiatorId)
                && !StringUtils.hasText(currentAssignee)) {
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

    private TaskListResult.TaskInfo buildTaskInfoFromHistoricTask(HistoricTaskInstance task) {
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

    /**
     * When portal passes current workspace BU, filter out tasks before pagination where BPMN FIXED_BU_ROLE fixed BU mismatches JWT workspace.
     * Depends on bpmnAssigneeType / bpmnBusinessUnitId / variables in {@link #buildTaskInfoFromFlowableTask}.
     */
    private List<Task> applyActiveWorkspaceBuTaskFilter(List<Task> tasks, String activeBusinessUnitId, String queryUserId) {
        if (!StringUtils.hasText(activeBusinessUnitId) || tasks == null || tasks.isEmpty()) {
            return tasks;
        }
        String activeBu = activeBusinessUnitId.trim();
        List<Task> out = new ArrayList<>();
        for (Task t : tasks) {
            TaskListResult.TaskInfo info = buildTaskInfoFromFlowableTask(t);
            if (fixedBuRoleVisibleForActiveWorkspace(info, activeBu, t, queryUserId)) {
                out.add(t);
            }
        }
        return out;
    }

    /**
     * Role-pool tasks are workspace-scoped; direct assignee/candidate rows stay visible across workspace mismatch
     * (rollback fallback may assign user while BPMN still carries a fixed BU id).
     */
    private boolean fixedBuRoleVisibleForActiveWorkspace(TaskListResult.TaskInfo info, String activeBu,
                                                       Task flowableTask, String queryUserId) {
        if (flowableTask != null && StringUtils.hasText(queryUserId)) {
            String assignee = flowableTask.getAssignee();
            if (StringUtils.hasText(assignee) && queryUserId.trim().equals(assignee.trim())) {
                return true;
            }
        }
        if (info == null || !StringUtils.hasText(activeBu)) {
            return true;
        }
        if (!isWorkspaceScopedBuPoolSemantics(info)) {
            return true;
        }
        String fixed = resolveFixedBuIdFromTaskInfo(info);
        if (!StringUtils.hasText(fixed)) {
            return true;
        }
        return equalsNormalizedBuId(activeBu, fixed);
    }

    /**
     * FIXED_BU_ROLE, or BPMN BU_ROLE with explicit businessUnitId in extensions (designer fixed BU role pool).
     * <p>Judged solely by current user task BPMN extensions, not process-instance-level {@code assigneeType}/{@code businessUnitId} variables:
     * those leak across nodes, misidentifying downstream nodes as fixed BU pool and filtering them all out in {@link #applyActiveWorkspaceBuTaskFilter}.</p>
     */
    private static boolean isWorkspaceScopedBuPoolSemantics(TaskListResult.TaskInfo info) {
        String bpmn = info.getBpmnAssigneeType();
        if (bpmn != null) {
            String u = bpmn.trim().toUpperCase(Locale.ROOT);
            if ("FIXED_BU_ROLE".equals(u)) {
                return true;
            }
            if ("BU_ROLE".equals(u) && StringUtils.hasText(info.getBpmnBusinessUnitId())) {
                return true;
            }
        }
        return false;
    }

    /** Align JWT string with Long/numeric-string BU id in engine variables */
    private static boolean equalsNormalizedBuId(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String x = a.trim();
        String y = b.trim();
        if (x.equals(y)) {
            return true;
        }
        try {
            if (x.matches("^-?\\d+$") && y.matches("^-?\\d+$")) {
                return Long.parseLong(x) == Long.parseLong(y);
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return false;
    }

    private static String resolveFixedBuIdFromTaskInfo(TaskListResult.TaskInfo info) {
        if (StringUtils.hasText(info.getBpmnBusinessUnitId())) {
            return info.getBpmnBusinessUnitId().trim();
        }
        return null;
    }
    
    
    // ==================== Private Helper Methods ====================

    private static boolean isBpmnProcessInitiatorType(String bpmnAssigneeType) {
        if (!StringUtils.hasText(bpmnAssigneeType)) {
            return false;
        }
        String u = bpmnAssigneeType.trim().toUpperCase(Locale.ROOT);
        return "INITIATOR".equals(u) || "PROCESS_INITIATOR".equals(u);
    }
    
    /**
     * Validate user ID
     */
    private void validateUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "userId", "User ID cannot be empty", userId)));
        }
    }
    
    /**
     * Validate task assignment request
     */
    private void validateTaskAssignmentRequest(TaskAssignmentRequest request) {
        if (request == null) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "request", "Request parameters cannot be empty", null)));
        }
        
        if (!request.isValid()) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "request", request.getValidationError(), null)));
        }
    }
    
    /**
     * Validate task delegation request
     */
    private void validateTaskDelegationRequest(TaskDelegationRequest request) {
        if (request == null) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "request", "Request parameters cannot be empty", null)));
        }
        
        if (!request.isValid()) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "request", request.getValidationError(), null)));
        }
    }
    
    /**
     * Validate task claim request
     */
    private void validateTaskClaimRequest(TaskClaimRequest request) {
        if (request == null) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "request", "Request parameters cannot be empty", null)));
        }
        
        if (!request.isValid()) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "request", request.getValidationError(), null)));
        }
    }
    
    /**
     * Validate task rollback request
     */
    private void validateTaskReturnRequest(TaskReturnRequest request) {
        if (request == null) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "request", "Request parameters cannot be empty", null)));
        }
        
        if (!StringUtils.hasText(request.getTargetActivityId())) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "targetActivityId", "Target activity ID cannot be empty", null)));
        }
        
        if (!StringUtils.hasText(request.getUserId())) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "userId", "User ID cannot be empty", null)));
        }
    }
    
    /**
     * Create extended task info
     */
    private ExtendedTaskInfo createExtendedTaskInfo(Task flowableTask, TaskAssignmentRequest request) {
        return ExtendedTaskInfo.builder()
            .taskId(flowableTask.getId())
            .processInstanceId(flowableTask.getProcessInstanceId())
            .processDefinitionId(flowableTask.getProcessDefinitionId())
            .taskDefinitionKey(flowableTask.getTaskDefinitionKey())
            .taskName(flowableTask.getName())
            .taskDescription(flowableTask.getDescription())
            .assignmentType(request.getAssignmentType())
            .assignmentTarget(request.getAssignmentTarget())
            .priority(request.getEffectivePriority())
            .dueDate(request.getDueDate())
            .formKey(flowableTask.getFormKey())
            .status("ASSIGNED")
            .createdTime(LocalDateTime.now())
            .createdBy(request.getOperatorUserId())
            .tenantId(request.getTenantId())
            .isDeleted(false)
            .version(0L)
            .build();
    }
    
    /**
     * Update task assignment info
     */
    private void updateTaskAssignment(ExtendedTaskInfo extendedTaskInfo, TaskAssignmentRequest request) {
        extendedTaskInfo.setAssignmentType(request.getAssignmentType());
        extendedTaskInfo.setAssignmentTarget(request.getAssignmentTarget());
        extendedTaskInfo.setPriority(request.getEffectivePriority());
        extendedTaskInfo.setDueDate(request.getDueDate());
        extendedTaskInfo.updateStatus("ASSIGNED", request.getOperatorUserId());
        
        // Clear previous delegation and claim info
        extendedTaskInfo.setDelegatedTo(null);
        extendedTaskInfo.setDelegatedBy(null);
        extendedTaskInfo.setDelegatedTime(null);
        extendedTaskInfo.setDelegationReason(null);
        extendedTaskInfo.setClaimedBy(null);
        extendedTaskInfo.setClaimedTime(null);
    }
    
    /**
     * Update Flowable task assignment
     */
    private void updateFlowableTaskAssignment(Task flowableTask, TaskAssignmentRequest request) {
        switch (request.getAssignmentType()) {
            case USER:
                // Directly assign to user
                taskService.setAssignee(flowableTask.getId(), request.getAssignmentTarget());
                break;
            case VIRTUAL_GROUP:
            case CANDIDATE_USERS:
                // Assign to virtual group or candidate pool, clear personal assignment
                taskService.setAssignee(flowableTask.getId(), null);
                break;
        }
        
        // Set priority and due date
        if (request.getPriority() != null) {
            taskService.setPriority(flowableTask.getId(), request.getPriority());
        }
        if (request.getDueDate() != null) {
            taskService.setDueDate(flowableTask.getId(), 
                java.sql.Timestamp.valueOf(request.getDueDate()));
        }
    }
    /**
     * Verify delegation permission
     */
    private void validateDelegationPermission(ExtendedTaskInfo task, String delegatedBy) {
        // Verify delegator has permission to delegate this task
        boolean hasPermission = userPermissionService.hasTaskPermission(
                delegatedBy, 
                task.getAssignmentType(), 
                task.getAssignmentTarget());
        
        if (!hasPermission) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "delegatedBy", "User does not have permission to delegate this task", delegatedBy)));
        }
    }
    
    /**
     * Verify claim permission
     */
    private void validateClaimPermission(ExtendedTaskInfo task, String claimedBy) {
        // Only virtual group and department role tasks can be claimed
        if (task.getAssignmentType() == AssignmentType.USER) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "taskId", "Directly assigned tasks cannot be claimed", task.getTaskId())));
        }
        
        // Verify user has permission to claim this task
        boolean hasPermission = userPermissionService.hasTaskPermission(
                claimedBy, 
                task.getAssignmentType(), 
                task.getAssignmentTarget());
        
        if (!hasPermission) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "claimedBy", "User does not have permission to claim this task", claimedBy)));
        }
    }
    
    /**
     * Engine-side assignee / claimedBy may be username from historic data, while portal JWT subject is user primary key UUID.
     */
    private boolean engineActorMatchesPortalUser(String engineSideActor, String portalUserId) {
        if (!StringUtils.hasText(engineSideActor) || !StringUtils.hasText(portalUserId)) {
            return false;
        }
        String a = engineSideActor.trim();
        String p = portalUserId.trim();
        if (a.equals(p)) {
            return true;
        }
        try {
            Map<String, Object> info = adminCenterClient.getUserInfo(p);
            if (info != null) {
                Object id = info.get("id");
                if (id != null && a.equals(id.toString().trim())) {
                    return true;
                }
                Object username = info.get("username");
                if (username != null && a.equals(username.toString().trim())) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("engineActorMatchesPortalUser: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Resolve actor (username or already UUID) to admin-center user primary key, for use in process variables.
     */
    private String normalizePortalUserIdForVariable(String actor) {
        if (!StringUtils.hasText(actor)) {
            return actor;
        }
        try {
            Map<String, Object> info = adminCenterClient.getUserInfo(actor.trim());
            if (info != null && info.get("id") != null) {
                return info.get("id").toString().trim();
            }
        } catch (Exception e) {
            log.debug("normalizePortalUserIdForVariable: {}", e.getMessage());
        }
        return actor.trim();
    }

    /**
     * Judge whether current user can complete by Flowable runtime assignee / candidates (users and candidate groups), avoiding sole reliance on potentially stale ExtendedTaskInfo.
     */
    private boolean flowableRuntimeAuthorizesComplete(Task task, String portalUserId) {
        if (task == null || !StringUtils.hasText(portalUserId)) {
            return false;
        }
        String uid = portalUserId.trim();
        String assignee = task.getAssignee();
        if (StringUtils.hasText(assignee) && engineActorMatchesPortalUser(assignee, uid)) {
            return true;
        }
        for (IdentityLink link : taskService.getIdentityLinksForTask(task.getId())) {
            if (!"candidate".equals(link.getType())) {
                continue;
            }
            if (link.getUserId() != null && StringUtils.hasText(link.getUserId())
                    && engineActorMatchesPortalUser(link.getUserId(), uid)) {
                return true;
            }
            if (link.getGroupId() != null && StringUtils.hasText(link.getGroupId())
                    && userPermissionService.isUserInVirtualGroup(uid, link.getGroupId().trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * When BPMN assigneeType is initiator node and current user is initiator, claim/setAssignee when only candidates exist without assignee, consistent with normalized semantics in {@link #buildTaskInfoFromFlowableTask}.
     */
    private void ensureProcessInitiatorAssigneeFromBpmnIfNeeded(Task task, String portalUserId) {
        if (task == null || !StringUtils.hasText(portalUserId)) {
            return;
        }
        if (StringUtils.hasText(task.getAssignee())) {
            return;
        }
        String pdId = task.getProcessDefinitionId();
        String defKey = task.getTaskDefinitionKey();
        if (!StringUtils.hasText(pdId) || !StringUtils.hasText(defKey)) {
            return;
        }
        String bpmnAt = null;
        try {
            bpmnAt = bpmnActionParser.getUserTaskExtensionPropertyValue(pdId, defKey, "assigneeType");
        } catch (Exception e) {
            log.debug("ensureProcessInitiatorAssignee: read assigneeType: {}", e.getMessage());
        }
        if (!isBpmnProcessInitiatorType(bpmnAt)) {
            return;
        }
        String piid = task.getProcessInstanceId();
        if (!StringUtils.hasText(piid)) {
            return;
        }
        String initiatorId = resolveInitiatorUserId(piid);
        if (!StringUtils.hasText(initiatorId) || !engineActorMatchesPortalUser(initiatorId, portalUserId.trim())) {
            return;
        }
        try {
            taskService.claim(task.getId(), portalUserId.trim());
            log.info("Claimed BPMN initiator task {} for user {} before complete", task.getId(), portalUserId);
        } catch (Exception e) {
            log.debug("Claim initiator task {} failed ({}), trying setAssignee", task.getId(), e.getMessage());
            try {
                taskService.setAssignee(task.getId(), portalUserId.trim());
                log.info("Set assignee on BPMN initiator task {} for user {} before complete", task.getId(), portalUserId);
            } catch (Exception e2) {
                log.warn("Could not claim/setAssignee initiator task {} for user {}: {}",
                        task.getId(), portalUserId, e2.getMessage());
            }
        }
    }

    /**
     * Orphan user tasks without assignee or candidate chain: write assignee before initiator completes, otherwise Flowable typically cannot complete.
     */
    private void ensureAssigneeForOrphanInitiatorTaskIfNeeded(Task task, String portalUserId) {
        if (task == null || !StringUtils.hasText(portalUserId)) {
            return;
        }
        if (StringUtils.hasText(task.getAssignee())) {
            return;
        }
        long candidateLinks = taskService.getIdentityLinksForTask(task.getId()).stream()
                .filter(l -> "candidate".equals(l.getType()))
                .count();
        if (candidateLinks > 0) {
            return;
        }
        String piid = task.getProcessInstanceId();
        if (!StringUtils.hasText(piid)) {
            return;
        }
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(piid)
                .singleResult();
        if (pi == null) {
            return;
        }
        String startUser = pi.getStartUserId();
        if (!StringUtils.hasText(startUser) || !engineActorMatchesPortalUser(startUser, portalUserId.trim())) {
            return;
        }
        log.warn("Task {} has no assignee and no candidate links; setting assignee to portal user {} (process initiator) before complete",
                task.getId(), portalUserId.trim());
        taskService.setAssignee(task.getId(), portalUserId.trim());
    }

    /**
     * Verify completion permission
     */
    private void validateCompletePermission(ExtendedTaskInfo task, String userId) {
        String currentAssignee = task.getCurrentAssignee();
        
        // If task has explicit current handler (delegate or claimer), only that user can complete
        if (currentAssignee != null && !currentAssignee.isBlank()) {
            if (!engineActorMatchesPortalUser(currentAssignee, userId)) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "userId", "User does not have permission to complete this task", userId)));
            }
            return;
        }
        
        // If no explicit current handler, verify permission by assignment type
        boolean hasPermission = userPermissionService.hasTaskPermission(
                userId, 
                task.getAssignmentType(), 
                task.getAssignmentTarget());
        
        if (!hasPermission) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "userId", "User does not have permission to complete this task", userId)));
        }
    }
    
    /**
     * Convert to task info DTO
     */
    private TaskListResult.TaskInfo convertToTaskInfo(ExtendedTaskInfo extendedTaskInfo) {
        // Get process definition name
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
    // ==================== Event Publishing Methods (Kafka notifications → user-portal) ====================
    
    private static String taskLink(String taskId) {
        return "/tasks/" + taskId;
    }

    private String resolveInitiatorUserId(String processInstanceId) {
        if (processInstanceId == null) {
            return null;
        }
        try {
            Object v = runtimeService.getVariable(processInstanceId, "initiator");
            if (v != null && StringUtils.hasText(v.toString())) {
                return v.toString().trim();
            }
        } catch (Exception e) {
            log.debug("Could not read initiator variable for process {}: {}", processInstanceId, e.getMessage());
        }
        try {
            ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            if (pi != null && StringUtils.hasText(pi.getStartUserId())) {
                return pi.getStartUserId().trim();
            }
        } catch (Exception e) {
            log.debug("Could not read startUserId for process {}: {}", processInstanceId, e.getMessage());
        }
        return null;
    }

    private void publishTaskAssignmentEvent(ExtendedTaskInfo task, TaskAssignmentRequest request) {
        log.info("Task assignment event: taskId={}, assignmentTarget={}, assignmentType={}",
                task.getTaskId(), request.getAssignmentTarget(), request.getAssignmentType());
        if (!request.shouldSendNotification()) {
            return;
        }
        if (request.getAssignmentType() != AssignmentType.USER) {
            return;
        }
        String targetUser = request.getAssignmentTarget();
        if (!StringUtils.hasText(targetUser)) {
            return;
        }
        String label = task.getTaskName() != null ? task.getTaskName() : task.getTaskId();
        notificationDispatchHelper.publishToUserAfterCommit(
                targetUser.trim(),
                "TASK",
                i18nService.getMessage("workflow.notification.assigned_title"),
                i18nService.getMessage("workflow.notification.assigned_body", label, request.getOperatorUserId()),
                taskLink(task.getTaskId()),
                "workflow-engine");
    }
    
    private void publishTaskDelegationEvent(ExtendedTaskInfo task, TaskDelegationRequest request) {
        log.info("Task delegation event: taskId={}, delegatedTo={}, delegatedBy={}",
                task.getTaskId(), request.getDelegatedTo(), request.getDelegatedBy());
        if (!request.shouldSendNotification()) {
            return;
        }
        String label = task.getTaskName() != null ? task.getTaskName() : task.getTaskId();
        notificationDispatchHelper.publishToUserAfterCommit(
                request.getDelegatedTo(),
                "TASK",
                i18nService.getMessage("workflow.notification.delegated_title"),
                i18nService.getMessage("workflow.notification.delegated_body",
                        request.getDelegatedBy(),
                        label,
                        request.getEffectiveDelegationReason() != null
                                ? " " + i18nService.getMessage("workflow.notification.delegation_reason", request.getEffectiveDelegationReason())
                                : "").trim(),
                taskLink(task.getTaskId()),
                "workflow-engine");
    }
    
    private void publishTaskClaimEvent(ExtendedTaskInfo task, TaskClaimRequest request) {
        log.info("Task claim event: taskId={}, claimedBy={}",
                task.getTaskId(), request.getClaimedBy());
        // Claimer is the operator; skip self-notification to avoid noise
    }
    
    private void publishTaskCompleteEvent(ExtendedTaskInfo task, String userId,
                                        java.util.Map<String, Object> variables,
                                        boolean sendNotification,
                                        String taskDisplayName,
                                        String initiatorUserId,
                                        String processInstanceId,
                                        String flowableTaskId) {
        String tid = task != null ? task.getTaskId() : flowableTaskId;
        log.info("Task completion event: taskId={}, completedBy={}", tid, userId);
        if (!sendNotification || !StringUtils.hasText(initiatorUserId) || initiatorUserId.equals(userId)) {
            return;
        }
        String label = taskDisplayName != null ? taskDisplayName : tid;
        String link = StringUtils.hasText(tid) ? taskLink(tid) : "/tasks";
        notificationDispatchHelper.publishToUserAfterCommit(
                initiatorUserId,
                "TASK",
                i18nService.getMessage("workflow.notification.completed_title"),
                i18nService.getMessage("workflow.notification.completed_body", userId, label),
                link,
                "workflow-engine");
    }
    
    private void recordReturnTaskComment(String taskId, String processInstanceId, Task currentTask,
                                       String targetActivityId, TaskReturnRequest request) {
        String targetLabel = resolveActivityDisplayName(currentTask.getProcessDefinitionId(), targetActivityId);
        StringBuilder msg = new StringBuilder();
        msg.append("Returned to ").append(targetLabel);
        if (request.getReason() != null && !request.getReason().isBlank()) {
            msg.append(": ").append(request.getReason().trim());
        }
        String previousActor = Authentication.getAuthenticatedUserId();
        try {
            Authentication.setAuthenticatedUserId(request.getUserId());
            taskService.addComment(taskId, processInstanceId, "return", msg.toString());
        } catch (Exception e) {
            log.warn("Failed to record return comment on task {}: {}", taskId, e.getMessage());
        } finally {
            Authentication.setAuthenticatedUserId(previousActor);
        }
    }

    private String resolveActivityDisplayName(String processDefinitionId, String activityId) {
        if (!StringUtils.hasText(activityId)) {
            return "previous step";
        }
        try {
            if (StringUtils.hasText(processDefinitionId)) {
                BpmnModel model = repositoryService.getBpmnModel(processDefinitionId);
                if (model != null) {
                    FlowElement el = model.getFlowElement(activityId.trim());
                    if (el != null && StringUtils.hasText(el.getName())) {
                        return el.getName().trim();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not resolve activity display name for {}: {}", activityId, e.getMessage());
        }
        return activityId.trim();
    }

    private void publishTaskReturnEvent(String taskId, String processInstanceId,
                                        String fromActivityId, String toActivityId,
                                        TaskReturnRequest request) {
        log.info("Task return event: taskId={}, from={}, to={}, userId={}, reason={}",
                taskId, fromActivityId, toActivityId, request.getUserId(), request.getReason());
        if (!request.shouldSendNotification()) {
            return;
        }
        String initiator = resolveInitiatorUserId(processInstanceId);
        if (!StringUtils.hasText(initiator)) {
            return;
        }
        notificationDispatchHelper.publishToUserAfterCommit(
                initiator,
                "PROCESS",
                i18nService.getMessage("workflow.notification.rollback_title"),
                i18nService.getMessage("workflow.notification.rollback_body",
                        processInstanceId,
                        request.getUserId(),
                        fromActivityId,
                        toActivityId,
                        request.getReason() != null ? " " + i18nService.getMessage("workflow.notification.rollback_reason", request.getReason()) : "").trim(),
                "/tasks",
                "workflow-engine");
    }
    
    // ==================== Statistical Query Methods ====================
    
    /**
     * Count user tasks
     */
    public long countUserTasks(String userId) {
        try {
            validateUserId(userId);
            return extendedTaskInfoRepository.countUserTodoTasks(userId);
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_COUNT_ERROR", 
                "Failed to count user tasks: " + e.getMessage(), e);
        }
    }
    
    /**
     * Count user overdue tasks
     */
    public long countUserOverdueTasks(String userId) {
        try {
            validateUserId(userId);
            return extendedTaskInfoRepository.countUserOverdueTasks(userId, LocalDateTime.now());
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_COUNT_ERROR", 
                "Failed to count user overdue tasks: " + e.getMessage(), e);
        }
    }
    
    /**
     * Query overdue tasks
     */
    public List<TaskListResult.TaskInfo> getOverdueTasks() {
        try {
            List<ExtendedTaskInfo> overdueTasks = extendedTaskInfoRepository
                .findOverdueTasks(LocalDateTime.now());
            
            return overdueTasks.stream()
                .map(this::convertToTaskInfo)
                .toList();
                
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_QUERY_ERROR", 
                "Failed to query overdue tasks: " + e.getMessage(), e);
        }
    }
    
    /**
     * Query high priority tasks
     */
    public List<TaskListResult.TaskInfo> getHighPriorityTasks(int minPriority) {
        try {
            List<ExtendedTaskInfo> highPriorityTasks = extendedTaskInfoRepository
                .findHighPriorityTasks(minPriority);
            
            return highPriorityTasks.stream()
                .map(this::convertToTaskInfo)
                .toList();
                
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_QUERY_ERROR", 
                "Failed to query high priority tasks: " + e.getMessage(), e);
        }
    }
    
    // ==================== Multi-Instance Sub-Process Support Methods ====================
    
    /**
     * Detect if current task is a multi-instance sub-task
     * Judge by multiInstance flag in extendedProperties
     */
    private boolean isMultiInstanceSubTask(ExtendedTaskInfo extendedTaskInfo) {
        String extendedProperties = extendedTaskInfo.getExtendedProperties();
        if (extendedProperties == null || extendedProperties.trim().isEmpty()) {
            return false;
        }
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> props = objectMapper.readValue(extendedProperties, Map.class);
            Object multiInstance = props.get("multiInstance");
            return multiInstance != null && Boolean.TRUE.equals(multiInstance);
        } catch (Exception e) {
            log.warn("Failed to parse extendedProperties: taskId={}", extendedTaskInfo.getTaskId(), e);
            return false;
        }
    }
    
    /**
     * Handle data write-back when multi-instance sub-task completes
     * Call MultiInstanceDataResolver to write back form data to sub-table
     *
     * Supports two variables structures:
     * 1. Nested mode — variables contain "formData" / "rowVersion" keys
     * 2. Flat mode — portal expands form fields as variables top-level keys (actual runtime path)
     */
    @SuppressWarnings("unchecked")
    private void handleMultiInstanceSubTaskCompletion(String taskId, Map<String, Object> variables, 
                                                      ExtendedTaskInfo extendedTaskInfo) {
        try {
            if (variables == null || variables.isEmpty()) {
                log.warn("Multi-instance sub-task completed but no form data provided: taskId={}", taskId);
                return;
            }
            
            Object formDataObj = variables.get("formData");
            Object rowVersionObj = variables.get("rowVersion");
            
            Map<String, Object> formData;
            Long rowVersion;
            
            if (formDataObj instanceof Map<?, ?>) {
                formData = (Map<String, Object>) formDataObj;
                rowVersion = rowVersionObj instanceof Number n ? n.longValue() : 1L;
            } else {
                // Portal merges request.getFormData() into variables as top-level keys.
                // Resolve sub-table metadata to determine valid column names.
                Map<String, Object> extProps = parseExtendedProps(extendedTaskInfo);
                String subTableName = extProps.get("subTableName") != null
                        ? String.valueOf(extProps.get("subTableName")) : null;
                Map<String, Object> rowKey = multiInstanceDataResolver.tryResolveSubTableRowKey(subTableName, extProps);

                if (rowKey == null || subTableName == null) {
                    log.warn("Multi-instance sub-task missing subTableRowKey/subTableName, skipping write-back: taskId={}", taskId);
                    return;
                }

                if (!multiInstanceDataResolver.subTableExists(subTableName)
                        && variables.containsKey("__subTables__")) {
                    log.info("Multi-instance sub-task uses variable-type sub-table, skipping physical table write-back: taskId={}, subTableName={}, rowKey={}",
                            taskId, subTableName, rowKey);
                    return;
                }

                Map<String, Object> currentRow = multiInstanceDataResolver.loadSubTableRow(subTableName, rowKey);
                rowVersion = currentRow.get("row_version") instanceof Number n ? n.longValue() : 0L;
                
                formData = new HashMap<>();
                Set<String> physicalCols = currentRow.keySet();
                for (Map.Entry<String, Object> e : variables.entrySet()) {
                    String k = e.getKey();
                    if (k == null || multiInstanceDataResolver.isSystemVariable(k)) {
                        continue;
                    }
                    if (k.startsWith("multiInstance_")
                            || "__subTables__".equals(k)
                            || "formData".equals(k)
                            || "rowVersion".equals(k)
                            || "subTableName".equals(k)
                            || "foreignKey".equals(k)
                            || "assigneeField".equals(k)
                            || "mainRecordId".equals(k)
                            || "currentItem".equals(k)
                            || "_currentItem".equals(k)) {
                        continue;
                    }
                    String col = multiInstanceDataResolver.resolveSubTablePhysicalColumnKey(subTableName, k, physicalCols);
                    if (col != null) {
                        formData.put(col, e.getValue());
                    }
                }
                log.info("Extracting sub-table column data from variables top-level keys: taskId={}, columns={}, rowVersion={}",
                        taskId, formData.keySet(), rowVersion);
            }
            
            log.info("Calling MultiInstanceDataResolver to write back data: taskId={}, rowVersion={}", 
                taskId, rowVersion);
            
            multiInstanceDataResolver.writeBackSubTableRow(taskId, formData, rowVersion);
            
            log.info("Multi-instance sub-task data write-back succeeded: taskId={}", taskId);
            
            // Publish WebSocket update notification
            publishMultiInstanceWebSocketUpdate(taskId, extendedTaskInfo);
            
        } catch (MultiInstanceDataResolver.OptimisticLockException e) {
            log.error("Multi-instance sub-task data write-back failed (optimistic lock conflict): taskId={}", taskId, e);
            throw e;
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Multi-instance sub-task data write-back failed: taskId={}", taskId, e);
            throw new WorkflowBusinessException("MULTI_INSTANCE_DATA_WRITEBACK_ERROR", 
                "Multi-instance sub-task data write-back failed: " + e.getMessage(), e);
        }
    }
    
    private Map<String, Object> parseExtendedProps(ExtendedTaskInfo extendedTaskInfo) {
        String json = extendedTaskInfo.getExtendedProperties();
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return new ObjectMapper().readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse extendedProperties: taskId={}", extendedTaskInfo.getTaskId(), e);
            return Collections.emptyMap();
        }
    }
    
    private static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(value).trim()); }
        catch (NumberFormatException e) { return null; }
    }
    
    /**
     * Publish multi-instance sub-task WebSocket update notification
     */
    private void publishMultiInstanceWebSocketUpdate(String taskId, ExtendedTaskInfo extendedTaskInfo) {
        if (updatePublisher == null) {
            return;
        }
        
        try {
            // Extract rowId and main task ID from extendedProperties
            String extendedProperties = extendedTaskInfo.getExtendedProperties();
            if (extendedProperties == null || extendedProperties.trim().isEmpty()) {
                return;
            }
            
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> props = objectMapper.readValue(extendedProperties, Map.class);
            
            Map<String, Object> rowKey = multiInstanceDataResolver.tryResolveSubTableRowKey(
                    props.get("subTableName") != null ? String.valueOf(props.get("subTableName")).trim() : null,
                    props);
            Long rowId = toLong(props.get("subTableRowId"));
            if (rowKey == null && rowId == null) {
                log.warn("Cannot parse sub-table row key from extendedProperties: taskId={}", taskId);
                return;
            }

            // Get main task ID (find predecessor task from process instance)
            String processInstanceId = extendedTaskInfo.getProcessInstanceId();
            String mainTaskId = findMainTaskIdForMultiInstance(processInstanceId);

            if (mainTaskId != null) {
                updatePublisher.publishUpdate(mainTaskId, rowId, rowKey, null, "COMPLETED");
                log.debug("WebSocket update notification published: mainTaskId={}, rowId={}, rowKey={}", mainTaskId, rowId, rowKey);
            }

        } catch (Exception e) {
            // WebSocket publish failure should not affect main flow
            log.warn("Failed to publish WebSocket update notification: taskId={}", taskId, e);
        }
    }

    /**
     * Find main task ID for multi-instance sub-process
     * Find task before multi-instance sub-process by querying process instance historic tasks
     */
    private String findMainTaskIdForMultiInstance(String processInstanceId) {
        try {
            // Query all historic tasks of process instance, ordered by creation time descending
            List<HistoricActivityInstance> activities = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityType("userTask")
                .orderByHistoricActivityInstanceStartTime()
                .desc()
                .list();

            // Find first non-multi-instance sub-task (i.e. main task)
            for (HistoricActivityInstance activity : activities) {
                String taskId = activity.getTaskId();
                if (taskId != null) {
                    Optional<ExtendedTaskInfo> extInfoOpt = extendedTaskInfoRepository
                        .findByTaskIdAndIsDeletedFalse(taskId);

                    if (extInfoOpt.isPresent()) {
                        ExtendedTaskInfo extInfo = extInfoOpt.get();
                        if (!isMultiInstanceSubTask(extInfo)) {
                            return taskId;
                        }
                    }
                }
            }

            return null;
        } catch (Exception e) {
            log.warn("Failed to find main task ID: processInstanceId={}", processInstanceId, e);
            return null;
        }
    }

    /**
     * Detect if next node is multi-instance sub-process, if so inject sub-table data
     *
     * Implementation logic:
     * 1. Get BPMN model of current task
     * 2. Find outgoing flows of current task
     * 3. Iterate target nodes of outgoing flows, detect if multi-instance sub-process
     * 4. If multi-instance sub-process, extract sub-table config from extension attributes
     * 5. Call SubTableDataInjector to inject sub-table data
     */
    private void detectAndInjectMultiInstanceData(String processInstanceId, 
                                                  String processDefinitionId, 
                                                  String taskDefinitionKey) {
        try {
            log.debug("Detecting if next node is multi-instance sub-process: processInstanceId={}, taskDefinitionKey={}", 
                processInstanceId, taskDefinitionKey);
            
            // 1. Get BPMN model
            org.flowable.bpmn.model.BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
            if (bpmnModel == null) {
                log.warn("Cannot get BPMN model: processDefinitionId={}", processDefinitionId);
                return;
            }
            
            // 2. Get current task node
            org.flowable.bpmn.model.FlowElement currentElement = bpmnModel.getFlowElement(taskDefinitionKey);
            if (currentElement == null) {
                log.warn("Cannot find current task node: taskDefinitionKey={}", taskDefinitionKey);
                return;
            }
            
            if (!(currentElement instanceof org.flowable.bpmn.model.UserTask)) {
                log.debug("Current node is not a UserTask: taskDefinitionKey={}", taskDefinitionKey);
                return;
            }
            
            org.flowable.bpmn.model.UserTask userTask = (org.flowable.bpmn.model.UserTask) currentElement;
            
            // 3. Get outgoing flows
            List<org.flowable.bpmn.model.SequenceFlow> outgoingFlows = userTask.getOutgoingFlows();
            if (outgoingFlows == null || outgoingFlows.isEmpty()) {
                log.debug("Current task has no outgoing flows: taskDefinitionKey={}", taskDefinitionKey);
                return;
            }
            
            // 4. Iterate outgoing flows, detect if target node is multi-instance sub-process
            for (org.flowable.bpmn.model.SequenceFlow flow : outgoingFlows) {
                String targetRef = flow.getTargetRef();
                org.flowable.bpmn.model.FlowElement targetElement = bpmnModel.getFlowElement(targetRef);
                
                if (targetElement instanceof org.flowable.bpmn.model.SubProcess) {
                    org.flowable.bpmn.model.SubProcess subProcess = 
                        (org.flowable.bpmn.model.SubProcess) targetElement;
                    
                    // Detect if multi-instance sub-process
                    org.flowable.bpmn.model.MultiInstanceLoopCharacteristics loopCharacteristics = 
                        subProcess.getLoopCharacteristics();
                    
                    if (loopCharacteristics != null) {
                        log.info("Detected multi-instance sub-process: subProcessId={}, processInstanceId={}", 
                            subProcess.getId(), processInstanceId);
                        
                        // 5. Extract multi-instance config and inject data
                        injectMultiInstanceSubTableData(processDefinitionId, processInstanceId, subProcess,
                                loopCharacteristics);
                        
                        // Only process first multi-instance sub-process
                        return;
                    }
                }
            }
            
            log.debug("Next node is not multi-instance sub-process: taskDefinitionKey={}", taskDefinitionKey);
            
        } catch (Exception e) {
            log.error("Failed to detect multi-instance sub-process: processInstanceId={}, taskDefinitionKey={}", 
                processInstanceId, taskDefinitionKey, e);
            // Do not throw exception, avoid affecting task completion flow
        }
    }

    /**
     * Resolve the process variable name for multi-instance input collection.
     * <p>Designer / Flowable export commonly uses {@code <multiInstanceLoopCharacteristics flowable:collection="..." />},
     * Flowable in-memory model stores it as {@link org.flowable.bpmn.model.MultiInstanceLoopCharacteristics#getInputDataItem()};
     * {@link com.developer.util.BpmnXmlGenerator} alternatively places {@code flowable:collection} child element under extensionElements,
     * both must be supported (kk and similar processes use the former).</p>
     */
    private String resolveMultiInstanceCollectionVariableName(
            org.flowable.bpmn.model.MultiInstanceLoopCharacteristics loopCharacteristics) {
        if (loopCharacteristics == null) {
            return null;
        }
        String fromInput = trimToNull(loopCharacteristics.getInputDataItem());
        if (fromInput != null) {
            return fromInput;
        }
        Map<String, List<org.flowable.bpmn.model.ExtensionElement>> extensionElements =
                loopCharacteristics.getExtensionElements();
        if (extensionElements != null) {
            List<org.flowable.bpmn.model.ExtensionElement> collectionElements =
                    extensionElements.get("collection");
            if (collectionElements != null && !collectionElements.isEmpty()) {
                String text = trimToNull(collectionElements.get(0).getElementText());
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    /**
     * Inject multi-instance sub-table data
     * Extract sub-table config from extension attributes of UserTask inside sub-process, call SubTableDataInjector to inject data.
     * <p>Flowable in-memory {@link org.flowable.bpmn.model.BpmnModel} often lacks designer {@code custom:*} extensions,
     * consistent with {@link SubTableAssignmentHandler}, {@link TaskAssignmentListener}: when missing, re-read from deployed BPMN XML.</p>
     */
    private void injectMultiInstanceSubTableData(String processDefinitionId,
                                                 String processInstanceId,
                                                 org.flowable.bpmn.model.SubProcess subProcess,
                                                 org.flowable.bpmn.model.MultiInstanceLoopCharacteristics loopCharacteristics) {
        try {
            String collectionVariableName = resolveMultiInstanceCollectionVariableName(loopCharacteristics);

            if (!StringUtils.hasText(collectionVariableName)) {
                log.warn("Multi-instance sub-process missing collection config (no flowable:collection / inputDataItem, and no extensionElements.collection): subProcessId={}",
                        subProcess.getId());
                return;
            }

            log.info("Multi-instance sub-process collection variable name: {}", collectionVariableName.trim());

            // Extract sub-table config from UserTask inside sub-process
            List<org.flowable.bpmn.model.FlowElement> flowElements =
                    (List<org.flowable.bpmn.model.FlowElement>) subProcess.getFlowElements();

            for (org.flowable.bpmn.model.FlowElement element : flowElements) {
                if (element instanceof org.flowable.bpmn.model.UserTask userTask) {
                    Map<String, Object> modelProps = extractSubTableConfig(userTask);
                    MiSubTableExtensionConfig cfg = resolveMiSubTableExtensionConfig(
                            userTask, processDefinitionId, modelProps);

                    if (StringUtils.hasText(cfg.subTableName()) && StringUtils.hasText(cfg.assigneeField())) {
                        String subTableName = cfg.subTableName().trim();
                        String assigneeField = cfg.assigneeField().trim();
                        String foreignKeyField = StringUtils.hasText(cfg.foreignKey())
                                ? cfg.foreignKey().trim()
                                : "main_record_id";

                        Long mainRecordId = parseLongFlexible(modelProps != null ? modelProps.get("mainRecordId") : null);
                        if (mainRecordId == null) {
                            mainRecordId = getMainRecordIdFromProcessVariables(processInstanceId);
                        }

                        String collectionVarTrimmed = collectionVariableName.trim();

                        // ① Portal already wrote multi-instance collection from __subTables__ (JSON storage, no physical sub-table)
                        try {
                            Object existingCollection = runtimeService.getVariable(processInstanceId, collectionVarTrimmed);
                            if (existingCollection instanceof java.util.Collection<?> ec && !ec.isEmpty()) {
                                log.info("Multi-instance collection '{}' already has {} elements, skipping SubTableDataInjector (JSON / user-portal path)",
                                        collectionVarTrimmed, ec.size());
                                return;
                            }
                        } catch (Exception e) {
                            log.debug("Failed to read multi-instance collection variable {}: {}", collectionVarTrimmed, e.getMessage());
                        }

                        // ② When no physical sub-table, skip JDBC SELECT to avoid relation does not exist
                        if (!subTableDataInjector.physicalTableExistsInCurrentSchema(subTableName)) {
                                log.warn(
                                    "Schema has no physical table '{}' and multi-instance collection '{}' is empty or not set; skip JDBC injection."
                                            + " For pure JSON sub-tables, write the collection variable before completing the predecessor task in portal (see TaskProcessComponent.injectMiCollectionFromBpmn).",
                                    subTableName, collectionVarTrimmed);
                            return;
                        }

                        log.info("Preparing to inject sub-table data: subTableName={}, assigneeField={}, collectionVar={}",
                                subTableName, assigneeField, collectionVarTrimmed);

                        subTableDataInjector.injectSubTableData(
                                processInstanceId,
                                subTableName,
                                foreignKeyField,
                                mainRecordId,
                                assigneeField,
                                collectionVarTrimmed
                        );

                        log.info("Sub-table data injection succeeded: processInstanceId={}, subTableName={}",
                                processInstanceId, subTableName);

                        // Only process first UserTask with complete config
                        return;
                    }
                }
            }

            log.warn("No sub-table config found in multi-instance sub-process: subProcessId={}", subProcess.getId());

        } catch (Exception e) {
            log.error("Failed to inject multi-instance sub-table data: processInstanceId={}, subProcessId={}",
                    processInstanceId, subProcess.getId(), e);
            throw new WorkflowBusinessException("MULTI_INSTANCE_DATA_INJECTION_ERROR",
                    "Failed to inject multi-instance sub-table data: " + e.getMessage(), e);
        }
    }

    /**
     * Sub-table fields declared on UserTask inside multi-instance (in-memory model + deployed XML fallback).
     */
    private record MiSubTableExtensionConfig(String subTableName, String assigneeField, String foreignKey) {
    }

    /**
     * Merge extension attributes from Flowable in-memory model with results parsed from BPMN XML by {@link BpmnActionParser}.
     */
    private MiSubTableExtensionConfig resolveMiSubTableExtensionConfig(
            org.flowable.bpmn.model.UserTask userTask,
            String processDefinitionId,
            Map<String, Object> modelProps) {
        Map<String, Object> fromModel = modelProps != null ? modelProps : Collections.emptyMap();

        String subTableName = mapStringValue(fromModel, "subTableName");
        String assigneeField = mapStringValue(fromModel, "assigneeField");
        String foreignKey = mapStringValue(fromModel, "foreignKey");
        if (!StringUtils.hasText(foreignKey)) {
            foreignKey = mapStringValue(fromModel, "foreignKeyField");
        }

        String utId = userTask.getId();
        if (bpmnActionParser != null
                && StringUtils.hasText(processDefinitionId)
                && StringUtils.hasText(utId)) {
            if (!StringUtils.hasText(subTableName)) {
                subTableName = trimToNull(
                        bpmnActionParser.getUserTaskExtensionPropertyValue(
                                processDefinitionId, utId, "subTableName"));
            }
            if (!StringUtils.hasText(assigneeField)) {
                assigneeField = trimToNull(
                        bpmnActionParser.getUserTaskExtensionPropertyValue(
                                processDefinitionId, utId, "assigneeField"));
            }
            if (!StringUtils.hasText(foreignKey)) {
                foreignKey = trimToNull(
                        bpmnActionParser.getUserTaskExtensionPropertyValue(
                                processDefinitionId, utId, "foreignKey"));
            }
            if (!StringUtils.hasText(foreignKey)) {
                foreignKey = trimToNull(
                        bpmnActionParser.getUserTaskExtensionPropertyValue(
                                processDefinitionId, utId, "foreignKeyField"));
            }
        }

        return new MiSubTableExtensionConfig(subTableName, assigneeField, foreignKey);
    }

    private static String mapStringValue(Map<String, Object> map, String key) {
        if (map == null || key == null || !map.containsKey(key)) {
            return null;
        }
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        return trimToNull(String.valueOf(v));
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static Long parseLongFlexible(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Extract sub-table config from UserTask extension attributes
     * Look for subTableName, assigneeField, etc. in custom:properties
     */
    private Map<String, Object> extractSubTableConfig(org.flowable.bpmn.model.UserTask userTask) {
        Map<String, Object> config = new HashMap<>();
        
        Map<String, List<org.flowable.bpmn.model.ExtensionElement>> extensionElements = 
            userTask.getExtensionElements();
        
        if (extensionElements == null || extensionElements.isEmpty()) {
            return config;
        }
        
        // Find custom:properties element
        List<org.flowable.bpmn.model.ExtensionElement> propertiesElements = 
            extensionElements.get("properties");
        
        if (propertiesElements == null || propertiesElements.isEmpty()) {
            return config;
        }
        
        for (org.flowable.bpmn.model.ExtensionElement propertiesElement : propertiesElements) {
            List<org.flowable.bpmn.model.ExtensionElement> propertyElements = 
                propertiesElement.getChildElements().get("property");
            
            if (propertyElements != null) {
                for (org.flowable.bpmn.model.ExtensionElement propertyElement : propertyElements) {
                    String name = propertyElement.getAttributeValue(null, "name");
                    String value = propertyElement.getAttributeValue(null, "value");
                    
                    if (name != null && value != null) {
                        config.put(name, value);
                    }
                }
            }
        }
        
        return config;
    }
    
    /**
     * Get main table record ID from process variables
     * Main table record ID is typically stored in process variable "mainRecordId" or "businessKey"
     */
    private Long getMainRecordIdFromProcessVariables(String processInstanceId) {
        try {
            Map<String, Object> variables = runtimeService.getVariables(processInstanceId);
            
            // Try to get from mainRecordId variable
            Object mainRecordIdObj = variables.get("mainRecordId");
            if (mainRecordIdObj != null) {
                return ((Number) mainRecordIdObj).longValue();
            }
            
            // Try to get from businessKey
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
            
            if (processInstance != null && processInstance.getBusinessKey() != null) {
                try {
                    return Long.parseLong(processInstance.getBusinessKey());
                } catch (NumberFormatException e) {
                    log.warn("Cannot convert businessKey to Long: {}", processInstance.getBusinessKey());
                }
            }
            
            log.warn("Cannot get main table record ID from process variables: processInstanceId={}", processInstanceId);
            return null;
            
        } catch (Exception e) {
            log.error("Failed to get main table record ID: processInstanceId={}", processInstanceId, e);
            return null;
        }
    }
}