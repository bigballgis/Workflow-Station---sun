package com.portal.component;

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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskQueryComponent {

    private final DelegationRuleRepository delegationRuleRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessHistoryRepository processHistoryRepository;

    // 模拟的任务数据存储（实际应集成workflow-engine-core）
    private final Map<String, TaskInfo> taskStore = new HashMap<>();

    @PostConstruct
    public void init() {
        initTestData();
        log.info("TaskQueryComponent initialized with {} test tasks", taskStore.size());
    }

    /**
     * 查询用户的待办任务
     */
    public PageResponse<TaskInfo> queryTasks(TaskQueryRequest request) {
        String userId = request.getUserId();
        List<String> assignmentTypes = request.getAssignmentTypes();
        
        List<TaskInfo> allTasks = new ArrayList<>();

        // 1. 从数据库查询分配给用户或候选用户包含用户的流程实例
        try {
            Pageable pageable = PageRequest.of(0, 100); // 获取前100条
            Page<ProcessInstance> processInstances = processInstanceRepository
                    .findByAssigneeOrCandidateAndStatus(userId, "RUNNING", pageable);
            
            for (ProcessInstance instance : processInstances.getContent()) {
                TaskInfo task = convertProcessInstanceToTask(instance, userId);
                allTasks.add(task);
            }
            log.info("Found {} process instances for user {} from database", processInstances.getTotalElements(), userId);
        } catch (Exception e) {
            log.warn("Failed to query process instances from database: {}", e.getMessage());
        }

        // 2. 查询直接分配给用户的任务（模拟数据）
        if (assignmentTypes == null || assignmentTypes.isEmpty() || assignmentTypes.contains("USER")) {
            allTasks.addAll(queryDirectAssignedTasks(userId));
        }

        // 3. 查询虚拟组任务
        if (assignmentTypes == null || assignmentTypes.isEmpty() || assignmentTypes.contains("VIRTUAL_GROUP")) {
            allTasks.addAll(queryVirtualGroupTasks(userId));
        }

        // 4. 查询部门角色任务
        if (assignmentTypes == null || assignmentTypes.isEmpty() || assignmentTypes.contains("DEPT_ROLE")) {
            allTasks.addAll(queryDeptRoleTasks(userId));
        }

        // 5. 查询委托任务
        if (assignmentTypes == null || assignmentTypes.isEmpty() || assignmentTypes.contains("DELEGATED")) {
            allTasks.addAll(queryDelegatedTasks(userId));
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
        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null ? request.getSize() : 20;
        int start = page * size;
        int end = Math.min(start + size, allTasks.size());

        List<TaskInfo> pagedTasks = start < allTasks.size() 
                ? allTasks.subList(start, end) 
                : Collections.emptyList();

        return PageResponse.of(pagedTasks, page, size, allTasks.size());
    }
    
    /**
     * 将流程实例转换为任务信息
     */
    private TaskInfo convertProcessInstanceToTask(ProcessInstance instance, String userId) {
        String assignmentType = "USER";
        String assignee = instance.getCurrentAssignee();
        
        // 如果有候选用户，检查当前用户是否在候选列表中
        if (instance.getCandidateUsers() != null && !instance.getCandidateUsers().isEmpty()) {
            if (instance.getCandidateUsers().contains(userId)) {
                assignmentType = "CANDIDATE";
                assignee = userId;
            }
        }
        
        return TaskInfo.builder()
                .taskId("task-" + instance.getId())
                .taskName(instance.getCurrentNode())
                .description(instance.getBusinessKey())
                .processInstanceId(instance.getId())
                .processDefinitionKey(instance.getProcessDefinitionKey())
                .processDefinitionName(instance.getProcessDefinitionName())
                .assignmentType(assignmentType)
                .assignee(assignee)
                .assigneeName(assignee)
                .initiatorId(instance.getStartUserId())
                .initiatorName(instance.getStartUserName())
                .priority(instance.getPriority() != null ? instance.getPriority() : "NORMAL")
                .status("PENDING")
                .createTime(instance.getStartTime())
                .isOverdue(false)
                .variables(instance.getVariables())
                .build();
    }

    /**
     * 查询直接分配给用户的任务
     */
    public List<TaskInfo> queryDirectAssignedTasks(String userId) {
        return taskStore.values().stream()
                .filter(t -> "USER".equals(t.getAssignmentType()))
                .filter(t -> userId.equals(t.getAssignee()))
                .collect(Collectors.toList());
    }

    /**
     * 查询虚拟组任务
     */
    public List<TaskInfo> queryVirtualGroupTasks(String userId) {
        // 获取用户所属的虚拟组
        List<String> userGroups = getUserVirtualGroups(userId);
        
        return taskStore.values().stream()
                .filter(t -> "VIRTUAL_GROUP".equals(t.getAssignmentType()))
                .filter(t -> userGroups.contains(t.getAssignee()))
                .collect(Collectors.toList());
    }

    /**
     * 查询部门角色任务
     */
    public List<TaskInfo> queryDeptRoleTasks(String userId) {
        // 获取用户的部门角色
        List<String> userDeptRoles = getUserDeptRoles(userId);
        
        return taskStore.values().stream()
                .filter(t -> "DEPT_ROLE".equals(t.getAssignmentType()))
                .filter(t -> userDeptRoles.contains(t.getAssignee()))
                .collect(Collectors.toList());
    }

    /**
     * 查询委托给用户的任务
     */
    public List<TaskInfo> queryDelegatedTasks(String userId) {
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

        // 查询委托人的任务
        return taskStore.values().stream()
                .filter(t -> delegatorIds.contains(t.getAssignee()) || 
                            delegatorIds.contains(t.getDelegatorId()))
                .map(t -> {
                    TaskInfo delegatedTask = TaskInfo.builder()
                            .taskId(t.getTaskId())
                            .taskName(t.getTaskName())
                            .description(t.getDescription())
                            .processInstanceId(t.getProcessInstanceId())
                            .processDefinitionKey(t.getProcessDefinitionKey())
                            .processDefinitionName(t.getProcessDefinitionName())
                            .assignmentType("DELEGATED")
                            .assignee(userId)
                            .delegatorId(t.getAssignee())
                            .delegatorName(t.getAssigneeName())
                            .initiatorId(t.getInitiatorId())
                            .initiatorName(t.getInitiatorName())
                            .priority(t.getPriority())
                            .status(t.getStatus())
                            .createTime(t.getCreateTime())
                            .dueDate(t.getDueDate())
                            .isOverdue(t.getIsOverdue())
                            .formKey(t.getFormKey())
                            .variables(t.getVariables())
                            .build();
                    return delegatedTask;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取任务详情
     */
    public Optional<TaskInfo> getTaskById(String taskId) {
        // 首先从内存存储中查找
        TaskInfo task = taskStore.get(taskId);
        if (task != null) {
            return Optional.of(task);
        }
        
        // 如果是从流程实例转换的任务ID（格式：task-{processInstanceId}）
        if (taskId.startsWith("task-")) {
            String processInstanceId = taskId.substring(5); // 移除 "task-" 前缀
            try {
                Optional<ProcessInstance> instanceOpt = processInstanceRepository.findById(processInstanceId);
                if (instanceOpt.isPresent()) {
                    ProcessInstance instance = instanceOpt.get();
                    String userId = instance.getCurrentAssignee();
                    if (userId == null && instance.getCandidateUsers() != null) {
                        // 如果没有直接分配人，使用第一个候选人
                        String[] candidates = instance.getCandidateUsers().split(",");
                        if (candidates.length > 0) {
                            userId = candidates[0].trim();
                        }
                    }
                    return Optional.of(convertProcessInstanceToTask(instance, userId != null ? userId : ""));
                }
            } catch (Exception e) {
                log.warn("Failed to get process instance by id {}: {}", processInstanceId, e.getMessage());
            }
        }
        
        return Optional.empty();
    }

    /**
     * 添加任务（用于测试）
     */
    public void addTask(TaskInfo task) {
        taskStore.put(task.getTaskId(), task);
    }

    /**
     * 移除任务（用于测试）
     */
    public void removeTask(String taskId) {
        taskStore.remove(taskId);
    }

    /**
     * 清空任务（用于测试）
     */
    public void clearTasks() {
        taskStore.clear();
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
                    // 关键词搜索
                    if (request.getKeyword() != null && !request.getKeyword().isEmpty()) {
                        String keyword = request.getKeyword().toLowerCase();
                        boolean matches = (t.getTaskName() != null && t.getTaskName().toLowerCase().contains(keyword))
                                || (t.getDescription() != null && t.getDescription().toLowerCase().contains(keyword))
                                || (t.getProcessDefinitionName() != null && t.getProcessDefinitionName().toLowerCase().contains(keyword));
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
     * 获取用户所属的虚拟组（模拟实现，实际应调用admin-center服务）
     */
    private List<String> getUserVirtualGroups(String userId) {
        // 模拟返回用户所属的虚拟组
        return List.of("group_" + userId, "common_group");
    }

    /**
     * 获取用户的部门角色（模拟实现，实际应调用admin-center服务）
     */
    private List<String> getUserDeptRoles(String userId) {
        // 模拟返回用户的部门角色
        return List.of("dept_role_" + userId, "common_role");
    }

    /**
     * 获取任务统计信息
     */
    public TaskStatistics getTaskStatistics(String userId) {
        List<TaskInfo> allTasks = new ArrayList<>();
        allTasks.addAll(queryDirectAssignedTasks(userId));
        allTasks.addAll(queryVirtualGroupTasks(userId));
        allTasks.addAll(queryDeptRoleTasks(userId));
        allTasks.addAll(queryDelegatedTasks(userId));

        // 去重
        allTasks = allTasks.stream()
                .collect(Collectors.toMap(TaskInfo::getTaskId, t -> t, (t1, t2) -> t1))
                .values()
                .stream()
                .collect(Collectors.toList());

        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();

        return TaskStatistics.builder()
                .totalTasks(allTasks.size())
                .directTasks(allTasks.stream().filter(t -> "USER".equals(t.getAssignmentType())).count())
                .groupTasks(allTasks.stream().filter(t -> "VIRTUAL_GROUP".equals(t.getAssignmentType())).count())
                .deptRoleTasks(allTasks.stream().filter(t -> "DEPT_ROLE".equals(t.getAssignmentType())).count())
                .delegatedTasks(allTasks.stream().filter(t -> "DELEGATED".equals(t.getAssignmentType())).count())
                .overdueTasks(allTasks.stream().filter(t -> Boolean.TRUE.equals(t.getIsOverdue())).count())
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
        List<TaskHistoryInfo> history = new ArrayList<>();
        
        // 如果是从数据库流程实例转换的任务，从数据库读取历史
        if (taskId.startsWith("task-")) {
            String processInstanceId = taskId.substring(5);
            try {
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
                
                if (!history.isEmpty()) {
                    return history;
                }
            } catch (Exception e) {
                log.warn("Failed to get process history from database: {}", e.getMessage());
            }
        }
        
        // 如果数据库没有历史，返回模拟数据
        TaskInfo task = taskStore.get(taskId);
        if (task == null && taskId.startsWith("task-")) {
            String processInstanceId = taskId.substring(5);
            try {
                Optional<ProcessInstance> instanceOpt = processInstanceRepository.findById(processInstanceId);
                if (instanceOpt.isPresent()) {
                    ProcessInstance instance = instanceOpt.get();
                    String userId = instance.getCurrentAssignee();
                    task = convertProcessInstanceToTask(instance, userId != null ? userId : "");
                }
            } catch (Exception e) {
                log.warn("Failed to get process instance for history: {}", e.getMessage());
            }
        }
        
        if (task == null) {
            return history;
        }

        // 模拟历史记录（仅当数据库没有记录时）
        history.add(TaskHistoryInfo.builder()
                .id("history_1")
                .taskId(taskId)
                .taskName(task.getTaskName())
                .activityId("start")
                .activityName("提交申请")
                .activityType("startEvent")
                .operationType("SUBMIT")
                .operatorId(task.getInitiatorId())
                .operatorName(task.getInitiatorName())
                .operationTime(task.getCreateTime() != null ? task.getCreateTime() : LocalDateTime.now())
                .comment("提交申请")
                .duration(0L)
                .build());

        return history;
    }

    /**
     * 初始化测试数据
     */
    public void initTestData() {
        // 添加一些测试任务
        addTask(TaskInfo.builder()
                .taskId("task_1")
                .taskName("请假申请审批")
                .description("员工请假申请，请审批")
                .processInstanceId("PI_1")
                .processDefinitionKey("leave_process")
                .processDefinitionName("请假流程")
                .assignmentType("USER")
                .assignee("user_1")
                .assigneeName("张三")
                .initiatorId("user_2")
                .initiatorName("李四")
                .priority("NORMAL")
                .status("PENDING")
                .createTime(LocalDateTime.now().minusDays(1))
                .isOverdue(false)
                .build());

        addTask(TaskInfo.builder()
                .taskId("task_2")
                .taskName("报销申请审批")
                .description("差旅费报销申请")
                .processInstanceId("PI_2")
                .processDefinitionKey("expense_process")
                .processDefinitionName("报销流程")
                .assignmentType("VIRTUAL_GROUP")
                .assignee("finance_group")
                .assigneeName("财务组")
                .initiatorId("user_3")
                .initiatorName("王五")
                .priority("HIGH")
                .status("PENDING")
                .createTime(LocalDateTime.now().minusDays(2))
                .dueDate(LocalDateTime.now().plusDays(1))
                .isOverdue(false)
                .build());

        addTask(TaskInfo.builder()
                .taskId("task_3")
                .taskName("采购申请审批")
                .description("办公用品采购申请")
                .processInstanceId("PI_3")
                .processDefinitionKey("purchase_process")
                .processDefinitionName("采购流程")
                .assignmentType("DEPT_ROLE")
                .assignee("dept_manager")
                .assigneeName("部门经理")
                .initiatorId("user_4")
                .initiatorName("赵六")
                .priority("URGENT")
                .status("PENDING")
                .createTime(LocalDateTime.now().minusDays(3))
                .dueDate(LocalDateTime.now().minusDays(1))
                .isOverdue(true)
                .build());
    }
}
