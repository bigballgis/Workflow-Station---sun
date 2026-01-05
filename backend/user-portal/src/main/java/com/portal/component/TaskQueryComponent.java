package com.portal.component;

import com.portal.dto.PageResponse;
import com.portal.dto.TaskInfo;
import com.portal.dto.TaskQueryRequest;
import com.portal.entity.DelegationRule;
import com.portal.enums.DelegationStatus;
import com.portal.repository.DelegationRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

    // 模拟的任务数据存储（实际应集成workflow-engine-core）
    private final Map<String, TaskInfo> taskStore = new HashMap<>();

    /**
     * 查询用户的待办任务
     */
    public PageResponse<TaskInfo> queryTasks(TaskQueryRequest request) {
        String userId = request.getUserId();
        List<String> assignmentTypes = request.getAssignmentTypes();
        
        List<TaskInfo> allTasks = new ArrayList<>();

        // 1. 查询直接分配给用户的任务
        if (assignmentTypes == null || assignmentTypes.isEmpty() || assignmentTypes.contains("USER")) {
            allTasks.addAll(queryDirectAssignedTasks(userId));
        }

        // 2. 查询虚拟组任务
        if (assignmentTypes == null || assignmentTypes.isEmpty() || assignmentTypes.contains("VIRTUAL_GROUP")) {
            allTasks.addAll(queryVirtualGroupTasks(userId));
        }

        // 3. 查询部门角色任务
        if (assignmentTypes == null || assignmentTypes.isEmpty() || assignmentTypes.contains("DEPT_ROLE")) {
            allTasks.addAll(queryDeptRoleTasks(userId));
        }

        // 4. 查询委托任务
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
        return Optional.ofNullable(taskStore.get(taskId));
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
}
