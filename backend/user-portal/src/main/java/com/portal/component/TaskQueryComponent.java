package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.dto.PageResponse;
import com.portal.dto.TaskActionInfo;
import com.portal.dto.TaskInfo;
import com.portal.dto.TaskQueryRequest;
import com.portal.dto.TaskStatistics;
import com.portal.dto.TaskHistoryInfo;
import com.portal.util.WorkflowEnginePayloadHelper;
import com.portal.entity.DelegationRule;
import com.portal.entity.ProcessHistory;
import com.portal.entity.ProcessInstance;
import com.portal.enums.DelegationStatus;
import com.portal.repository.DelegationRuleRepository;
import com.portal.repository.ProcessHistoryRepository;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.service.TaskActionService;
import com.portal.exception.PortalException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Task query component.
 * Supports multi-dimensional task queries: direct assignment, virtual groups, department roles, delegated tasks.
 * 
 * Note: All task queries must go through the Flowable engine; local fallback implementations are not allowed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskQueryComponent {

    private final DelegationRuleRepository delegationRuleRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessHistoryRepository processHistoryRepository;
    private final WorkflowEngineClient workflowEngineClient;
    private final TaskActionService taskActionService;

    @PostConstruct
    public void init() {
        log.info("TaskQueryComponent initialized, workflow engine available: {}", workflowEngineClient.isAvailable());
    }

    /**
     * Query pending tasks for a user.
     * 
     * Retrieves task list from the Flowable engine with multi-dimensional query support.
     */
    public PageResponse<TaskInfo> queryTasks(TaskQueryRequest request) {
        // Check if the Flowable engine is available
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }
        
        String userId = request.getUserId();
        if (userId == null || userId.isBlank()) {
            throw new PortalException("401", "Authenticated user id is required for task query");
        }
        List<String> assignmentTypes = request.getAssignmentTypes();
        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null ? request.getSize() : 20;
        
        List<TaskInfo> allTasks = new ArrayList<>();

        // 1. Fetch tasks from Flowable
        try {
            // Get virtual groups the user belongs to
            List<String> groupIds = getUserVirtualGroups(userId);
            
            // Determine query method based on assignment type filter
            boolean includeGroups = assignmentTypes == null || assignmentTypes.isEmpty() 
                || assignmentTypes.contains("VIRTUAL_GROUP");
            
            Optional<Map<String, Object>> result;
            if (includeGroups) {
                result = workflowEngineClient.getUserAllVisibleTasks(userId, groupIds, Collections.emptyList(), 0, 1000);
            } else {
                result = workflowEngineClient.getUserTasks(userId, 0, 1000);
            }
            
            if (result.isPresent()) {
                Map<String, Object> responseBody = result.get();
                List<Map<String, Object>> tasks = WorkflowEnginePayloadHelper.taskListFromPayload(responseBody);
                if (tasks != null) {
                    log.info("Processing {} tasks from Flowable", tasks.size());
                    for (Map<String, Object> taskMap : tasks) {
                        TaskInfo taskInfo = convertMapToTaskInfo(taskMap);
                        log.info("Checking task {} from process {}", taskInfo.getTaskId(), taskInfo.getProcessInstanceId());
                        // Filter out tasks from withdrawn processes
                        if (!isProcessWithdrawn(taskInfo.getProcessInstanceId())) {
                            allTasks.add(taskInfo);
                            log.info("Task {} added to list", taskInfo.getTaskId());
                        } else {
                            log.info("Filtering out task {} from withdrawn process {}",
                                taskInfo.getTaskId(), taskInfo.getProcessInstanceId());
                        }
                    }
                }
                log.info("Found {} tasks from Flowable for user {} (after filtering withdrawn processes)",
                    allTasks.size(), userId);
            }
        } catch (Exception e) {
            log.error("Failed to query tasks from Flowable: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to query tasks from Flowable: " + e.getMessage(), e);
        }

        // 1b. 若按用户聚合查询无结果（引擎异常被吞、assignee 未写入等），按本地 RUNNING 实例逐单拉取任务
        if (allTasks.isEmpty()) {
            mergeTasksFromRunningProcessInstancesForUser(userId, allTasks);
        }

        // 2. Query delegated tasks (delegation info stored locally)
        if (assignmentTypes == null || assignmentTypes.isEmpty() || assignmentTypes.contains("DELEGATED")) {
            List<TaskInfo> delegatedTasks = queryDelegatedTasks(userId);
            allTasks.addAll(delegatedTasks);
        }

        // Deduplicate
        allTasks = allTasks.stream()
                .collect(Collectors.toMap(TaskInfo::getTaskId, t -> t, (t1, t2) -> t1))
                .values()
                .stream()
                .collect(Collectors.toList());

        // Apply filters
        allTasks = applyFilters(allTasks, request);

        // Sort
        allTasks = applySorting(allTasks, request);

        // Paginate
        int start = page * size;
        int end = Math.min(start + size, allTasks.size());

        List<TaskInfo> pagedTasks = start < allTasks.size() 
                ? allTasks.subList(start, end) 
                : Collections.emptyList();

        return PageResponse.of(pagedTasks, page, size, allTasks.size());
    }

    /**
     * 当 /api/v1/tasks?userId= 聚合结果为空时，根据门户库中「当前用户为发起人的 RUNNING 实例」按 processInstanceId 再拉引擎任务。
     * 覆盖：assignee 未写入、userId 与引擎不一致、RestTemplate 静默失败等导致待办为空的场景。
     */
    private void mergeTasksFromRunningProcessInstancesForUser(String userId, List<TaskInfo> allTasks) {
        List<ProcessInstance> running = processInstanceRepository.findByStartUserIdAndStatus(userId, "RUNNING");
        if (running.isEmpty()) {
            return;
        }
        log.info("Flowable user-task query was empty; merging from {} RUNNING process instance(s) for user {}",
                running.size(), userId);
        Set<String> seen = allTasks.stream()
                .map(TaskInfo::getTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        for (ProcessInstance pi : running) {
            String pid = pi.getId();
            if (pid == null || pid.isBlank()) {
                continue;
            }
            try {
                Optional<Map<String, Object>> tr = workflowEngineClient.getProcessInstanceTasks(pid);
                if (tr.isEmpty()) {
                    continue;
                }
                List<Map<String, Object>> tasks = WorkflowEnginePayloadHelper.taskListFromPayload(tr.get());
                if (tasks == null || tasks.isEmpty()) {
                    continue;
                }
                for (Map<String, Object> taskMap : tasks) {
                    TaskInfo taskInfo = convertMapToTaskInfo(taskMap);
                    if (taskInfo.getTaskId() == null || seen.contains(taskInfo.getTaskId())) {
                        continue;
                    }
                    if (isProcessWithdrawn(taskInfo.getProcessInstanceId())) {
                        continue;
                    }
                    seen.add(taskInfo.getTaskId());
                    allTasks.add(taskInfo);
                }
            } catch (Exception e) {
                log.warn("Fallback task query failed for processInstanceId={}: {}", pid, e.getMessage());
            }
        }
    }
    
    /**
     * Check if a process instance has been withdrawn.
     */
    private boolean isProcessWithdrawn(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isEmpty()) {
            log.debug("Process instance ID is null or empty");
            return false;
        }
        
        log.debug("Checking if process {} is withdrawn", processInstanceId);
        Optional<ProcessInstance> optInstance = processInstanceRepository.findById(processInstanceId);
        if (optInstance.isPresent()) {
            ProcessInstance instance = optInstance.get();
            boolean isWithdrawn = "WITHDRAWN".equals(instance.getStatus());
            log.debug("Process {} status: {}, isWithdrawn: {}", processInstanceId, instance.getStatus(), isWithdrawn);
            return isWithdrawn;
        }
        
        log.debug("Process {} not found in database", processInstanceId);
        return false;
    }

    /**
     * 引擎 REST 经 Map 反序列化时，用户 ID 可能为 JSON 数字（Long），不能直接 (String) 强转，
     * 否则运行时异常或字段丢失，门户会误认为 assignee 为空，权限校验失败。
     */
    private static String engineStringField(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s.isBlank() ? null : s.trim();
        }
        if (value instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isFinite(d) && Math.floor(d) == d) {
                return String.valueOf(n.longValue());
            }
            return n.toString();
        }
        if (value instanceof Boolean b) {
            return b.toString();
        }
        String t = value.toString().trim();
        return t.isEmpty() ? null : t;
    }
    
    /**
     * Convert a Map to TaskInfo.
     */
    private TaskInfo convertMapToTaskInfo(Map<String, Object> taskMap) {
        // Prefer processDefinitionKey; extract from processDefinitionId if absent
        String processDefinitionKey = (String) taskMap.get("processDefinitionKey");
        if (processDefinitionKey == null || processDefinitionKey.isEmpty()) {
            String processDefinitionId = engineStringField(taskMap.get("processDefinitionId"));
            processDefinitionKey = extractProcessDefinitionKey(processDefinitionId);
        }
        
        // Get process definition name; fall back to processDefinitionKey if not returned
        String processDefinitionName = (String) taskMap.get("processDefinitionName");
        if (processDefinitionName == null || processDefinitionName.isEmpty()) {
            processDefinitionName = processDefinitionKey;
        }
        
        // Get initiator info
        String initiatorId = engineStringField(taskMap.get("initiatorId"));
        String initiatorName = engineStringField(taskMap.get("initiatorName"));
        
        // Get current assignee
        String currentAssignee = engineStringField(taskMap.get("currentAssignee"));
        // Get current assignee name; fall back to currentAssignee if not available
        String currentAssigneeName = engineStringField(taskMap.get("currentAssigneeName"));
        if (currentAssigneeName == null || currentAssigneeName.isEmpty()) {
            currentAssigneeName = currentAssignee;
        }
        
        List<String> candidateUserIds = parseStringIdList(taskMap.get("candidateUserIds"));
        List<String> candidateGroupIds = parseStringIdList(taskMap.get("candidateGroupIds"));

        // Determine assignment type: prefer engine value, otherwise infer
        String assignmentType = null;
        Object atObj = taskMap.get("assignmentType");
        if (atObj instanceof Enum<?> en) {
            assignmentType = en.name();
        } else if (atObj != null) {
            assignmentType = atObj.toString().trim();
        }
        if (assignmentType == null || assignmentType.isEmpty()) {
            if (currentAssignee != null && !currentAssignee.isEmpty()) {
                assignmentType = "USER";
            } else if (candidateUserIds != null && !candidateUserIds.isEmpty()) {
                assignmentType = "CANDIDATE_USERS";
            } else {
                assignmentType = "VIRTUAL_GROUP";
            }
        }
        
        // Get process variables
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) taskMap.get("variables");
        
        return TaskInfo.builder()
                .taskId(engineStringField(taskMap.get("taskId")))
                .taskName((String) taskMap.get("taskName"))
                .description((String) taskMap.get("taskDescription"))
                .processInstanceId(engineStringField(taskMap.get("processInstanceId")))
                .processDefinitionKey(processDefinitionKey)
                .processDefinitionName(processDefinitionName)
                .assignmentType(assignmentType)
                .assignee(currentAssignee)
                .assigneeName(currentAssigneeName)
                .initiatorId(initiatorId)
                .initiatorName(initiatorName)
                .priority(taskMap.get("priority") != null ? taskMap.get("priority").toString() : "NORMAL")
                .status((String) taskMap.get("status"))
                .createTime(parseDateTime(taskMap.get("createdTime")))
                .dueDate(parseDateTime(taskMap.get("dueDate")))
                .isOverdue(taskMap.get("isOverdue") != null ? (Boolean) taskMap.get("isOverdue") : false)
                .formKey((String) taskMap.get("formKey"))
                .taskDefinitionKey((String) taskMap.get("taskDefinitionKey"))
                .variables(variables)
                .candidateUserIds(candidateUserIds)
                .candidateGroupIds(candidateGroupIds)
                .build();
    }

    /**
     * 解析引擎返回的候选人/候选组列表（JSON 数组或逗号分隔字符串）
     */
    private List<String> parseStringIdList(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null && !o.toString().isBlank()) {
                    out.add(o.toString().trim());
                }
            }
            return out.isEmpty() ? null : out;
        }
        if (raw instanceof String s && !s.isBlank()) {
            List<String> out = new ArrayList<>();
            for (String part : s.split(",")) {
                if (!part.isBlank()) {
                    out.add(part.trim());
                }
            }
            return out.isEmpty() ? null : out;
        }
        return null;
    }
    
    /**
     * Extract processDefinitionKey from processDefinitionId.
     * Format: key:version:uuid (e.g. Process_PurchaseRequest:2:b550b1fe-f0b0-11f0-b82f-00ff197375e0)
     */
    private String extractProcessDefinitionKey(String processDefinitionId) {
        if (processDefinitionId == null || processDefinitionId.isEmpty()) {
            return null;
        }
        int colonIndex = processDefinitionId.indexOf(':');
        if (colonIndex > 0) {
            return processDefinitionId.substring(0, colonIndex);
        }
        return processDefinitionId;
    }
    
    /**
     * Parse a date-time value.
     */
    private LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof String) {
            try {
                return LocalDateTime.parse((String) value);
            } catch (Exception e) {
                log.warn("Failed to parse datetime: {}", value);
                return null;
            }
        }
        return null;
    }

    /**
     * Query tasks delegated to a user.
     * 
     * Delegation info is stored in the local database and combined with Flowable task info.
     */
    public List<TaskInfo> queryDelegatedTasks(String userId) {
        // Check if the Flowable engine is available
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }
        
        // Get active delegation rules where the current user is the delegate
        List<DelegationRule> delegations = delegationRuleRepository
                .findActiveDelegationsForDelegate(userId, LocalDateTime.now());

        if (delegations.isEmpty()) {
            return Collections.emptyList();
        }

        // Get list of delegators
        Set<String> delegatorIds = delegations.stream()
                .map(DelegationRule::getDelegatorId)
                .collect(Collectors.toSet());

        // Fetch delegator's tasks from Flowable
        List<TaskInfo> delegatedTasks = new ArrayList<>();
        for (String delegatorId : delegatorIds) {
            try {
                Optional<Map<String, Object>> result = workflowEngineClient.getUserTasks(delegatorId, 0, 100);
                if (result.isPresent()) {
                    Map<String, Object> responseBody = result.get();
                    List<Map<String, Object>> tasks = WorkflowEnginePayloadHelper.taskListFromPayload(responseBody);
                    if (tasks != null) {
                        for (Map<String, Object> taskMap : tasks) {
                            TaskInfo taskInfo = convertMapToTaskInfo(taskMap);
                            // Mark as delegated task
                            TaskInfo delegatedTask = TaskInfo.builder()
                                    .taskId(taskInfo.getTaskId())
                                    .taskName(taskInfo.getTaskName())
                                    .description(taskInfo.getDescription())
                                    .processInstanceId(taskInfo.getProcessInstanceId())
                                    .processDefinitionKey(taskInfo.getProcessDefinitionKey())
                                    .processDefinitionName(taskInfo.getProcessDefinitionName())
                                    .assignmentType("DELEGATED")
                                    .assignee(userId)
                                    .delegatorId(delegatorId)
                                    .delegatorName(delegatorId)
                                    .initiatorId(taskInfo.getInitiatorId())
                                    .initiatorName(taskInfo.getInitiatorName())
                                    .priority(taskInfo.getPriority())
                                    .status(taskInfo.getStatus())
                                    .createTime(taskInfo.getCreateTime())
                                    .dueDate(taskInfo.getDueDate())
                                    .isOverdue(taskInfo.getIsOverdue())
                                    .formKey(taskInfo.getFormKey())
                                    .variables(taskInfo.getVariables())
                                    .build();
                            delegatedTasks.add(delegatedTask);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get delegated tasks for delegator {}: {}", delegatorId, e.getMessage());
            }
        }
        
        return delegatedTasks;
    }

    /**
     * Get task details by ID.
     */
    public Optional<TaskInfo> getTaskById(String taskId) {
        log.debug("getTaskById called with taskId: {}", taskId);
        
        // Check if the Flowable engine is available
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }
        
        log.debug("Workflow engine is available, calling getTaskById");
        
        try {
            Optional<Map<String, Object>> result = workflowEngineClient.getTaskById(taskId);
            log.debug("Got result from workflow engine: {}", result.isPresent());
            
            if (result.isPresent()) {
                Map<String, Object> responseBody = result.get();
                Map<String, Object> data = WorkflowEnginePayloadHelper.singleTaskFromPayload(responseBody);
                if (data != null) {
                    log.debug("Converting task data to TaskInfo");
                    TaskInfo taskInfo = convertMapToTaskInfo(data);
                    
                    // Supplement variables from local ProcessInstance (Flowable may lose data when serializing complex nested objects)
                    String processInstanceId = taskInfo.getProcessInstanceId();
                    if (processInstanceId != null) {
                        processInstanceRepository.findById(processInstanceId).ifPresent(pi -> {
                            if (pi.getVariables() != null) {
                                Map<String, Object> merged = new java.util.HashMap<>();
                                // Start with Flowable variables (base fields)
                                if (taskInfo.getVariables() != null) {
                                    merged.putAll(taskInfo.getVariables());
                                }
                                // Override with local DB variables (more complete, includes __subTables__)
                                merged.putAll(pi.getVariables());
                                taskInfo.setVariables(merged);
                                log.debug("Merged variables from local DB for process {}, keys: {}", 
                                    processInstanceId, merged.keySet());
                            }
                        });
                    }
                    
                    // Get available task actions: only query DB and set actions when the engine returns actionIds (including empty array);
                    // if the engine did not return actionIds (node has no Actions configured), keep actions=null so the frontend does not show default Approve/Reject.
                    Object rawActionIds = data.get("actionIds");
                    if (rawActionIds != null) {
                        try {
                            List<TaskActionInfo> actions = taskActionService.getTaskActions(taskId);
                            log.debug("Got {} actions from TaskActionService", actions != null ? actions.size() : 0);
                            taskInfo.setActions(actions != null ? actions : Collections.emptyList());
                        } catch (Exception e) {
                            log.warn("Failed to get actions for task {}: {}", taskId, e.getMessage(), e);
                            taskInfo.setActions(Collections.emptyList());
                        }
                    }
                    // When rawActionIds == null, do not set actions; keep null to indicate no Actions configured on this node
                    
                    return Optional.of(taskInfo);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get task by id {} from Flowable: {}", taskId, e.getMessage());
        }
        
        return Optional.empty();
    }

    /**
     * Apply filter criteria.
     */
    private List<TaskInfo> applyFilters(List<TaskInfo> tasks, TaskQueryRequest request) {
        return tasks.stream()
                .filter(t -> {
                    // Priority filter
                    if (request.getPriorities() != null && !request.getPriorities().isEmpty()) {
                        if (!request.getPriorities().contains(t.getPriority())) {
                            return false;
                        }
                    }
                    // Process type filter
                    if (request.getProcessTypes() != null && !request.getProcessTypes().isEmpty()) {
                        if (!request.getProcessTypes().contains(t.getProcessDefinitionKey())) {
                            return false;
                        }
                    }
                    // Status filter
                    if (request.getStatuses() != null && !request.getStatuses().isEmpty()) {
                        if (!request.getStatuses().contains(t.getStatus())) {
                            return false;
                        }
                    }
                    // Time range filter
                    if (request.getStartTime() != null && t.getCreateTime() != null) {
                        if (t.getCreateTime().isBefore(request.getStartTime())) {
                            return false;
                        }
                    }
                    if (request.getEndTime() != null && t.getCreateTime() != null) {
                        if (t.getCreateTime().isAfter(request.getEndTime())) {
                            return false;
                        }
                    }
                    // Overdue filter
                    if (Boolean.TRUE.equals(request.getIncludeOverdue())) {
                        // Only include overdue tasks
                        if (!Boolean.TRUE.equals(t.getIsOverdue())) {
                            return false;
                        }
                    }
                    // Keyword search (including initiator name)
                    if (request.getKeyword() != null && !request.getKeyword().isEmpty()) {
                        String keyword = request.getKeyword().toLowerCase();
                        boolean matches = (t.getTaskName() != null && t.getTaskName().toLowerCase().contains(keyword))
                                || (t.getDescription() != null && t.getDescription().toLowerCase().contains(keyword))
                                || (t.getProcessDefinitionName() != null && t.getProcessDefinitionName().toLowerCase().contains(keyword))
                                || (t.getInitiatorName() != null && t.getInitiatorName().toLowerCase().contains(keyword));
                        if (!matches) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    /**
     * Apply sorting.
     */
    private List<TaskInfo> applySorting(List<TaskInfo> tasks, TaskQueryRequest request) {
        String sortBy = request.getSortBy() != null ? request.getSortBy() : "createTime";
        boolean ascending = "asc".equalsIgnoreCase(request.getSortDirection());

        Comparator<TaskInfo> comparator = switch (sortBy) {
            case "priority" -> Comparator.comparing(TaskInfo::getPriority, Comparator.nullsLast(Comparator.naturalOrder()));
            case "dueDate" -> Comparator.comparing(TaskInfo::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()));
            case "taskName" -> Comparator.comparing(TaskInfo::getTaskName, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(TaskInfo::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()));
        };

        if (!ascending) {
            comparator = comparator.reversed();
        }

        return tasks.stream().sorted(comparator).collect(Collectors.toList());
    }

    /**
     * Get virtual groups the user belongs to.
     * Retrieved via workflow-engine-core calling admin-center.
     */
    @SuppressWarnings("unchecked")
    private List<String> getUserVirtualGroups(String userId) {
        try {
            Optional<Map<String, Object>> result = workflowEngineClient.getUserTaskPermissions(userId);
            if (result.isPresent()) {
                Map<String, Object> data = result.get();
                List<String> groupIds = (List<String>) data.get("virtualGroupIds");
                if (groupIds != null && !groupIds.isEmpty()) {
                    return groupIds;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get user virtual groups from workflow engine: {}", e.getMessage());
        }
        // Return empty list; do not use mock data
        return Collections.emptyList();
    }



    /**
     * Get task statistics.
     */
    public TaskStatistics getTaskStatistics(String userId) {
        // Check if the Flowable engine is available
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }
        
        // Get task statistics from Flowable
        Optional<Map<String, Object>> countResult = workflowEngineClient.countUserTasks();
        
        long totalCount = 0;
        long overdueCount = 0;
        
        if (countResult.isPresent()) {
            Map<String, Object> data = WorkflowEnginePayloadHelper.taskCountFromPayload(countResult.get());
            if (data != null) {
                totalCount = data.get("totalCount") != null ? ((Number) data.get("totalCount")).longValue() : 0;
                overdueCount = data.get("overdueCount") != null ? ((Number) data.get("overdueCount")).longValue() : 0;
            }
        }
        
        // Query all tasks for detailed statistics
        TaskQueryRequest request = TaskQueryRequest.builder()
                .userId(userId)
                .page(0)
                .size(1000)
                .build();
        
        PageResponse<TaskInfo> tasksResponse = queryTasks(request);
        List<TaskInfo> allTasks = tasksResponse.getContent();

        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();

        return TaskStatistics.builder()
                .totalTasks(allTasks.size())
                .directTasks(allTasks.stream().filter(t -> "USER".equals(t.getAssignmentType())).count())
                .groupTasks(allTasks.stream().filter(t -> "VIRTUAL_GROUP".equals(t.getAssignmentType())).count())
                .deptRoleTasks(allTasks.stream().filter(t -> "DEPT_ROLE".equals(t.getAssignmentType())).count())
                .delegatedTasks(allTasks.stream().filter(t -> "DELEGATED".equals(t.getAssignmentType())).count())
                .overdueTasks(overdueCount > 0 ? overdueCount : allTasks.stream().filter(t -> Boolean.TRUE.equals(t.getIsOverdue())).count())
                .urgentTasks(allTasks.stream().filter(t -> "URGENT".equals(t.getPriority())).count())
                .highPriorityTasks(allTasks.stream().filter(t -> "HIGH".equals(t.getPriority())).count())
                .todayNewTasks(allTasks.stream()
                        .filter(t -> t.getCreateTime() != null && t.getCreateTime().isAfter(todayStart))
                        .count())
                .todayCompletedTasks(0L) // TODO: count from history records
                .build();
    }

    /**
     * Get task flow history.
     */
    public List<TaskHistoryInfo> getTaskHistory(String taskId) {
        // Check if the Flowable engine is available
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }
        
        List<TaskHistoryInfo> history = new ArrayList<>();
        
        // Get task history from Flowable (includes user name resolution)
        Optional<List<Map<String, Object>>> historyResult = workflowEngineClient.getTaskHistoryByTaskId(taskId);
        if (historyResult.isPresent()) {
            List<Map<String, Object>> historyList = historyResult.get();
            for (int i = 0; i < historyList.size(); i++) {
                Map<String, Object> historyMap = historyList.get(i);
                Long duration = null;
                if (i > 0) {
                    // Calculate duration
                    LocalDateTime prevTime = parseDateTime(historyList.get(i-1).get("operationTime"));
                    LocalDateTime currTime = parseDateTime(historyMap.get("operationTime"));
                    if (prevTime != null && currTime != null) {
                        duration = java.time.Duration.between(prevTime, currTime).toMillis();
                    }
                }
                
                history.add(TaskHistoryInfo.builder()
                        .id((String) historyMap.get("id"))
                        .taskId((String) historyMap.get("taskId"))
                        .taskName((String) historyMap.get("taskName"))
                        .activityId((String) historyMap.get("activityId"))
                        .activityName((String) historyMap.get("activityName"))
                        .activityType((String) historyMap.get("activityType"))
                        .operationType((String) historyMap.get("operationType"))
                        .operatorId((String) historyMap.get("operatorId"))
                        .operatorName((String) historyMap.get("operatorName"))
                        .operationTime(parseDateTime(historyMap.get("operationTime")))
                        .comment((String) historyMap.get("comment"))
                        .duration(duration)
                        .build());
            }
            return history;
        }
        
        // If Flowable has no history records, try fetching from the local database
        try {
            // First try to get task info from Flowable to obtain processInstanceId
            Optional<TaskInfo> taskInfoOpt = getTaskById(taskId);
            if (taskInfoOpt.isPresent()) {
                String processInstanceId = taskInfoOpt.get().getProcessInstanceId();
                
                // Try to get history from the local database
                List<ProcessHistory> dbHistory = processHistoryRepository
                        .findByProcessInstanceIdOrderByOperationTimeAsc(processInstanceId);
                
                for (int i = 0; i < dbHistory.size(); i++) {
                    ProcessHistory ph = dbHistory.get(i);
                    Long duration = null;
                    if (i > 0 && ph.getOperationTime() != null && dbHistory.get(i-1).getOperationTime() != null) {
                        duration = java.time.Duration.between(
                                dbHistory.get(i-1).getOperationTime(), 
                                ph.getOperationTime()
                        ).toMillis();
                    }
                    
                    history.add(TaskHistoryInfo.builder()
                            .id("history_" + ph.getId())
                            .taskId(ph.getTaskId())
                            .taskName(ph.getActivityName())
                            .activityId(ph.getActivityId())
                            .activityName(ph.getActivityName())
                            .activityType(ph.getActivityType())
                            .operationType(ph.getOperationType())
                            .operatorId(ph.getOperatorId())
                            .operatorName(ph.getOperatorName())
                            .operationTime(ph.getOperationTime())
                            .comment(ph.getComment())
                            .duration(duration)
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get process history from database: {}", e.getMessage());
        }
        
        return history;
    }
    
    /**
     * Query tasks completed by a user.
     */
    @SuppressWarnings("unchecked")
    public PageResponse<TaskInfo> queryCompletedTasks(TaskQueryRequest request) {
        // Check if the Flowable engine is available
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }
        
        String userId = request.getUserId();
        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null ? request.getSize() : 20;
        String keyword = request.getKeyword();
        String startTime = request.getStartTime() != null ? request.getStartTime().toString() : null;
        String endTime = request.getEndTime() != null ? request.getEndTime().toString() : null;
        
        try {
            Optional<Map<String, Object>> result = workflowEngineClient.getCompletedTasks(
                userId, page, size, keyword, startTime, endTime);
            
            if (result.isPresent()) {
                Map<String, Object> data = result.get();
                List<Map<String, Object>> content = (List<Map<String, Object>>) data.get("content");
                long totalElements = data.get("totalElements") != null 
                    ? ((Number) data.get("totalElements")).longValue() : 0;
                
                List<TaskInfo> tasks = new ArrayList<>();
                if (content != null) {
                    for (Map<String, Object> taskMap : content) {
                        tasks.add(convertCompletedTaskToTaskInfo(taskMap));
                    }
                }
                
                return PageResponse.of(tasks, page, size, totalElements);
            }
        } catch (Exception e) {
            log.error("Failed to query completed tasks from Flowable: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to query completed tasks: " + e.getMessage(), e);
        }
        
        return PageResponse.of(Collections.emptyList(), page, size, 0);
    }
    
    /**
     * Convert a completed task Map to TaskInfo.
     */
    private TaskInfo convertCompletedTaskToTaskInfo(Map<String, Object> taskMap) {
        String processDefinitionKey = (String) taskMap.get("processDefinitionKey");
        String processDefinitionName = (String) taskMap.get("processDefinitionName");
        if (processDefinitionName == null || processDefinitionName.isEmpty()) {
            processDefinitionName = processDefinitionKey;
        }

        // Look up the actual function unit name from up_process_instance by processInstanceId, overriding the BPMN name returned by Flowable
        String processInstanceId = engineStringField(taskMap.get("processInstanceId"));
        if (processInstanceId != null && !processInstanceId.isEmpty()) {
            try {
                processInstanceRepository.findById(processInstanceId).ifPresent(instance -> {
                    // instance.getProcessDefinitionName() stores the function unit name
                });
                // Cannot assign to outer variable from lambda in findById; use direct assignment instead
                Optional<ProcessInstance> instanceOpt = processInstanceRepository.findById(processInstanceId);
                if (instanceOpt.isPresent() && instanceOpt.get().getProcessDefinitionName() != null) {
                    processDefinitionName = instanceOpt.get().getProcessDefinitionName();
                }
            } catch (Exception e) {
                log.warn("Failed to get process definition name from up_process_instance for {}: {}", processInstanceId, e.getMessage());
            }
        }
        
        return TaskInfo.builder()
                .taskId(engineStringField(taskMap.get("taskId")))
                .taskName((String) taskMap.get("taskName"))
                .description((String) taskMap.get("taskDescription"))
                .processInstanceId(engineStringField(taskMap.get("processInstanceId")))
                .processDefinitionKey(processDefinitionKey)
                .processDefinitionName(processDefinitionName)
                .assignee(engineStringField(taskMap.get("assignee")))
                .status("COMPLETED")
                .createTime(parseDateTime(taskMap.get("startTime")))
                .completedTime(parseDateTime(taskMap.get("endTime")))
                .durationInMillis(taskMap.get("durationInMillis") != null 
                    ? ((Number) taskMap.get("durationInMillis")).longValue() : null)
                .action((String) taskMap.get("action"))
                .build();
    }
}
