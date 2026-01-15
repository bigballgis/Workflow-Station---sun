package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.dto.PageResponse;
import com.portal.dto.TaskInfo;
import com.portal.dto.TaskQueryRequest;
import com.portal.dto.TaskStatistics;
import com.portal.dto.TaskHistoryInfo;
import com.portal.entity.DelegationRule;
import com.portal.entity.ProcessHistory;
import com.portal.entity.ProcessInstance;
import com.portal.enums.DelegationStatus;
import com.portal.repository.DelegationRuleRepository;
import com.portal.repository.ProcessHistoryRepository;
import com.portal.repository.ProcessInstanceRepository;
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
 * 任务查询组件
 * 支持多维度任务查询：直接分配、虚拟组、部门角色、委托任务
 * 
 * 注意：所有任务查询必须通过 Flowable 引擎完成，不允许本地回退实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskQueryComponent {

    private final DelegationRuleRepository delegationRuleRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessHistoryRepository processHistoryRepository;
    private final WorkflowEngineClient workflowEngineClient;

    @PostConstruct
    public void init() {
        log.info("TaskQueryComponent initialized, workflow engine available: {}", workflowEngineClient.isAvailable());
    }

    /**
     * 查询用户的待办任务
     * 
     * 通过 Flowable 引擎获取任务列表，支持多维度查询
     */
    public PageResponse<TaskInfo> queryTasks(TaskQueryRequest request) {
        // 检查 Flowable 引擎是否可用
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable 引擎不可用，请检查 workflow-engine-core 服务是否启动");
        }
        
        String userId = request.getUserId();
        List<String> assignmentTypes = request.getAssignmentTypes();
        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null ? request.getSize() : 20;
        
        List<TaskInfo> allTasks = new ArrayList<>();

        // 1. 从 Flowable 获取任务
        try {
            // 获取用户所属的虚拟组和部门角色
            List<String> groupIds = getUserVirtualGroups(userId);
            List<String> deptRoles = getUserDeptRoles(userId);
            
            // 根据分配类型筛选决定查询方式
            boolean includeGroups = assignmentTypes == null || assignmentTypes.isEmpty() 
                || assignmentTypes.contains("VIRTUAL_GROUP") || assignmentTypes.contains("DEPT_ROLE");
            
            Optional<Map<String, Object>> result;
            if (includeGroups) {
                result = workflowEngineClient.getUserAllVisibleTasks(userId, groupIds, deptRoles, 0, 1000);
            } else {
                result = workflowEngineClient.getUserTasks(userId, 0, 1000);
            }
            
            if (result.isPresent()) {
                Map<String, Object> responseBody = result.get();
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                if (data != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> tasks = (List<Map<String, Object>>) data.get("tasks");
                    if (tasks != null) {
                        for (Map<String, Object> taskMap : tasks) {
                            TaskInfo taskInfo = convertMapToTaskInfo(taskMap);
                            allTasks.add(taskInfo);
                        }
                    }
                }
                log.info("Found {} tasks from Flowable for user {}", allTasks.size(), userId);
            }
        } catch (Exception e) {
            log.error("Failed to query tasks from Flowable: {}", e.getMessage(), e);
            throw new IllegalStateException("从 Flowable 查询任务失败: " + e.getMessage(), e);
        }

        // 2. 查询委托任务（委托信息存储在本地）
        if (assignmentTypes == null || assignmentTypes.isEmpty() || assignmentTypes.contains("DELEGATED")) {
            List<TaskInfo> delegatedTasks = queryDelegatedTasks(userId);
            allTasks.addAll(delegatedTasks);
        }

        // 去重
        allTasks = allTasks.stream()
                .collect(Collectors.toMap(TaskInfo::getTaskId, t -> t, (t1, t2) -> t1))
                .values()
                .stream()
                .collect(Collectors.toList());

        // 应用筛选条件
        allTasks = applyFilters(allTasks, request);

        // 排序
        allTasks = applySorting(allTasks, request);

        // 分页
        int start = page * size;
        int end = Math.min(start + size, allTasks.size());

        List<TaskInfo> pagedTasks = start < allTasks.size() 
                ? allTasks.subList(start, end) 
                : Collections.emptyList();

        return PageResponse.of(pagedTasks, page, size, allTasks.size());
    }
    
    /**
     * 将 Map 转换为 TaskInfo
     */
    private TaskInfo convertMapToTaskInfo(Map<String, Object> taskMap) {
        // 优先使用 processDefinitionKey，如果没有则从 processDefinitionId 中提取
        String processDefinitionKey = (String) taskMap.get("processDefinitionKey");
        if (processDefinitionKey == null || processDefinitionKey.isEmpty()) {
            String processDefinitionId = (String) taskMap.get("processDefinitionId");
            processDefinitionKey = extractProcessDefinitionKey(processDefinitionId);
        }
        
        // 获取流程定义名称，优先使用返回的 processDefinitionName，否则使用 processDefinitionKey
        String processDefinitionName = (String) taskMap.get("processDefinitionName");
        if (processDefinitionName == null || processDefinitionName.isEmpty()) {
            processDefinitionName = processDefinitionKey;
        }
        
        // 获取发起人信息
        String initiatorId = (String) taskMap.get("initiatorId");
        String initiatorName = (String) taskMap.get("initiatorName");
        
        // 获取当前处理人
        String currentAssignee = (String) taskMap.get("currentAssignee");
        // 获取当前处理人名称，优先使用 currentAssigneeName，否则使用 currentAssignee
        String currentAssigneeName = (String) taskMap.get("currentAssigneeName");
        if (currentAssigneeName == null || currentAssigneeName.isEmpty()) {
            currentAssigneeName = currentAssignee;
        }
        
        // 确定分配类型：优先使用返回的 assignmentType，如果没有则根据 currentAssignee 判断
        String assignmentType = taskMap.get("assignmentType") != null ? taskMap.get("assignmentType").toString() : null;
        if (assignmentType == null || assignmentType.isEmpty()) {
            if (currentAssignee != null && !currentAssignee.isEmpty()) {
                // 有处理人但没有指定分配类型，默认为 USER
                assignmentType = "USER";
            } else {
                // 没有处理人也没有分配类型，默认为 VIRTUAL_GROUP
                assignmentType = "VIRTUAL_GROUP";
            }
        }
        
        return TaskInfo.builder()
                .taskId((String) taskMap.get("taskId"))
                .taskName((String) taskMap.get("taskName"))
                .description((String) taskMap.get("taskDescription"))
                .processInstanceId((String) taskMap.get("processInstanceId"))
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
                .build();
    }
    
    /**
     * 从 processDefinitionId 中提取 processDefinitionKey
     * 格式: key:version:uuid (例如: Process_PurchaseRequest:2:b550b1fe-f0b0-11f0-b82f-00ff197375e0)
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
     * 解析日期时间
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
     * 查询委托给用户的任务
     * 
     * 委托信息存储在本地数据库，需要结合 Flowable 任务信息
     */
    public List<TaskInfo> queryDelegatedTasks(String userId) {
        // 检查 Flowable 引擎是否可用
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable 引擎不可用，请检查 workflow-engine-core 服务是否启动");
        }
        
        // 获取委托给当前用户的有效委托规则
        List<DelegationRule> delegations = delegationRuleRepository
                .findActiveDelegationsForDelegate(userId, LocalDateTime.now());

        if (delegations.isEmpty()) {
            return Collections.emptyList();
        }

        // 获取委托人列表
        Set<String> delegatorIds = delegations.stream()
                .map(DelegationRule::getDelegatorId)
                .collect(Collectors.toSet());

        // 从 Flowable 获取委托人的任务
        List<TaskInfo> delegatedTasks = new ArrayList<>();
        for (String delegatorId : delegatorIds) {
            try {
                Optional<Map<String, Object>> result = workflowEngineClient.getUserTasks(delegatorId, 0, 100);
                if (result.isPresent()) {
                    Map<String, Object> responseBody = result.get();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                    if (data != null) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> tasks = (List<Map<String, Object>>) data.get("tasks");
                        if (tasks != null) {
                            for (Map<String, Object> taskMap : tasks) {
                                TaskInfo taskInfo = convertMapToTaskInfo(taskMap);
                                // 标记为委托任务
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
                }
            } catch (Exception e) {
                log.warn("Failed to get delegated tasks for delegator {}: {}", delegatorId, e.getMessage());
            }
        }
        
        return delegatedTasks;
    }

    /**
     * 获取任务详情
     */
    public Optional<TaskInfo> getTaskById(String taskId) {
        // 检查 Flowable 引擎是否可用
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable 引擎不可用，请检查 workflow-engine-core 服务是否启动");
        }
        
        try {
            Optional<Map<String, Object>> result = workflowEngineClient.getTaskById(taskId);
            if (result.isPresent()) {
                Map<String, Object> responseBody = result.get();
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                if (data != null) {
                    return Optional.of(convertMapToTaskInfo(data));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get task by id {} from Flowable: {}", taskId, e.getMessage());
        }
        
        return Optional.empty();
    }

    /**
     * 应用筛选条件
     */
    private List<TaskInfo> applyFilters(List<TaskInfo> tasks, TaskQueryRequest request) {
        return tasks.stream()
                .filter(t -> {
                    // 优先级筛选
                    if (request.getPriorities() != null && !request.getPriorities().isEmpty()) {
                        if (!request.getPriorities().contains(t.getPriority())) {
                            return false;
                        }
                    }
                    // 流程类型筛选
                    if (request.getProcessTypes() != null && !request.getProcessTypes().isEmpty()) {
                        if (!request.getProcessTypes().contains(t.getProcessDefinitionKey())) {
                            return false;
                        }
                    }
                    // 状态筛选
                    if (request.getStatuses() != null && !request.getStatuses().isEmpty()) {
                        if (!request.getStatuses().contains(t.getStatus())) {
                            return false;
                        }
                    }
                    // 时间范围筛选
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
                    // 逾期筛选
                    if (Boolean.TRUE.equals(request.getIncludeOverdue())) {
                        // 只包含逾期任务
                        if (!Boolean.TRUE.equals(t.getIsOverdue())) {
                            return false;
                        }
                    }
                    // 关键词搜索（包括发起人名称）
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
     * 应用排序
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
     * 获取用户所属的虚拟组
     * 通过 workflow-engine-core 调用 admin-center 获取
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
        // 返回空列表，不使用模拟数据
        return Collections.emptyList();
    }

    /**
     * 获取用户的部门角色
     * 通过 workflow-engine-core 调用 admin-center 获取
     */
    @SuppressWarnings("unchecked")
    private List<String> getUserDeptRoles(String userId) {
        try {
            Optional<Map<String, Object>> result = workflowEngineClient.getUserTaskPermissions(userId);
            if (result.isPresent()) {
                Map<String, Object> data = result.get();
                List<String> deptRoles = (List<String>) data.get("departmentRoles");
                if (deptRoles != null && !deptRoles.isEmpty()) {
                    return deptRoles;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get user department roles from workflow engine: {}", e.getMessage());
        }
        // 返回空列表，不使用模拟数据
        return Collections.emptyList();
    }

    /**
     * 获取任务统计信息
     */
    public TaskStatistics getTaskStatistics(String userId) {
        // 检查 Flowable 引擎是否可用
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable 引擎不可用，请检查 workflow-engine-core 服务是否启动");
        }
        
        // 从 Flowable 获取任务统计
        Optional<Map<String, Object>> countResult = workflowEngineClient.countUserTasks(userId);
        
        long totalCount = 0;
        long overdueCount = 0;
        
        if (countResult.isPresent()) {
            Map<String, Object> responseBody = countResult.get();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
            if (data != null) {
                totalCount = data.get("totalCount") != null ? ((Number) data.get("totalCount")).longValue() : 0;
                overdueCount = data.get("overdueCount") != null ? ((Number) data.get("overdueCount")).longValue() : 0;
            }
        }
        
        // 查询所有任务以获取详细统计
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
                .todayCompletedTasks(0L) // 需要从历史记录中统计
                .build();
    }

    /**
     * 获取任务流转历史
     */
    public List<TaskHistoryInfo> getTaskHistory(String taskId) {
        // 检查 Flowable 引擎是否可用
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable 引擎不可用，请检查 workflow-engine-core 服务是否启动");
        }
        
        List<TaskHistoryInfo> history = new ArrayList<>();
        
        // 首先尝试从 Flowable 获取任务信息以获取 processInstanceId
        Optional<TaskInfo> taskInfoOpt = getTaskById(taskId);
        if (taskInfoOpt.isPresent()) {
            String processInstanceId = taskInfoOpt.get().getProcessInstanceId();
            
            // 从 Flowable 获取任务历史
            Optional<List<Map<String, Object>>> historyResult = workflowEngineClient.getTaskHistory(processInstanceId);
            if (historyResult.isPresent()) {
                List<Map<String, Object>> historyList = historyResult.get();
                for (int i = 0; i < historyList.size(); i++) {
                    Map<String, Object> historyMap = historyList.get(i);
                    Long duration = null;
                    if (i > 0) {
                        // 计算持续时间
                        LocalDateTime prevTime = parseDateTime(historyList.get(i-1).get("endTime"));
                        LocalDateTime currTime = parseDateTime(historyMap.get("startTime"));
                        if (prevTime != null && currTime != null) {
                            duration = java.time.Duration.between(prevTime, currTime).toMillis();
                        }
                    }
                    
                    history.add(TaskHistoryInfo.builder()
                            .id((String) historyMap.get("id"))
                            .taskId((String) historyMap.get("taskId"))
                            .taskName((String) historyMap.get("name"))
                            .activityId((String) historyMap.get("activityId"))
                            .activityName((String) historyMap.get("activityName"))
                            .activityType((String) historyMap.get("activityType"))
                            .operationType((String) historyMap.get("deleteReason"))
                            .operatorId((String) historyMap.get("assignee"))
                            .operatorName((String) historyMap.get("assignee"))
                            .operationTime(parseDateTime(historyMap.get("endTime")))
                            .duration(duration)
                            .build());
                }
            }
        }
        
        // 如果 Flowable 没有历史记录，尝试从本地数据库获取
        if (history.isEmpty()) {
            try {
                // 尝试从本地数据库获取历史
                String processInstanceId = taskId.startsWith("task-") ? taskId.substring(5) : taskId;
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
            } catch (Exception e) {
                log.warn("Failed to get process history from database: {}", e.getMessage());
            }
        }
        
        return history;
    }
    
    /**
     * 查询用户已处理的任务列表
     */
    @SuppressWarnings("unchecked")
    public PageResponse<TaskInfo> queryCompletedTasks(TaskQueryRequest request) {
        // 检查 Flowable 引擎是否可用
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable 引擎不可用，请检查 workflow-engine-core 服务是否启动");
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
            throw new IllegalStateException("查询已处理任务失败: " + e.getMessage(), e);
        }
        
        return PageResponse.of(Collections.emptyList(), page, size, 0);
    }
    
    /**
     * 将已完成任务的 Map 转换为 TaskInfo
     */
    private TaskInfo convertCompletedTaskToTaskInfo(Map<String, Object> taskMap) {
        String processDefinitionKey = (String) taskMap.get("processDefinitionKey");
        String processDefinitionName = (String) taskMap.get("processDefinitionName");
        if (processDefinitionName == null || processDefinitionName.isEmpty()) {
            processDefinitionName = processDefinitionKey;
        }
        
        return TaskInfo.builder()
                .taskId((String) taskMap.get("taskId"))
                .taskName((String) taskMap.get("taskName"))
                .description((String) taskMap.get("taskDescription"))
                .processInstanceId((String) taskMap.get("processInstanceId"))
                .processDefinitionKey(processDefinitionKey)
                .processDefinitionName(processDefinitionName)
                .assignee((String) taskMap.get("assignee"))
                .status("COMPLETED")
                .createTime(parseDateTime(taskMap.get("startTime")))
                .completedTime(parseDateTime(taskMap.get("endTime")))
                .durationInMillis(taskMap.get("durationInMillis") != null 
                    ? ((Number) taskMap.get("durationInMillis")).longValue() : null)
                .action((String) taskMap.get("action"))
                .build();
    }
}
