package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.dto.TaskInfo;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.util.EngineTaskPushdown;
import com.portal.util.RequestContextInheritanceUtils;
import com.portal.util.WorkflowEnginePayloadHelper;
import com.platform.security.util.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Engine window / full-scan fetches for Mine, plus initiator fallback when the user-task query is empty.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EngineVisibleTaskFetcher {

    private final WorkflowEngineClient workflowEngineClient;
    private final ProcessInstanceRepository processInstanceRepository;
    private final WorkspaceTaskFilterComponent workspaceTaskFilter;

    @Lazy
    @Autowired
    private TaskProcessComponent taskProcessComponent;

    record EngineWindowResult(List<TaskInfo> tasks, long engineTotal) {
    }

    EngineWindowResult fetchEngineTaskPageWindow(
            String userId, List<String> assignmentTypes, int page, int size,
            EngineTaskPushdown.Criteria push) {
        List<TaskInfo> engineTasks = new ArrayList<>();
        long engineTotal = 0L;
        EngineTaskPushdown.Criteria safePush = push != null ? push : EngineTaskPushdown.Criteria.empty();
        try {
            long tw0 = System.nanoTime();
            List<String> groupIds = workspaceTaskFilter.getUserVirtualGroups(userId);
            groupIds = workspaceTaskFilter.filterVirtualGroupsForActiveWorkspace(userId, groupIds);
            long twVg = System.nanoTime();
            boolean includeGroups = assignmentTypes == null || assignmentTypes.isEmpty()
                    || assignmentTypes.contains("VIRTUAL_GROUP");
            Optional<Map<String, Object>> result = includeGroups
                    ? workflowEngineClient.getUserAllVisibleTasks(
                            userId, groupIds, Collections.emptyList(), page, size, safePush)
                    : workflowEngineClient.getUserAllVisibleTasks(
                            userId, Collections.emptyList(), Collections.emptyList(), page, size, safePush);
            log.info("[PERF] engineWindow vgroups={}ms engineHttp={}ms groupCount={} includeGroups={} push={}",
                    (twVg - tw0) / 1_000_000L, (System.nanoTime() - twVg) / 1_000_000L,
                    groupIds != null ? groupIds.size() : 0, includeGroups, safePush.hasAny());
            if (result.isEmpty()) {
                throw new IllegalStateException("Failed to query tasks from Flowable: engine returned no payload");
            }
            Map<String, Object> responseBody = result.get();
            engineTotal = EngineTaskMapper.extractEngineTotalCount(responseBody);
            List<Map<String, Object>> tasks = WorkflowEnginePayloadHelper.taskListFromPayload(responseBody);
            if (tasks != null) {
                for (Map<String, Object> taskMap : tasks) {
                    engineTasks.add(EngineTaskMapper.convertMapToTaskInfo(taskMap));
                }
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to query tasks from Flowable: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to query tasks from Flowable: " + e.getMessage(), e);
        }
        if (engineTasks.isEmpty() && !safePush.hasFilterFragments()) {
            mergeTasksFromRunningProcessInstancesForUser(userId, engineTasks);
        }
        return new EngineWindowResult(engineTasks, engineTotal);
    }

    List<TaskInfo> fetchAllEngineTasksPaged(
            String userId, List<String> assignmentTypes, EngineTaskPushdown.Criteria push) {
        List<String> groupIds = workspaceTaskFilter.getUserVirtualGroups(userId);
        groupIds = workspaceTaskFilter.filterVirtualGroupsForActiveWorkspace(userId, groupIds);
        boolean includeGroups = assignmentTypes == null || assignmentTypes.isEmpty()
                || assignmentTypes.contains("VIRTUAL_GROUP");
        EngineTaskPushdown.Criteria safePush = push != null ? push : EngineTaskPushdown.Criteria.empty();
        List<TaskInfo> out = new ArrayList<>();
        final int batch = 500;
        for (int p = 0; ; p++) {
            Optional<Map<String, Object>> result = includeGroups
                    ? workflowEngineClient.getUserAllVisibleTasks(
                            userId, groupIds, Collections.emptyList(), p, batch, safePush)
                    : workflowEngineClient.getUserAllVisibleTasks(
                            userId, Collections.emptyList(), Collections.emptyList(), p, batch, safePush);
            if (result.isEmpty()) {
                throw new IllegalStateException(
                        "Failed to query tasks from Flowable during full scan page " + p);
            }
            List<Map<String, Object>> tasks = WorkflowEnginePayloadHelper.taskListFromPayload(result.get());
            if (tasks == null || tasks.isEmpty()) {
                break;
            }
            for (Map<String, Object> taskMap : tasks) {
                out.add(EngineTaskMapper.convertMapToTaskInfo(taskMap));
            }
            if (tasks.size() < batch) {
                break;
            }
        }
        if (out.isEmpty() && !safePush.hasAny()) {
            mergeTasksFromRunningProcessInstancesForUser(userId, out);
        }
        return out;
    }

    EngineWindowResult fetchWindowWithRequestContext(
            String userId, List<String> assignmentTypes, int page, int size,
            EngineTaskPushdown.Criteria push) {
        SecurityContext ctx = SecurityContextHolder.getContext();
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return RequestContextInheritanceUtils.runWithInheritedRequestAndSecurity(ctx, attrs,
                () -> fetchEngineTaskPageWindow(userId, assignmentTypes, page, size, push));
    }

    List<TaskInfo> fetchAllWithRequestContext(
            String userId, List<String> assignmentTypes, EngineTaskPushdown.Criteria push) {
        SecurityContext ctx = SecurityContextHolder.getContext();
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return RequestContextInheritanceUtils.runWithInheritedRequestAndSecurity(ctx, attrs,
                () -> fetchAllEngineTasksPaged(userId, assignmentTypes, push));
    }

    /**
     * When /api/v1/tasks?userId= aggregation is empty, re-query engine tasks by processInstanceId
     * for RUNNING instances where the current user is the initiator in the portal DB.
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
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
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
                    TaskInfo taskInfo = EngineTaskMapper.convertMapToTaskInfo(taskMap);
                    if (taskInfo.getTaskId() == null || seen.contains(taskInfo.getTaskId())) {
                        continue;
                    }
                    if (taskProcessComponent != null
                            && !taskProcessComponent.canProcessTask(taskInfo, userId, portalUsername)) {
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
}
