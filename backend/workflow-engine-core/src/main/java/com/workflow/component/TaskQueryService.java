package com.workflow.component;

import com.workflow.dto.response.TaskListResult;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;
import com.workflow.repository.ExtendedTaskInfoRepository;

import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Task query, orphan repair, and task-info building.
 * Extracted from TaskManagerComponent.
 * Delegates heavy lifting to {@link TaskInfoAssembler} and {@link TaskOrphanRepairService}.
 */
@Slf4j
@Component
@Transactional
public class TaskQueryService {

    @Autowired
    private TaskService taskService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;

    @Autowired
    private TaskInfoAssembler taskInfoAssembler;

    @Autowired
    private TaskOrphanRepairService taskOrphanRepairService;

    // ==================== Public Query Methods ====================

    public TaskListResult getUserTasks(String userId, int page, int size) {
        return getUserTasks(userId, page, size, null);
    }

    public TaskListResult getUserTasks(String userId, int page, int size, String activeBusinessUnitId) {
        try {
            validateUserId(userId);

            int fetchLimit = (page + 1) * size;
            // Rate-limited: orphan repair (incl. per-task variable reads + admin-center round-trips for
            // BU_ROLE pools) must not run on every To Do / dashboard / My Request refresh.
            taskOrphanRepairService.maybeRepairOrphanTasks(fetchLimit);

            List<Task> assignedTasks = taskService.createTaskQuery()
                .taskAssignee(userId)
                .orderByTaskCreateTime().desc()
                .listPage(0, fetchLimit);

            List<Task> candidateTasks = taskService.createTaskQuery()
                .taskCandidateUser(userId)
                .orderByTaskCreateTime().desc()
                .listPage(0, fetchLimit);

            LinkedHashMap<String, Task> taskMap = new LinkedHashMap<>();
            for (Task t : assignedTasks) taskMap.putIfAbsent(t.getId(), t);
            for (Task t : candidateTasks) taskMap.putIfAbsent(t.getId(), t);
            taskOrphanRepairService.mergeOrphanInitiatorTasksRepair(userId, fetchLimit, taskMap);

            List<Task> uniqueTasks = new ArrayList<>(taskMap.values());
            uniqueTasks.sort((t1, t2) -> t2.getCreateTime().compareTo(t1.getCreateTime()));
            uniqueTasks = taskOrphanRepairService.applyActiveWorkspaceBuTaskFilter(
                    uniqueTasks, activeBusinessUnitId, userId);

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

            Map<String, String> startUsers = taskInfoAssembler.prewarmUserDisplayNames(pagedTasks);
            List<TaskListResult.TaskInfo> taskInfos = pagedTasks.stream()
                .map(t -> taskInfoAssembler.convertFlowableTaskToTaskInfo(t, startUsers))
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

    public Map<String, String> resolveUserDisplayNames(java.util.Collection<String> userIds) {
        return taskInfoAssembler.resolveUserDisplayNames(userIds);
    }

    public TaskListResult getTasksByProcessInstance(String processInstanceId, int page, int size) {
        try {
            List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .orderByTaskCreateTime()
                .desc()
                .listPage(page * size, size);

            long totalCount = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .count();

            List<TaskListResult.TaskInfo> taskInfos = tasks.stream()
                .map(taskInfoAssembler::convertFlowableTaskToTaskInfo)
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

    public TaskListResult getUserAllVisibleTasks(String userId, List<String> groupIds,
                                                 List<String> deptRoles, int page, int size) {
        return getUserAllVisibleTasks(userId, groupIds, deptRoles, page, size, null);
    }

    public TaskListResult getUserAllVisibleTasks(String userId, List<String> groupIds,
                                                 List<String> deptRoles, int page, int size,
                                                 String activeBusinessUnitId) {
        try {
            validateUserId(userId);

            int fetchLimit = (page + 1) * size;
            taskOrphanRepairService.maybeRepairOrphanTasks(fetchLimit);

            List<Task> assignedTasks = taskService.createTaskQuery()
                .taskAssignee(userId)
                .orderByTaskCreateTime().desc()
                .listPage(0, fetchLimit);

            List<Task> candidateTasks = taskService.createTaskQuery()
                .taskCandidateUser(userId)
                .orderByTaskCreateTime().desc()
                .listPage(0, fetchLimit);

            LinkedHashMap<String, Task> taskMap = new LinkedHashMap<>();
            for (Task t : assignedTasks) taskMap.putIfAbsent(t.getId(), t);
            for (Task t : candidateTasks) taskMap.putIfAbsent(t.getId(), t);

            if (groupIds != null && !groupIds.isEmpty()) {
                List<Task> groupTasks = taskService.createTaskQuery()
                        .taskCandidateGroupIn(groupIds)
                        .orderByTaskCreateTime().desc()
                        .listPage(0, fetchLimit);
                for (Task t : groupTasks) taskMap.putIfAbsent(t.getId(), t);
            }
            taskOrphanRepairService.mergeOrphanInitiatorTasksRepair(userId, fetchLimit, taskMap);

            List<Task> uniqueTasks = new ArrayList<>(taskMap.values());
            uniqueTasks.sort((t1, t2) -> t2.getCreateTime().compareTo(t1.getCreateTime()));
            uniqueTasks = taskOrphanRepairService.applyActiveWorkspaceBuTaskFilter(
                    uniqueTasks, activeBusinessUnitId, userId);

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

            Map<String, String> startUsers = taskInfoAssembler.prewarmUserDisplayNames(pagedTasks);
            List<TaskListResult.TaskInfo> taskInfos = pagedTasks.stream()
                .map(t -> taskInfoAssembler.convertFlowableTaskToTaskInfo(t, startUsers))
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

    public TaskListResult.TaskInfo getTaskInfo(String taskId) {
        try {
            Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();

            if (task != null) {
                return taskInfoAssembler.buildTaskInfoFromFlowableTask(task);
            }

            java.util.Optional<ExtendedTaskInfo> extendedTaskInfoOpt = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId);

            if (extendedTaskInfoOpt.isPresent()) {
                return taskInfoAssembler.convertToTaskInfo(extendedTaskInfoOpt.get());
            }

            HistoricTaskInstance historicTask = historyService.createHistoricTaskInstanceQuery()
                .taskId(taskId)
                .singleResult();
            if (historicTask != null) {
                return taskInfoAssembler.buildTaskInfoFromHistoricTask(historicTask);
            }

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

    // ==================== Task Info Builders (delegated, kept for backward compat) ====================

    /** Lightweight converter for To Do list — skips full variable bag. */
    TaskListResult.TaskInfo convertFlowableTaskToTaskInfo(Task task) {
        return taskInfoAssembler.convertFlowableTaskToTaskInfo(task);
    }

    /** Detail path — includes full process-variable bag. */
    public TaskListResult.TaskInfo buildTaskInfoFromFlowableTask(Task task) {
        return taskInfoAssembler.buildTaskInfoFromFlowableTask(task);
    }

    public TaskListResult.TaskInfo buildTaskInfoFromFlowableTask(Task task, boolean includeVariables) {
        return taskInfoAssembler.buildTaskInfoFromFlowableTask(task, includeVariables);
    }

    public TaskListResult.TaskInfo buildTaskInfoFromHistoricTask(HistoricTaskInstance task) {
        return taskInfoAssembler.buildTaskInfoFromHistoricTask(task);
    }

    public TaskListResult.TaskInfo convertToTaskInfo(ExtendedTaskInfo extendedTaskInfo) {
        return taskInfoAssembler.convertToTaskInfo(extendedTaskInfo);
    }

    public String resolveUserDisplayName(String userId) {
        return taskInfoAssembler.resolveUserDisplayName(userId);
    }

    // ==================== Orphan Repair (delegated, kept for backward compat) ====================

    void maybeRepairOrphanTasks(int fetchLimit) {
        taskOrphanRepairService.maybeRepairOrphanTasks(fetchLimit);
    }

    void repairOrphanMultiInstanceTasks(int fetchLimit) {
        taskOrphanRepairService.repairOrphanMultiInstanceTasks(fetchLimit);
    }

    List<Task> applyActiveWorkspaceBuTaskFilter(List<Task> tasks, String activeBusinessUnitId, String queryUserId) {
        return taskOrphanRepairService.applyActiveWorkspaceBuTaskFilter(tasks, activeBusinessUnitId, queryUserId);
    }

    // ==================== Static Helpers ====================

    public static boolean isBpmnProcessInitiatorType(String bpmnAssigneeType) {
        if (!StringUtils.hasText(bpmnAssigneeType)) {
            return false;
        }
        String u = bpmnAssigneeType.trim().toUpperCase(Locale.ROOT);
        return "INITIATOR".equals(u) || "PROCESS_INITIATOR".equals(u);
    }

    static String pickDisplayNameFromUserInfo(Map<String, Object> userInfo, String fallback) {
        return TaskInfoAssembler.pickDisplayNameFromUserInfo(userInfo, fallback);
    }

    void validateUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "userId", "User ID cannot be empty", userId)));
        }
    }

    String getProcessDefinitionName(String processDefinitionId) {
        return taskInfoAssembler.getProcessDefinitionName(processDefinitionId);
    }

    String extractProcessDefinitionKey(String processDefinitionId) {
        return taskInfoAssembler.extractProcessDefinitionKey(processDefinitionId);
    }
}
