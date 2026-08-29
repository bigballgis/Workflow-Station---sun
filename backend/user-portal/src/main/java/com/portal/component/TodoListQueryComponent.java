package com.portal.component;

import com.platform.common.list.ListColumnFilter;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.PageResponse;
import com.portal.dto.PortalListGroup;
import com.portal.dto.PortalListPage;
import com.portal.dto.TaskInfo;
import com.portal.dto.TaskQueryRequest;
import com.portal.dto.TodoTaskQueryRequest;
import com.portal.exception.PortalException;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.util.BuRolePoolTasks;
import com.portal.util.ListQuerySupport;
import com.portal.util.TaskInfoListOps;
import com.portal.util.TaskInfoQueryFilters;
import com.portal.util.ToClaimTaskColumnSpec;
import com.portal.util.TodoListUnion;
import com.portal.util.TodoTaskColumnSpec;
import com.portal.util.WithdrawnProcessIds;
import com.portal.util.WorkflowEnginePayloadHelper;
import com.platform.security.util.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared-list To Do and Tasks to Claim, plus Claim All / Unclaim All pool scans.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TodoListQueryComponent {

    private final WorkflowEngineClient workflowEngineClient;
    private final ProcessInstanceRepository processInstanceRepository;
    private final WorkspaceTaskFilterComponent workspaceTaskFilter;
    private final RequestIdEnricher requestIdEnricher;
    private final TaskPermissionEvaluator taskPermissionEvaluator;
    private final ClaimForceUnclaimAnnotator claimForceUnclaimAnnotator;
    private final MineTaskScanner mineTaskScanner;

    public PortalListPage<TaskInfo> queryTodoList(String userId, TodoTaskQueryRequest request) {
        long started = System.nanoTime();
        TaskQueryRequest adapted = toTaskQuery(userId, request);
        List<PortalListGroup> groups = new ArrayList<>();
        PageResponse<TaskInfo> page = queryMergedTodoPage(userId, adapted, request.page(), request.size(), groups);
        if (request.groupBy() != null && !request.groupBy().isBlank()
                && page.getTotalElements() > 0 && groups.isEmpty()) {
            throw new IllegalStateException("GROUP BY returned no groups for a non-empty todo result");
        }
        long total = page.getTotalElements();
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        ListQuerySupport.logIfSlow(log, TaskQueryComponent.TODO_LIST_KEY, request.page(), request.size(), total, started);
        ListQuerySupport.logIfOverSla(log, TaskQueryComponent.TODO_LIST_KEY, request.page(), request.size(), total,
                elapsedMs, elapsedMs, 0L);
        return new PortalListPage<>(TodoTaskColumnSpec.columns(), page.getContent(), List.copyOf(groups),
                request.page(), request.size(), total);
    }

    public List<TaskInfo> listClaimPoolTasks(String userId) {
        return fetchAllClaimPoolTasksPaged(userId);
    }

    /**
     * Same Mine ∪ claim-pool set as the To Do page. Unclaim All must use this: sole-assignee
     * BU Role holds have no candidate link, so they appear on To Do via Mine and are invisible
     * to {@link #listClaimPoolTasks}.
     */
    public List<TaskInfo> listMergedTodoTasks(String userId) {
        TaskQueryRequest mineFetch = TaskQueryRequest.builder()
                .userId(userId)
                .page(0)
                .size(500)
                .build();
        List<TaskInfo> merged = TodoListUnion.merge(
                mineTaskScanner.scanMineTasks(userId, mineFetch, null, 500),
                fetchAllClaimPoolTasksPaged(userId));
        String portalUsername = SecurityContextUtils.getCurrentUsername().orElse(null);
        taskPermissionEvaluator.annotateClaimState(merged, userId, portalUsername);
        return merged;
    }

    public PortalListPage<TaskInfo> queryToClaimList(String userId, TodoTaskQueryRequest request) {
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }
        if (userId == null || userId.isBlank()) {
            throw new PortalException("401", "Authenticated user id is required for task query");
        }
        long started = System.nanoTime();

        List<ListColumnFilter> filters = new ArrayList<>(request.filters());
        if (!request.priorities().isEmpty()) {
            filters.add(new ListColumnFilter("priority", "in", String.join(",", request.priorities()), null));
        }
        TaskQueryRequest adapted = TaskQueryRequest.builder()
                .userId(userId)
                .page(request.page())
                .size(request.size())
                .filters(filters)
                .sortBy(request.sortField())
                .sortDirection(request.sortDirection())
                .groupBy(request.groupBy())
                .keyword(request.keyword())
                .build();

        List<TaskInfo> poolTasks = fetchAllClaimPoolTasksPaged(userId);
        maybeEnrichRequestIds(poolTasks, adapted);
        List<TaskInfo> filtered = TaskInfoQueryFilters.apply(poolTasks, adapted);
        filtered = TaskInfoListOps.applySorting(filtered, adapted);

        List<PortalListGroup> groups = new ArrayList<>();
        if (request.groupBy() != null && !request.groupBy().isBlank()) {
            groups.addAll(TaskInfoListOps.groupsOf(filtered, request.groupBy()));
        }
        List<TaskInfo> paged = new ArrayList<>(
                TaskInfoListOps.pageOf(filtered, request.page(), request.size()));
        requestIdEnricher.enrichTaskRequestIds(paged);
        EngineTaskMapper.clearTaskVariablesForList(paged);

        ListQuerySupport.logIfSlow(log, TaskQueryComponent.TO_CLAIM_LIST_KEY, request.page(), request.size(),
                filtered.size(), started);
        return new PortalListPage<>(ToClaimTaskColumnSpec.columns(), paged, List.copyOf(groups),
                request.page(), request.size(), filtered.size());
    }

    static TaskQueryRequest toTaskQuery(String userId, TodoTaskQueryRequest request) {
        List<ListColumnFilter> filters = new ArrayList<>(request.filters());
        if (!request.priorities().isEmpty()) {
            filters.add(new ListColumnFilter("priority", "in", String.join(",", request.priorities()), null));
        }
        return TaskQueryRequest.builder()
                .userId(userId)
                .page(request.page())
                .size(request.size())
                .filters(filters)
                .sortBy(request.sortField())
                .sortDirection(request.sortDirection())
                .groupBy(request.groupBy())
                .keyword(request.keyword())
                .assignmentTypes(request.assignmentTypes().isEmpty() ? null : request.assignmentTypes())
                .build();
    }

    private PageResponse<TaskInfo> queryMergedTodoPage(
            String userId, TaskQueryRequest adapted, int page, int size, List<PortalListGroup> groupsOut) {
        TaskQueryRequest mineFetch = TaskQueryRequest.builder()
                .userId(userId)
                .page(0)
                .size(size)
                .build();
        List<TaskInfo> mine = mineTaskScanner.scanMineTasks(userId, mineFetch, null, size);
        List<TaskInfo> merged = TodoListUnion.merge(mine, fetchAllClaimPoolTasksPaged(userId));
        maybeEnrichRequestIds(merged, adapted);
        List<TaskInfo> filtered = TaskInfoQueryFilters.apply(merged, adapted);
        filtered = TaskInfoListOps.applySorting(filtered, adapted);
        if (groupsOut != null && adapted.getGroupBy() != null && !adapted.getGroupBy().isBlank()) {
            groupsOut.addAll(TaskInfoListOps.groupsOf(filtered, adapted.getGroupBy()));
        }
        List<TaskInfo> paged = new ArrayList<>(TaskInfoListOps.pageOf(filtered, page, size));
        requestIdEnricher.enrichTaskRequestIds(paged);
        EngineTaskMapper.clearTaskVariablesForList(paged);
        return PageResponse.of(paged, page, size, filtered.size());
    }

    private List<TaskInfo> fetchAllClaimPoolTasksPaged(String userId) {
        String portalUsername = SecurityContextUtils.getCurrentUsername().orElse(null);
        List<TaskInfo> out = new ArrayList<>();
        final int batch = 500;
        for (int p = 0; ; p++) {
            Map<String, Object> page = BuRolePoolTasks.requireEnginePage(
                    workflowEngineClient.getUserClaimPoolTasks(userId, p, batch), p);
            List<Map<String, Object>> tasks = WorkflowEnginePayloadHelper.taskListFromPayload(page);
            if (tasks == null || tasks.isEmpty()) {
                break;
            }
            List<TaskInfo> converted = new ArrayList<>(tasks.size());
            for (Map<String, Object> taskMap : tasks) {
                converted.add(EngineTaskMapper.convertMapToTaskInfo(taskMap));
            }
            out.addAll(BuRolePoolTasks.retainClaimPoolTasks(converted));
            if (tasks.size() < batch) {
                break;
            }
        }
        Set<String> withdrawn = WithdrawnProcessIds.of(processInstanceRepository, out.stream()
                .map(TaskInfo::getProcessInstanceId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet()));
        List<TaskInfo> visible = out.stream()
                .filter(t -> t.getProcessInstanceId() == null || !withdrawn.contains(t.getProcessInstanceId()))
                .collect(Collectors.toCollection(ArrayList::new));
        visible = workspaceTaskFilter.filterFixedBuRoleTasksForActiveWorkspace(visible, userId);
        taskPermissionEvaluator.annotateClaimState(visible, userId, portalUsername);
        claimForceUnclaimAnnotator.annotate(visible, userId);
        return visible;
    }

    private void maybeEnrichRequestIds(List<TaskInfo> tasks, TaskQueryRequest request) {
        if (TaskInfoQueryFilters.needsRequestIdEnrichment(request)) {
            requestIdEnricher.enrichTaskRequestIds(tasks);
        }
    }
}
