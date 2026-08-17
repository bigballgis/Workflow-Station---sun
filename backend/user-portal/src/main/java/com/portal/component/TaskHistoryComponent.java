package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.dto.PageResponse;
import com.portal.dto.TaskHistoryInfo;
import com.portal.dto.TaskInfo;
import com.portal.dto.TaskQueryRequest;
import com.portal.dto.TaskStatistics;
import com.portal.entity.DelegationAudit;
import com.portal.entity.ProcessHistory;
import com.portal.entity.ProcessInstance;
import com.portal.repository.DelegationAuditRepository;
import com.portal.repository.ProcessHistoryRepository;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.util.TaskInfoListOps;
import com.portal.util.WorkflowEnginePayloadHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Task history, completed-task and statistics queries against the workflow engine,
 * with local-database fallbacks. Extracted from {@link TaskQueryComponent}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskHistoryComponent {

    static final String OP_ACT_AS_COMPLETE = "ACT_AS_COMPLETE";

    /** Bounded pool for acting-completed engine hydrate (read-only HTTP fan-out). */
    private static final ExecutorService ACTING_HYDRATE_EXECUTOR = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "acting-completed-hydrate");
        t.setDaemon(true);
        return t;
    });

    private final WorkflowEngineClient workflowEngineClient;
    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessHistoryRepository processHistoryRepository;
    private final JdbcTemplate jdbcTemplate;
    private final RequestIdEnricher requestIdEnricher;
    private final DelegationAuditRepository delegationAuditRepository;

    /** Lazy: breaks cycle with {@link TaskQueryComponent} which delegates to this component. */
    @Lazy
    @Autowired
    private TaskQueryComponent taskQueryComponent;

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
                .size(10_000)
                .build();

        PageResponse<TaskInfo> tasksResponse = taskQueryComponent.queryTasks(request);
        List<TaskInfo> allTasks = tasksResponse.getContent();
        long totalTodo = tasksResponse.getTotalElements();

        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        long todayCompletedTasks = countCompletedTasksInRange(
                userId, todayStart.toString(), LocalDateTime.now().toString());

        return TaskStatistics.builder()
                .totalTasks(totalTodo)
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
                .todayCompletedTasks(todayCompletedTasks)
                .build();
    }

    /**
     * Count tasks the user completed within a time range (via workflow-engine history API).
     * Aligns with {@code DashboardComponent} completedTodayCount aggregation.
     */
    private long countCompletedTasksInRange(String userId, String startIso, String endIso) {
        try {
            Optional<Map<String, Object>> result = workflowEngineClient.getCompletedTasks(
                    userId, 0, 1, null, startIso, endIso);
            if (result.isPresent()) {
                Object totalElements = result.get().get("totalElements");
                if (totalElements instanceof Number) {
                    return ((Number) totalElements).longValue();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to count completed tasks for user {} in range {} - {}: {}",
                    userId, startIso, endIso, e.getMessage());
        }
        return 0L;
    }

    /**
     * Get task flow history.
     */
    public List<TaskHistoryInfo> getTaskHistory(String taskId) {
        return getTaskHistory(taskId, null);
    }

    /**
     * Get task flow history. When {@code processInstanceId} is known (e.g. from task detail), skip an extra
     * workflow-engine {@code getTaskInfo} round-trip inside {@code GET /tasks/{taskId}/history}.
     */
    public List<TaskHistoryInfo> getTaskHistory(String taskId, String processInstanceId) {
        // Check if the Flowable engine is available
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }

        List<TaskHistoryInfo> history = new ArrayList<>();

        // Get task history from Flowable (includes user name resolution)
        Optional<List<Map<String, Object>>> historyResult;
        if (processInstanceId != null && !processInstanceId.isBlank()) {
            historyResult = workflowEngineClient.getProcessInstanceHistory(processInstanceId.trim());
        } else {
            historyResult = workflowEngineClient.getTaskHistoryByTaskId(taskId);
        }
        if (historyResult.isPresent()) {
            List<Map<String, Object>> historyList = historyResult.get();
            for (int i = 0; i < historyList.size(); i++) {
                Map<String, Object> historyMap = historyList.get(i);
                Long duration = null;
                if (i > 0) {
                    // Calculate duration
                    LocalDateTime prevTime = EngineTaskMapper.parseDateTime(historyList.get(i - 1).get("operationTime"));
                    LocalDateTime currTime = EngineTaskMapper.parseDateTime(historyMap.get("operationTime"));
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
                        .operationTime(EngineTaskMapper.parseDateTime(historyMap.get("operationTime")))
                        .comment((String) historyMap.get("comment"))
                        .duration(duration)
                        .build());
            }
            return history;
        }

        // If Flowable has no history records, try fetching from the local database
        try {
            String resolvedProcessInstanceId = processInstanceId;
            if (resolvedProcessInstanceId == null || resolvedProcessInstanceId.isBlank()) {
                Optional<TaskInfo> taskInfoOpt = taskQueryComponent.getTaskById(taskId);
                if (taskInfoOpt.isPresent()) {
                    resolvedProcessInstanceId = taskInfoOpt.get().getProcessInstanceId();
                }
            }
            if (resolvedProcessInstanceId != null && !resolvedProcessInstanceId.isBlank()) {
                // Try to get history from the local database
                List<ProcessHistory> dbHistory = processHistoryRepository
                        .findByProcessInstanceIdOrderByOperationTimeAsc(resolvedProcessInstanceId);

                for (int i = 0; i < dbHistory.size(); i++) {
                    ProcessHistory ph = dbHistory.get(i);
                    Long duration = null;
                    if (i > 0 && ph.getOperationTime() != null && dbHistory.get(i - 1).getOperationTime() != null) {
                        duration = java.time.Duration.between(
                                dbHistory.get(i - 1).getOperationTime(),
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
     * Multi-instance subtasks are flagged so the frontend can hide
     * the Action tag and Detail link (their detail is already visible
     * in the Participant Info Form on the application detail page).
     */
    @SuppressWarnings("unchecked")
    public PageResponse<TaskInfo> queryCompletedTasks(TaskQueryRequest request) {
        if (isActingCompletedOnly(request.getAssignmentTypes())) {
            return queryActingCompletedFromAudit(request);
        }

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
            if (TaskInfoListOps.needsMemoryScanForCompleted(request)) {
                return queryCompletedTasksFullThenFilter(request, userId, page, size, keyword, startTime, endTime);
            }

            Optional<Map<String, Object>> result = workflowEngineClient.getCompletedTasks(
                userId, page, size, keyword, startTime, endTime);

            if (result.isPresent()) {
                Map<String, Object> data = result.get();
                List<Map<String, Object>> content = (List<Map<String, Object>>) data.get("content");
                long totalElements = data.get("totalElements") != null
                    ? ((Number) data.get("totalElements")).longValue() : 0;

                List<TaskInfo> tasks = convertAndEnrichCompletedPage(userId, content);
                return PageResponse.of(tasks, page, size, totalElements);
            }
        } catch (Exception e) {
            log.error("Failed to query completed tasks from Flowable: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to query completed tasks: " + e.getMessage(), e);
        }

        return PageResponse.of(Collections.emptyList(), page, size, 0);
    }

    /**
     * When column filters / non-default sort are present, pull all engine pages, filter+sort in memory,
     * then return the requested page with a correct filtered total.
     */
    @SuppressWarnings("unchecked")
    private PageResponse<TaskInfo> queryCompletedTasksFullThenFilter(
            TaskQueryRequest request,
            String userId,
            int page,
            int size,
            String keyword,
            String startTime,
            String endTime) {
        List<Map<String, Object>> allMaps = new ArrayList<>();
        int enginePage = 0;
        long reportedTotal = -1;
        int enginePageSize = Math.max(1, Math.min(size, 100));
        while (true) {
            Optional<Map<String, Object>> result = workflowEngineClient.getCompletedTasks(
                    userId, enginePage, enginePageSize, keyword, startTime, endTime);
            if (result.isEmpty()) {
                break;
            }
            Map<String, Object> data = result.get();
            if (reportedTotal < 0 && data.get("totalElements") instanceof Number n) {
                reportedTotal = n.longValue();
            }
            List<Map<String, Object>> content = (List<Map<String, Object>>) data.get("content");
            if (content == null || content.isEmpty()) {
                break;
            }
            allMaps.addAll(content);
            if (content.size() < enginePageSize) {
                break;
            }
            enginePage++;
            // Stop when engine-reported total is reached (exact, not an artificial page cap).
            if (reportedTotal >= 0 && allMaps.size() >= reportedTotal) {
                break;
            }
        }

        List<TaskInfo> tasks = convertAndEnrichCompletedPage(userId, allMaps);
        tasks = TaskInfoListOps.applyColumnFilters(tasks, request.getFilters());
        tasks = TaskInfoListOps.applySorting(tasks, request);
        long total = tasks.size();
        List<TaskInfo> pageTasks = TaskInfoListOps.pageOf(tasks, page, size);
        return PageResponse.of(pageTasks, page, size, total);
    }

    private List<TaskInfo> convertAndEnrichCompletedPage(String userId, List<Map<String, Object>> content) {
        List<TaskInfo> tasks = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return tasks;
        }
        Map<String, String> processNameById = resolveProcessNamesForCompletedTasks(content);
        for (Map<String, Object> taskMap : content) {
            tasks.add(convertCompletedTaskToTaskInfo(taskMap, processNameById));
        }
        Set<String> miTaskIds = findMultiInstanceTaskIds(
                tasks.stream().map(TaskInfo::getTaskId).filter(Objects::nonNull).toList());
        if (!miTaskIds.isEmpty()) {
            for (TaskInfo t : tasks) {
                if (miTaskIds.contains(t.getTaskId())) {
                    t.setMultiInstanceSubTask(true);
                }
            }
        }
        enrichMineCompletedWithProxyActors(userId, tasks);
        requestIdEnricher.enrichTaskRequestIds(tasks);
        return tasks;
    }

    private static boolean isActingCompletedOnly(List<String> assignmentTypes) {
        return assignmentTypes != null
                && assignmentTypes.size() == 1
                && "DELEGATED".equalsIgnoreCase(assignmentTypes.get(0));
    }

    /**
     * Completed "Acting for others" tab: page {@code ACT_AS_COMPLETE} audit rows for the delegate.
     */
    private PageResponse<TaskInfo> queryActingCompletedFromAudit(TaskQueryRequest request) {
        String userId = request.getUserId();
        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null ? request.getSize() : 20;
        boolean memoryScan = TaskInfoListOps.needsMemoryScanForCompleted(request);

        List<DelegationAudit> audits;
        long auditTotal;
        if (memoryScan) {
            // Fetch all acting-complete audits for this delegate, then filter/sort/page (exact total).
            int auditPageSize = Math.max(size, 200);
            Page<DelegationAudit> all = delegationAuditRepository
                    .findByDelegateIdAndOperationTypeOrderByCreatedAtDesc(
                            userId, OP_ACT_AS_COMPLETE, PageRequest.of(0, auditPageSize));
            audits = new ArrayList<>(all.getContent());
            int p = 1;
            while (all.hasNext()) {
                all = delegationAuditRepository.findByDelegateIdAndOperationTypeOrderByCreatedAtDesc(
                        userId, OP_ACT_AS_COMPLETE, PageRequest.of(p++, auditPageSize));
                audits.addAll(all.getContent());
            }
            auditTotal = audits.size();
        } else {
            Page<DelegationAudit> auditPage = delegationAuditRepository
                    .findByDelegateIdAndOperationTypeOrderByCreatedAtDesc(
                            userId, OP_ACT_AS_COMPLETE, PageRequest.of(page, size));
            audits = auditPage.getContent();
            auditTotal = auditPage.getTotalElements();
        }

        List<TaskInfo> tasks = hydrateActingCompletedTasks(audits);
        requestIdEnricher.enrichTaskRequestIds(tasks);
        if (memoryScan) {
            tasks = TaskInfoListOps.applyColumnFilters(tasks, request.getFilters());
            tasks = TaskInfoListOps.applySorting(tasks, request);
            long total = tasks.size();
            return PageResponse.of(TaskInfoListOps.pageOf(tasks, page, size), page, size, total);
        }
        return PageResponse.of(tasks, page, size, auditTotal);
    }

    /**
     * Build acting-completed rows and hydrate engine task details with bounded parallel fan-out
     * (avoids sequential N+1 HTTP on filter/sort scans).
     */
    private List<TaskInfo> hydrateActingCompletedTasks(List<DelegationAudit> audits) {
        if (audits == null || audits.isEmpty()) {
            return List.of();
        }
        Map<String, Map<String, Object>> detailsById = new HashMap<>();
        if (workflowEngineClient.isAvailable()) {
            List<String> taskIds = audits.stream()
                    .map(DelegationAudit::getTaskId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!taskIds.isEmpty()) {
                List<CompletableFuture<Void>> futures = new ArrayList<>(taskIds.size());
                for (String taskId : taskIds) {
                    futures.add(CompletableFuture.runAsync(() -> {
                        try {
                            Optional<Map<String, Object>> detail = workflowEngineClient.getTaskById(taskId);
                            detail.ifPresent(m -> {
                                synchronized (detailsById) {
                                    detailsById.put(taskId, m);
                                }
                            });
                        } catch (Exception e) {
                            log.debug("Could not hydrate completed acting task {}: {}", taskId, e.getMessage());
                        }
                    }, ACTING_HYDRATE_EXECUTOR));
                }
                try {
                    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
                } catch (Exception e) {
                    log.warn("Acting-completed hydrate fan-out interrupted: {}", e.getMessage());
                }
            }
        }
        List<TaskInfo> tasks = new ArrayList<>(audits.size());
        for (DelegationAudit audit : audits) {
            TaskInfo.TaskInfoBuilder b = TaskInfo.builder()
                    .taskId(audit.getTaskId())
                    .assignmentType("DELEGATED")
                    .delegatorId(audit.getDelegatorId())
                    .delegatorName(audit.getDelegatorId())
                    .assignee(audit.getDelegateId())
                    .status("COMPLETED")
                    .completedTime(audit.getCreatedAt())
                    .action(audit.getOperationDetail() != null ? audit.getOperationDetail() : "APPROVE")
                    .taskName(audit.getTaskId());
            Map<String, Object> m = audit.getTaskId() != null ? detailsById.get(audit.getTaskId()) : null;
            if (m != null) {
                Object name = m.get("taskName") != null ? m.get("taskName") : m.get("name");
                if (name != null) {
                    b.taskName(String.valueOf(name));
                }
                if (m.get("processInstanceId") != null) {
                    b.processInstanceId(String.valueOf(m.get("processInstanceId")));
                }
                if (m.get("processDefinitionKey") != null) {
                    b.processDefinitionKey(String.valueOf(m.get("processDefinitionKey")));
                }
                if (m.get("processDefinitionName") != null) {
                    b.processDefinitionName(String.valueOf(m.get("processDefinitionName")));
                }
            }
            tasks.add(b.build());
        }
        return tasks;
    }

    /** On mine completed rows, mark tasks finished by a standing proxy (badge: acted by B). */
    private void enrichMineCompletedWithProxyActors(String delegatorId, List<TaskInfo> tasks) {
        if (delegatorId == null || tasks == null || tasks.isEmpty()) {
            return;
        }
        List<String> taskIds = tasks.stream()
                .map(TaskInfo::getTaskId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (taskIds.isEmpty()) {
            return;
        }
        List<DelegationAudit> audits = delegationAuditRepository
                .findByDelegatorIdAndOperationTypeAndTaskIdIn(delegatorId, OP_ACT_AS_COMPLETE, taskIds);
        if (audits.isEmpty()) {
            return;
        }
        Map<String, String> actorByTask = new HashMap<>();
        for (DelegationAudit a : audits) {
            if (a.getTaskId() != null && a.getDelegateId() != null) {
                actorByTask.putIfAbsent(a.getTaskId(), a.getDelegateId());
            }
        }
        for (TaskInfo t : tasks) {
            String actor = actorByTask.get(t.getTaskId());
            if (actor != null) {
                t.setAction("ACTED_BY_PROXY");
                t.setDelegatorId(actor);
                t.setDelegatorName(actor);
            }
        }
    }

    /**
     * Batch-check which of the given task IDs are multi-instance subtasks
     * by looking at wf_extended_task_info.extended_properties.
     */
    private Set<String> findMultiInstanceTaskIds(List<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) return Collections.emptySet();
        try {
            String placeholders = String.join(",", Collections.nCopies(taskIds.size(), "?"));
            String sql = "SELECT task_id FROM wf_extended_task_info "
                    + "WHERE task_id IN (" + placeholders + ") "
                    + "AND is_deleted = false "
                    + "AND extended_properties LIKE '%\"multiInstance\":true%'";
            List<String> ids = jdbcTemplate.query(sql,
                    (rs, i) -> rs.getString("task_id"),
                    taskIds.toArray());
            return new HashSet<>(ids);
        } catch (Exception e) {
            log.debug("findMultiInstanceTaskIds skipped: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * Batch-resolve the local function-unit process names for a page of completed tasks.
     * One {@code findAllById} instead of a per-row {@code findById} (N+1 avoidance).
     */
    private Map<String, String> resolveProcessNamesForCompletedTasks(List<Map<String, Object>> content) {
        Set<String> ids = new HashSet<>();
        for (Map<String, Object> taskMap : content) {
            String pid = EngineTaskMapper.engineStringField(taskMap.get("processInstanceId"));
            if (pid != null && !pid.isEmpty()) {
                ids.add(pid);
            }
        }
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> nameById = new java.util.HashMap<>();
        try {
            for (ProcessInstance pi : processInstanceRepository.findAllById(ids)) {
                if (pi.getProcessDefinitionName() != null) {
                    nameById.put(pi.getId(), pi.getProcessDefinitionName());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to batch-resolve process definition names for {} completed tasks: {}",
                    ids.size(), e.getMessage());
        }
        return nameById;
    }

    /**
     * Convert a completed task Map to TaskInfo.
     *
     * @param processNameById local function-unit names keyed by processInstanceId (batch-resolved by the caller)
     */
    private TaskInfo convertCompletedTaskToTaskInfo(Map<String, Object> taskMap, Map<String, String> processNameById) {
        String processDefinitionKey = (String) taskMap.get("processDefinitionKey");
        String processDefinitionName = (String) taskMap.get("processDefinitionName");
        if (processDefinitionName == null || processDefinitionName.isEmpty()) {
            processDefinitionName = processDefinitionKey;
        }

        // Override the BPMN name returned by Flowable with the local function-unit name (batch-resolved, no per-row query).
        String processInstanceId = EngineTaskMapper.engineStringField(taskMap.get("processInstanceId"));
        if (processInstanceId != null && !processInstanceId.isEmpty()) {
            String localName = processNameById.get(processInstanceId);
            if (localName != null) {
                processDefinitionName = localName;
            }
        }

        return TaskInfo.builder()
                .taskId(EngineTaskMapper.engineStringField(taskMap.get("taskId")))
                .taskName((String) taskMap.get("taskName"))
                .description((String) taskMap.get("taskDescription"))
                .processInstanceId(EngineTaskMapper.engineStringField(taskMap.get("processInstanceId")))
                .processDefinitionKey(processDefinitionKey)
                .processDefinitionName(processDefinitionName)
                .taskDefinitionKey((String) taskMap.get("taskDefinitionKey"))
                .assignee(EngineTaskMapper.engineStringField(taskMap.get("assignee")))
                .status("COMPLETED")
                .createTime(EngineTaskMapper.parseDateTime(taskMap.get("startTime")))
                .completedTime(EngineTaskMapper.parseDateTime(taskMap.get("endTime")))
                .durationInMillis(taskMap.get("durationInMillis") != null
                    ? ((Number) taskMap.get("durationInMillis")).longValue() : null)
                .action((String) taskMap.get("action"))
                .build();
    }
}
