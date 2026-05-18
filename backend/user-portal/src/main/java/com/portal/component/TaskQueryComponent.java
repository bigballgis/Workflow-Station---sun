package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.dto.PageResponse;
import com.portal.dto.TaskActionInfo;
import com.portal.dto.TaskInfo;
import com.portal.dto.TaskQueryRequest;
import com.portal.dto.TaskStatistics;
import com.portal.dto.TaskHistoryInfo;
import com.portal.util.WorkflowEnginePayloadHelper;
import com.platform.security.util.SecurityContextUtils;
import com.portal.entity.DelegationRule;
import com.portal.entity.ProcessHistory;
import com.portal.entity.ProcessInstance;
import com.portal.enums.DelegationStatus;
import com.portal.repository.DelegationRuleRepository;
import com.portal.repository.ProcessHistoryRepository;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.service.TaskActionService;
import com.portal.service.PortalWorkspaceAuthService;
import com.portal.exception.PortalException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Task query component.
 * Supports multi-dimensional task queries: direct assignment, virtual groups, department roles, delegated tasks.
 * 
 * Primary path queries the Flowable engine, then drops only {@link TaskProcessComponent#shouldHideTaskInTodoList}
 * (发起人误占下游空池), then {@link #filterFixedBuRoleTasksForActiveWorkspace} for FIXED_BU_ROLE vs JWT active BU.
 * Full {@link TaskProcessComponent#canProcessTask} is not applied to the list — engine
 * membership is authoritative; complete/claim still enforce canProcessTask.
 * When the engine returns an empty list, {@link #mergeTasksFromRunningProcessInstancesForUser} merges from
 * RUNNING instances with {@code canProcessTask} (initiator fallback path only).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskQueryComponent {

    /** 委托方待办分页单次拉取条数（避免单次过大响应） */
    private static final int DELEGATOR_ENGINE_PAGE_SIZE = 200;

    private final DelegationRuleRepository delegationRuleRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessHistoryRepository processHistoryRepository;
    private final WorkflowEngineClient workflowEngineClient;
    private final TaskActionService taskActionService;
    private final JdbcTemplate jdbcTemplate;
    private final VirtualGroupAccessComponent virtualGroupAccessComponent;
    private final PortalWorkspaceAuthService portalWorkspaceAuthService;

    /** Lazy: breaks cycle with {@link TaskProcessComponent} which depends on this component. */
    @Lazy
    @Autowired
    private TaskProcessComponent taskProcessComponent;

    /** Lazy: merge physical relation-table rows into task variables (same as process detail). */
    @Lazy
    @Autowired
    private ProcessComponent processComponent;

    @PostConstruct
    public void init() {
        log.info("TaskQueryComponent initialized, workflow engine available: {}", workflowEngineClient.isAvailable());
    }

    /**
     * Copies Flowable task variables then overlays portal {@link ProcessInstance} snapshot variables (richer payloads
     * such as {@code __subTables__}). Keeps Flowable-supplied execution-scoped {@code _currentItem}/{@code currentItem}
     * when present: the portal snapshot is single process-wide JSON and would otherwise overwrite MI iteration context.
     */
    private static void mergePortalProcessVariablesPreferringFlowableMiElementItem(
            Map<String, Object> mergedOut,
            Map<String, Object> flowableVariables,
            Map<String, Object> portalProcessVariables) {
        mergedOut.clear();
        if (flowableVariables != null) {
            mergedOut.putAll(flowableVariables);
        }
        boolean hadUnderscore = mergedOut.containsKey("_currentItem");
        Object underscoreVal = mergedOut.get("_currentItem");
        boolean hadBare = mergedOut.containsKey("currentItem");
        Object bareVal = mergedOut.get("currentItem");
        if (portalProcessVariables != null) {
            mergedOut.putAll(portalProcessVariables);
        }
        if (hadUnderscore) {
            mergedOut.put("_currentItem", underscoreVal);
        }
        if (hadBare) {
            mergedOut.put("currentItem", bareVal);
        }
    }

    /**
     * Query pending tasks for a user.
     * <p>默认：按请求的 {@code page}/{@code size} 调工作流引擎拉取待办，与 {@link #queryDelegatedTasks}（若适用）并行执行，
     * 合并后按门户规则过滤、排序，再对合并列表做分页切片。无固定 1000 条上限。
     * 关键词/优先级等筛选则按 {@code size} 分页循环拉取引擎直至取尽，再查委托后统一过滤。</p>
     */
    public PageResponse<TaskInfo> queryTasks(TaskQueryRequest request) {
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

        if (isDelegatedOnlyAssignmentFilter(assignmentTypes)) {
            return queryDelegatedTasksOnlyPage(userId, request, page, size);
        }

        if (needsFullEngineScanBeforeFilters(request)) {
            return queryTasksFullEnginePagesThenDelegate(userId, request, assignmentTypes, page, size);
        }

        return queryTasksEngineWindowThenDelegate(userId, request, assignmentTypes, page, size);
    }

    private static boolean isDelegatedOnlyAssignmentFilter(List<String> assignmentTypes) {
        return assignmentTypes != null
                && assignmentTypes.size() == 1
                && "DELEGATED".equalsIgnoreCase(assignmentTypes.get(0));
    }

    /**
     * 关键词、优先级等必须在全量引擎待办上过滤；按请求的 {@code size} 分页向引擎取直至取尽（无单次条数上限）。
     */
    private boolean needsFullEngineScanBeforeFilters(TaskQueryRequest request) {
        if (request.getPriorities() != null && !request.getPriorities().isEmpty()) {
            return true;
        }
        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            return true;
        }
        if (request.getProcessTypes() != null && !request.getProcessTypes().isEmpty()) {
            return true;
        }
        if (request.getStatuses() != null && !request.getStatuses().isEmpty()) {
            return true;
        }
        if (request.getStartTime() != null || request.getEndTime() != null) {
            return true;
        }
        if (Boolean.TRUE.equals(request.getIncludeOverdue())) {
            return true;
        }
        String sortBy = request.getSortBy();
        if (sortBy != null && !sortBy.isBlank() && !"createTime".equalsIgnoreCase(sortBy)) {
            return true;
        }
        String sortDir = request.getSortDirection();
        return sortDir != null && !sortDir.isBlank() && !"desc".equalsIgnoreCase(sortDir);
    }

    private PageResponse<TaskInfo> queryDelegatedTasksOnlyPage(
            String userId, TaskQueryRequest request, int page, int size) {
        List<TaskInfo> allTasks = new ArrayList<>(queryDelegatedTasks(userId));
        allTasks = applyPortalPostEngineFilters(userId, allTasks);
        allTasks = applyFilters(allTasks, request);
        allTasks = applySorting(allTasks, request);
        int start = page * size;
        int end = Math.min(start + size, allTasks.size());
        List<TaskInfo> pagedTasks = start < allTasks.size()
                ? allTasks.subList(start, end)
                : Collections.emptyList();
        clearTaskVariablesForList(pagedTasks);
        return PageResponse.of(pagedTasks, page, size, allTasks.size());
    }

    private PageResponse<TaskInfo> queryTasksFullEnginePagesThenDelegate(
            String userId,
            TaskQueryRequest request,
            List<String> assignmentTypes,
            int page,
            int size) {
        boolean includeDelegated = assignmentTypes == null || assignmentTypes.isEmpty()
                || assignmentTypes.contains("DELEGATED");

        SecurityContext ctx = SecurityContextHolder.getContext();
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        CompletableFuture<List<TaskInfo>> engineAllFuture = CompletableFuture.supplyAsync(() ->
                runWithInheritedRequestAndSecurity(ctx, attrs,
                        () -> fetchAllEngineTasksPaged(userId, assignmentTypes, size)));

        CompletableFuture<List<TaskInfo>> delegatedFuture = includeDelegated
                ? CompletableFuture.supplyAsync(() ->
                runWithInheritedRequestAndSecurity(ctx, attrs, () -> queryDelegatedTasks(userId)))
                : CompletableFuture.completedFuture(Collections.emptyList());

        List<TaskInfo> engineTasks = engineAllFuture.join();
        List<TaskInfo> delegated = delegatedFuture.join();

        List<TaskInfo> allTasks = new ArrayList<>(engineTasks);
        allTasks.addAll(delegated);
        allTasks = applyPortalPostEngineFilters(userId, allTasks);
        allTasks = applyFilters(allTasks, request);
        allTasks = applySorting(allTasks, request);
        int start = page * size;
        int end = Math.min(start + size, allTasks.size());
        List<TaskInfo> pagedTasks = start < allTasks.size()
                ? allTasks.subList(start, end)
                : Collections.emptyList();
        clearTaskVariablesForList(pagedTasks);
        return PageResponse.of(pagedTasks, page, size, allTasks.size());
    }

    /**
     * 引擎单页拉取结果（含 totalCount，供分页近似用）。
     */
    private record EngineWindowResult(List<TaskInfo> tasks, long engineTotal) {
    }

    /**
     * 按 {@code page}/{@code size} 向引擎拉一页待办；若为空则尝试发起人 RUNNING 合并路径。
     */
    private EngineWindowResult fetchEngineTaskPageWindow(
            String userId, List<String> assignmentTypes, int page, int size) {
        List<TaskInfo> engineTasks = new ArrayList<>();
        long engineTotal = 0L;
        try {
            List<String> groupIds = getUserVirtualGroups(userId);
            groupIds = filterVirtualGroupsForActiveWorkspace(userId, groupIds);
            boolean includeGroups = assignmentTypes == null || assignmentTypes.isEmpty()
                    || assignmentTypes.contains("VIRTUAL_GROUP");
            Optional<Map<String, Object>> result = includeGroups
                    ? workflowEngineClient.getUserAllVisibleTasks(userId, groupIds, Collections.emptyList(), page, size)
                    : workflowEngineClient.getUserTasks(userId, page, size);
            if (result.isPresent()) {
                Map<String, Object> responseBody = result.get();
                engineTotal = extractEngineTotalCount(responseBody);
                List<Map<String, Object>> tasks = WorkflowEnginePayloadHelper.taskListFromPayload(responseBody);
                if (tasks != null) {
                    for (Map<String, Object> taskMap : tasks) {
                        engineTasks.add(convertMapToTaskInfo(taskMap));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to query tasks from Flowable: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to query tasks from Flowable: " + e.getMessage(), e);
        }

        if (engineTasks.isEmpty()) {
            mergeTasksFromRunningProcessInstancesForUser(userId, engineTasks);
        }
        return new EngineWindowResult(engineTasks, engineTotal);
    }

    /**
     * 先按请求的 {@code page}/{@code size} 向引擎拉取待办，再同步拉取委托待办，合并排序后切片（当前页 = 合并列表的第 {@code page} 页）。
     * 引擎与委托待办在无依赖情况下并行拉取以缩短首屏等待。
     */
    private PageResponse<TaskInfo> queryTasksEngineWindowThenDelegate(
            String userId,
            TaskQueryRequest request,
            List<String> assignmentTypes,
            int page,
            int size) {
        boolean includeDelegated = assignmentTypes == null || assignmentTypes.isEmpty()
                || assignmentTypes.contains("DELEGATED");

        SecurityContext ctx = SecurityContextHolder.getContext();
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        CompletableFuture<EngineWindowResult> engineFuture = CompletableFuture.supplyAsync(() ->
                runWithInheritedRequestAndSecurity(ctx, attrs,
                        () -> fetchEngineTaskPageWindow(userId, assignmentTypes, page, size)));

        CompletableFuture<List<TaskInfo>> delegatedFuture = includeDelegated
                ? CompletableFuture.supplyAsync(() ->
                runWithInheritedRequestAndSecurity(ctx, attrs, () -> queryDelegatedTasks(userId)))
                : CompletableFuture.completedFuture(Collections.emptyList());

        EngineWindowResult engineResult = engineFuture.join();
        List<TaskInfo> engineTasks = engineResult.tasks();
        long engineTotal = engineResult.engineTotal();
        List<TaskInfo> delegated = delegatedFuture.join();

        List<TaskInfo> allTasks = new ArrayList<>(engineTasks);
        allTasks.addAll(delegated);
        allTasks = applyPortalPostEngineFilters(userId, allTasks);
        allTasks = applySorting(allTasks, request);

        int start = page * size;
        int end = Math.min(start + size, allTasks.size());
        List<TaskInfo> pagedTasks = start < allTasks.size()
                ? allTasks.subList(start, end)
                : Collections.emptyList();

        boolean enginePageComplete = engineTasks.size() < size || engineTotal <= (long) (page + 1) * size;
        long totalElements = enginePageComplete
                ? allTasks.size()
                : Math.max(engineTotal + (long) delegated.size(), (long) allTasks.size());

        clearTaskVariablesForList(pagedTasks);
        return PageResponse.of(pagedTasks, page, size, totalElements);
    }

    private List<TaskInfo> fetchAllEngineTasksPaged(String userId, List<String> assignmentTypes, int pageSize) {
        List<String> groupIds = getUserVirtualGroups(userId);
        groupIds = filterVirtualGroupsForActiveWorkspace(userId, groupIds);
        boolean includeGroups = assignmentTypes == null || assignmentTypes.isEmpty()
                || assignmentTypes.contains("VIRTUAL_GROUP");
        List<TaskInfo> out = new ArrayList<>();
        for (int p = 0; ; p++) {
            Optional<Map<String, Object>> result = includeGroups
                    ? workflowEngineClient.getUserAllVisibleTasks(userId, groupIds, Collections.emptyList(), p, pageSize)
                    : workflowEngineClient.getUserTasks(userId, p, pageSize);
            if (result.isEmpty()) {
                break;
            }
            List<Map<String, Object>> tasks = WorkflowEnginePayloadHelper.taskListFromPayload(result.get());
            if (tasks == null || tasks.isEmpty()) {
                break;
            }
            for (Map<String, Object> taskMap : tasks) {
                out.add(convertMapToTaskInfo(taskMap));
            }
            if (tasks.size() < pageSize) {
                break;
            }
        }
        if (out.isEmpty()) {
            mergeTasksFromRunningProcessInstancesForUser(userId, out);
        }
        return out;
    }

    private List<TaskInfo> applyPortalPostEngineFilters(String userId, List<TaskInfo> allTasks) {
        Set<String> processIds = allTasks.stream()
                .map(TaskInfo::getProcessInstanceId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        Set<String> withdrawnProcessIds = findWithdrawnProcessInstanceIds(processIds);
        List<TaskInfo> filtered = allTasks.stream()
                .filter(t -> {
                    String pid = t.getProcessInstanceId();
                    return pid == null || pid.isBlank() || !withdrawnProcessIds.contains(pid);
                })
                .collect(Collectors.toCollection(ArrayList::new));
        filtered = filtered.stream()
                .collect(Collectors.toMap(TaskInfo::getTaskId, t -> t, (t1, t2) -> t1))
                .values()
                .stream()
                .collect(Collectors.toList());
        String portalUsername = SecurityContextUtils.getCurrentUsername().orElse(null);
        if (taskProcessComponent != null) {
            filtered = filtered.stream()
                    .filter(t -> !taskProcessComponent.shouldHideTaskInTodoList(t, userId, portalUsername))
                    .collect(Collectors.toList());
        }
        return filterFixedBuRoleTasksForActiveWorkspace(filtered);
    }

    private static long extractEngineTotalCount(Map<String, Object> responseBody) {
        if (responseBody == null) {
            return 0L;
        }
        Object tc = responseBody.get("totalCount");
        if (tc instanceof Number n) {
            return Math.max(n.longValue(), 0L);
        }
        return 0L;
    }

    private static void clearTaskVariablesForList(List<TaskInfo> tasks) {
        if (tasks == null) {
            return;
        }
        for (TaskInfo t : tasks) {
            if (t != null) {
                t.setVariables(null);
            }
        }
    }

    /**
     * 当 /api/v1/tasks?userId= 聚合结果为空时，根据门户库中「当前用户为发起人的 RUNNING 实例」按 processInstanceId 再拉引擎任务。
     * 覆盖：assignee 未写入、userId 与引擎不一致、RestTemplate 静默失败等导致待办为空的场景。
     * <p>仅合并当前用户<strong>有权处理</strong>的任务（与引擎 assignee/候选人语义一致），避免把下一节点（如 BU_ROLE）误塞给发起人待办。</p>
     */
    private void mergeTasksFromRunningProcessInstancesForUser(String userId, List<TaskInfo> allTasks) {
        String portalUsername = SecurityContextUtils.getCurrentUsername().orElse(null);
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
                    if (taskProcessComponent != null && !taskProcessComponent.canProcessTask(taskInfo, userId, portalUsername)) {
                        log.debug("Fallback merge: skip task {} for user {} (not assignee/candidate/delegation pool)",
                                taskInfo.getTaskId(), userId);
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
     * Single round-trip to local portal DB: which of these process instances are withdrawn.
     * (Avoids N+1 {@code findById} per task in the hot path.)
     */
    private Set<String> findWithdrawnProcessInstanceIds(Collection<String> processInstanceIds) {
        if (processInstanceIds == null || processInstanceIds.isEmpty()) {
            return Collections.emptySet();
        }
        return processInstanceRepository.findAllById(processInstanceIds).stream()
                .filter(pi -> "WITHDRAWN".equals(pi.getStatus()))
                .map(ProcessInstance::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
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
        String assignmentTarget = engineStringField(taskMap.get("assignmentTarget"));

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
                .bpmnAssigneeType(engineStringField(taskMap.get("bpmnAssigneeType")))
                .bpmnBusinessUnitId(engineStringField(taskMap.get("bpmnBusinessUnitId")))
                .assignmentTarget(assignmentTarget)
                .assignee(currentAssignee)
                .assigneeName(currentAssigneeName)
                .initiatorId(initiatorId)
                .initiatorName(initiatorName)
                .priority(taskMap.get("priority") != null ? taskMap.get("priority").toString() : "NORMAL")
                .status((String) taskMap.get("status"))
                .createTime(parseDateTime(taskMap.get("createdTime")))
                .completedTime(parseDateTime(taskMap.get("completedTime")))
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
     * 在异步线程中继承当前请求的 Authorization 与安全上下文，供转发 workflow-engine 使用。
     */
    private static <T> T runWithInheritedRequestAndSecurity(
            SecurityContext securityContext,
            ServletRequestAttributes requestAttributes,
            Callable<T> action) {
        try {
            if (requestAttributes != null) {
                RequestContextHolder.setRequestAttributes(requestAttributes, true);
            }
            SecurityContextHolder.setContext(securityContext);
            return action.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            SecurityContextHolder.clearContext();
            RequestContextHolder.resetRequestAttributes();
        }
    }

    /**
     * 将某委托人的待办转为「受委托人为 assignee」的列表（单委托人；可与其它委托人并行查询）。
     */
    private List<TaskInfo> loadDelegatedTasksForDelegator(String delegateUserId, String delegatorId) {
        List<TaskInfo> delegatedTasks = new ArrayList<>();
        try {
            for (int p = 0; ; p++) {
                Optional<Map<String, Object>> result =
                        workflowEngineClient.getUserTasks(delegatorId, p, DELEGATOR_ENGINE_PAGE_SIZE);
                if (result.isEmpty()) {
                    break;
                }
                Map<String, Object> responseBody = result.get();
                List<Map<String, Object>> tasks = WorkflowEnginePayloadHelper.taskListFromPayload(responseBody);
                if (tasks == null || tasks.isEmpty()) {
                    break;
                }
                for (Map<String, Object> taskMap : tasks) {
                    TaskInfo taskInfo = convertMapToTaskInfo(taskMap);
                    TaskInfo delegatedTask = TaskInfo.builder()
                            .taskId(taskInfo.getTaskId())
                            .taskName(taskInfo.getTaskName())
                            .description(taskInfo.getDescription())
                            .processInstanceId(taskInfo.getProcessInstanceId())
                            .processDefinitionKey(taskInfo.getProcessDefinitionKey())
                            .processDefinitionName(taskInfo.getProcessDefinitionName())
                            .bpmnAssigneeType(taskInfo.getBpmnAssigneeType())
                            .bpmnBusinessUnitId(taskInfo.getBpmnBusinessUnitId())
                            .assignmentType("DELEGATED")
                            .assignee(delegateUserId)
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
                if (tasks.size() < DELEGATOR_ENGINE_PAGE_SIZE) {
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get delegated tasks for delegator {}: {}", delegatorId, e.getMessage());
        }
        return delegatedTasks;
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

        SecurityContext ctx = SecurityContextHolder.getContext();
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        List<CompletableFuture<List<TaskInfo>>> futures = delegatorIds.stream()
                .map(delegatorId -> CompletableFuture.supplyAsync(() -> runWithInheritedRequestAndSecurity(
                        ctx, attrs, () -> loadDelegatedTasksForDelegator(userId, delegatorId))))
                .toList();

        List<TaskInfo> delegatedTasks = new ArrayList<>();
        for (CompletableFuture<List<TaskInfo>> f : futures) {
            try {
                delegatedTasks.addAll(f.join());
            } catch (Exception e) {
                log.warn("Failed to join delegated task future: {}", e.getMessage());
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
                            if (taskInfo.getInitiatorId() == null || taskInfo.getInitiatorId().isBlank()) {
                                if (pi.getInitiatorId() != null && !pi.getInitiatorId().isBlank()) {
                                    taskInfo.setInitiatorId(pi.getInitiatorId().trim());
                                } else if (pi.getStartUserId() != null && !pi.getStartUserId().isBlank()) {
                                    taskInfo.setInitiatorId(pi.getStartUserId().trim());
                                }
                            }
                            if (taskInfo.getInitiatorName() == null || taskInfo.getInitiatorName().isBlank()) {
                                if (pi.getStartUserName() != null && !pi.getStartUserName().isBlank()) {
                                    taskInfo.setInitiatorName(pi.getStartUserName().trim());
                                }
                            }
                            if (pi.getVariables() != null) {
                                Map<String, Object> merged = new java.util.HashMap<>();
                                // Flowable first, then portal snapshot — but do not squash MI element item from engine.
                                mergePortalProcessVariablesPreferringFlowableMiElementItem(
                                        merged, taskInfo.getVariables(), pi.getVariables());
                                enrichMissingParticipantRowIdsInSubTables(merged);
                                processComponent.enrichSubTablesVariablesFromPhysicalTables(processInstanceId, merged);
                                enrichParticipantAssignmentData(merged);
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
     * 固定/指定 BU 角色池（BPMN FIXED_BU_ROLE，或 BU_ROLE 且扩展含 businessUnitId）：引擎会合并 taskCandidateUser，
     * 与候选组过滤无关。只要 JWT 含 {@code activeBusinessUnitId}，池所属 BU 必须与当前工作台一致。
     * <p>不再依赖 {@link PortalWorkspaceAuthService#listWorkspaceContexts} 非空：部分环境 UBR 数据与「切换 BU」不同步时，
     * 仅靠 VG 过滤会误判为「非工作台模式」而跳过本过滤。</p>
     */
    private List<TaskInfo> filterFixedBuRoleTasksForActiveWorkspace(List<TaskInfo> tasks) {
        Optional<String> activeBuOpt = SecurityContextUtils.getCurrentActiveBusinessUnitId();
        if (activeBuOpt.isEmpty()) {
            return tasks;
        }
        String activeBu = normalizeBuId(activeBuOpt.get());
        List<TaskInfo> out = new ArrayList<>();
        for (TaskInfo t : tasks) {
            if (t == null) {
                continue;
            }
            if (!isWorkspaceScopedBuPoolSemantics(t)) {
                out.add(t);
                continue;
            }
            String fixedBu = resolveFixedBusinessUnitForBpmnTask(t);
            if (fixedBu == null || fixedBu.isBlank()) {
                out.add(t);
                continue;
            }
            if (equalsNormalizedBuId(activeBu, fixedBu)) {
                out.add(t);
            }
        }
        return out;
    }

    private static String normalizeBuId(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim();
    }

    /**
     * FIXED_BU_ROLE，或 BPMN 为 BU_ROLE 且扩展中显式指定 businessUnitId（与引擎 {@code isWorkspaceScopedBuPoolSemantics} 对齐）。
     * <p>仅 BPMN 扩展；不读流程变量（避免跨节点残留变量误判）。</p>
     */
    private boolean isWorkspaceScopedBuPoolSemantics(TaskInfo t) {
        String bpmn = t.getBpmnAssigneeType();
        if (bpmn != null) {
            String u = bpmn.trim().toUpperCase(java.util.Locale.ROOT);
            if ("FIXED_BU_ROLE".equals(u)) {
                return true;
            }
            if ("BU_ROLE".equals(u) && t.getBpmnBusinessUnitId() != null && !t.getBpmnBusinessUnitId().isBlank()) {
                return true;
            }
        }
        return false;
    }

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

    /**
     * 固定业务单元：仅 BPMN 扩展 {@code bpmnBusinessUnitId}（与 workflow-engine 任务列表过滤语义一致，不用流程变量）。
     */
    private String resolveFixedBusinessUnitForBpmnTask(TaskInfo t) {
        String bu = t.getBpmnBusinessUnitId();
        if (bu != null && !bu.isBlank()) {
            return bu.trim();
        }
        return null;
    }

    /**
     * 工作台上下文下：仅保留「当前业务单元下对该虚拟组绑定角色拥有 UBR」的虚拟组，
     * 避免多 BU 用户在其他 BU 工作台仍看到候选组任务（引擎 user-permissions 返回全量 virtualGroupIds）。
     */
    private List<String> filterVirtualGroupsForActiveWorkspace(String userId, List<String> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return groupIds == null ? Collections.emptyList() : groupIds;
        }
        Optional<String> activeBu = SecurityContextUtils.getCurrentActiveBusinessUnitId();
        if (activeBu.isEmpty()) {
            return groupIds;
        }
        if (portalWorkspaceAuthService.listWorkspaceContexts(userId).isEmpty()) {
            return groupIds;
        }
        List<String> kept = new ArrayList<>();
        for (String gid : groupIds) {
            Optional<String> boundRoleId = virtualGroupAccessComponent.getBoundRoleIdForVirtualGroup(gid);
            if (boundRoleId.isEmpty()) {
                log.debug("Workspace VG filter: group {} has no bound role; excluded from candidate-group query", gid);
                continue;
            }
            if (portalWorkspaceAuthService.hasContext(userId, activeBu.get(), boundRoleId.get())) {
                kept.add(gid);
            }
        }
        return kept;
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
                .size(10_000)
                .build();

        PageResponse<TaskInfo> tasksResponse = queryTasks(request);
        List<TaskInfo> allTasks = tasksResponse.getContent();
        long totalTodo = tasksResponse.getTotalElements();

        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();

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
     * Multi-instance subtasks are flagged so the frontend can hide
     * the Action tag and Detail link (their detail is already visible
     * in the Participant Info Form on the application detail page).
     */
    @SuppressWarnings("unchecked")
    public PageResponse<TaskInfo> queryCompletedTasks(TaskQueryRequest request) {
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

                // Tag multi-instance subtasks so the frontend can suppress
                // the Action column and Detail link for them.
                Set<String> miTaskIds = findMultiInstanceTaskIds(
                        tasks.stream().map(TaskInfo::getTaskId).filter(Objects::nonNull).toList());
                if (!miTaskIds.isEmpty()) {
                    for (TaskInfo t : tasks) {
                        if (miTaskIds.contains(t.getTaskId())) {
                            t.setMultiInstanceSubTask(true);
                        }
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
                .taskDefinitionKey((String) taskMap.get("taskDefinitionKey"))
                .assignee(engineStringField(taskMap.get("assignee")))
                .status("COMPLETED")
                .createTime(parseDateTime(taskMap.get("startTime")))
                .completedTime(parseDateTime(taskMap.get("endTime")))
                .durationInMillis(taskMap.get("durationInMillis") != null 
                    ? ((Number) taskMap.get("durationInMillis")).longValue() : null)
                .action((String) taskMap.get("action"))
                .build();
    }

    /**
     * 流程变量中的 {@code __subTables__} 行可能仅含表单字段（无 {@code id}），门户 Assign 需要 rowId。
     * 关系表已落库时，按 email（必要时结合 name 消歧）从 {@code participants} 表回补主键。
     */
    @SuppressWarnings("unchecked")
    private void enrichMissingParticipantRowIdsInSubTables(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return;
        }
        Object subTablesObj = variables.get("__subTables__");
        if (!(subTablesObj instanceof Map<?, ?>)) {
            return;
        }
        Map<String, Object> subTables = (Map<String, Object>) subTablesObj;
        List<Map<String, Object>> pending = new ArrayList<>();
        for (Object v : subTables.values()) {
            if (!(v instanceof List<?> list)) {
                continue;
            }
            for (Object rowObj : list) {
                if (!(rowObj instanceof Map<?, ?>)) {
                    continue;
                }
                Map<String, Object> row = (Map<String, Object>) rowObj;
                if (row.get("id") != null || row.get("rowId") != null) {
                    continue;
                }
                Object email = row.get("email");
                Object name = row.get("name");
                if ((email == null || String.valueOf(email).isBlank())
                        && (name == null || String.valueOf(name).isBlank())) {
                    continue;
                }
                pending.add(row);
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        String table = "participants";
        if (!table.matches("[a-zA-Z0-9_]+")) {
            return;
        }
        try {
            Long meetingId = null;
            Object midObj = variables.get("meeting_id");
            if (midObj == null) {
                midObj = variables.get("mainRecordId");
            }
            if (midObj instanceof Number n) {
                meetingId = n.longValue();
            } else if (midObj != null) {
                try {
                    meetingId = Long.parseLong(String.valueOf(midObj).trim());
                } catch (Exception ignored) {
                    meetingId = null;
                }
            }
            int enriched = 0;
            for (Map<String, Object> row : pending) {
                String em = row.get("email") == null ? "" : String.valueOf(row.get("email")).trim();
                String nm = row.get("name") == null ? "" : String.valueOf(row.get("name")).trim();
                String dept = row.get("department") == null ? "" : String.valueOf(row.get("department")).trim();

                Long id = null;
                if (!em.isBlank()) {
                    if (meetingId != null) {
                        List<Long> ids = jdbcTemplate.query(
                                "SELECT id FROM " + table + " WHERE meeting_id = ? AND lower(trim(email)) = lower(trim(?)) ORDER BY id LIMIT 1",
                                (rs, i) -> rs.getLong("id"),
                                meetingId, em);
                        if (!ids.isEmpty()) id = ids.get(0);
                    } else {
                        List<Long> ids = jdbcTemplate.query(
                                "SELECT id FROM " + table + " WHERE lower(trim(email)) = lower(trim(?)) ORDER BY id LIMIT 1",
                                (rs, i) -> rs.getLong("id"),
                                em);
                        if (!ids.isEmpty()) id = ids.get(0);
                    }
                }

                if (id == null && !nm.isBlank() && !dept.isBlank()) {
                    if (meetingId != null) {
                        List<Long> ids = jdbcTemplate.query(
                                "SELECT id FROM " + table + " WHERE meeting_id = ? AND lower(trim(name)) = lower(trim(?)) AND lower(trim(department)) = lower(trim(?)) ORDER BY id LIMIT 1",
                                (rs, i) -> rs.getLong("id"),
                                meetingId, nm, dept);
                        if (!ids.isEmpty()) id = ids.get(0);
                    } else {
                        List<Long> ids = jdbcTemplate.query(
                                "SELECT id FROM " + table + " WHERE lower(trim(name)) = lower(trim(?)) AND lower(trim(department)) = lower(trim(?)) ORDER BY id LIMIT 1",
                                (rs, i) -> rs.getLong("id"),
                                nm, dept);
                        if (!ids.isEmpty()) id = ids.get(0);
                    }
                }

                if (id == null && !nm.isBlank()) {
                    if (meetingId != null) {
                        List<Long> ids = jdbcTemplate.query(
                                "SELECT id FROM " + table + " WHERE meeting_id = ? AND lower(trim(name)) = lower(trim(?)) ORDER BY id LIMIT 1",
                                (rs, i) -> rs.getLong("id"),
                                meetingId, nm);
                        if (!ids.isEmpty()) id = ids.get(0);
                    } else {
                        List<Long> ids = jdbcTemplate.query(
                                "SELECT id FROM " + table + " WHERE lower(trim(name)) = lower(trim(?)) ORDER BY id LIMIT 1",
                                (rs, i) -> rs.getLong("id"),
                                nm);
                        if (!ids.isEmpty()) id = ids.get(0);
                    }
                }

                if (id != null) {
                    row.put("id", id);
                    enriched++;
                }
            }
            if (enriched > 0) {
                log.debug("Enriched {} sub-table rows with DB id from {}", enriched, table);
            } else {
                log.debug("No participant row id enriched from {}", table);
            }
        } catch (Exception e) {
            log.debug("enrichMissingParticipantRowIdsInSubTables skipped: {}", e.getMessage());
        }
    }

    /**
     * 从 participants 物理表回填 assignee_display_name 和 attend_status 到 __subTables__ 行，
     * 使 completed tasks 视图能显示分配结果。
     */
    @SuppressWarnings("unchecked")
    private void enrichParticipantAssignmentData(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return;
        }
        Object subTablesObj = variables.get("__subTables__");
        if (!(subTablesObj instanceof Map<?, ?>)) {
            return;
        }
        Map<String, Object> subTables = (Map<String, Object>) subTablesObj;
        List<Map<String, Object>> rowsWithId = new ArrayList<>();
        for (Object v : subTables.values()) {
            if (!(v instanceof List<?> list)) {
                continue;
            }
            for (Object rowObj : list) {
                if (!(rowObj instanceof Map<?, ?>)) {
                    continue;
                }
                Map<String, Object> row = (Map<String, Object>) rowObj;
                Object rowId = row.get("id");
                if (rowId == null) {
                    rowId = row.get("rowId");
                }
                if (rowId != null) {
                    rowsWithId.add(row);
                }
            }
        }
        if (rowsWithId.isEmpty()) {
            return;
        }
        try {
            for (Map<String, Object> row : rowsWithId) {
                Object rowId = row.get("id");
                if (rowId == null) {
                    rowId = row.get("rowId");
                }
                Long id;
                if (rowId instanceof Number n) {
                    id = n.longValue();
                } else {
                    try {
                        id = Long.parseLong(String.valueOf(rowId).trim());
                    } catch (Exception ignored) {
                        continue;
                    }
                }
                List<Map<String, Object>> dbRows = jdbcTemplate.query(
                        "SELECT assignee_user_id, assignee_display_name, attend_status FROM participants WHERE id = ?",
                        (rs, i) -> {
                            Map<String, Object> m = new HashMap<>();
                            m.put("assignee_user_id", rs.getString("assignee_user_id"));
                            m.put("assignee_display_name", rs.getString("assignee_display_name"));
                            m.put("attend_status", rs.getString("attend_status"));
                            return m;
                        },
                        id);
                if (!dbRows.isEmpty()) {
                    Map<String, Object> dbRow = dbRows.get(0);
                    String displayName = (String) dbRow.get("assignee_display_name");
                    String assigneeUserId = (String) dbRow.get("assignee_user_id");
                    if (displayName == null && assigneeUserId != null && !assigneeUserId.isBlank()) {
                        displayName = resolveUsernameByUserId(assigneeUserId);
                    }
                    if (displayName != null) {
                        row.put("assignee_display_name", displayName);
                    }
                    if (dbRow.get("attend_status") != null) {
                        row.put("attend_status", dbRow.get("attend_status"));
                    }
                    if (assigneeUserId != null && row.get("assignee_user_id") == null) {
                        row.put("assignee_user_id", assigneeUserId);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("enrichParticipantAssignmentData skipped: {}", e.getMessage());
        }
    }

    private String resolveUsernameByUserId(String userId) {
        try {
            List<String> names = jdbcTemplate.query(
                    "SELECT COALESCE(username, display_name) FROM sys_users WHERE id = ? LIMIT 1",
                    (rs, i) -> rs.getString(1), userId);
            return names.isEmpty() ? userId : names.get(0);
        } catch (Exception e) {
            return userId;
        }
    }
}
