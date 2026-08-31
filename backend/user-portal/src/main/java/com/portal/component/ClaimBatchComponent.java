package com.portal.component;

import com.portal.dto.ClaimBatchRequest;
import com.portal.dto.ClaimBatchResponse;
import com.portal.dto.TaskInfo;
import com.portal.exception.PortalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Claim All: one HTTP slice of at most {@link #BATCH_LIMIT} currently claimable pool tasks.
 * The portal UI loops until {@code remaining == 0}. Writes stay on this thread (each
 * {@link TaskProcessComponent#claimTask} is its own transaction).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimBatchComponent {

    public static final int BATCH_LIMIT = 100;

    private final TaskQueryComponent taskQueryComponent;
    private final TaskProcessComponent taskProcessComponent;

    public ClaimBatchResponse claimNextBatch(String userId, String portalUsername, ClaimBatchRequest request) {
        if (userId == null || userId.isBlank()) {
            throw new PortalException("401", "Authenticated user id is required");
        }
        List<TaskInfo> claimable = filterEligible(
                taskQueryComponent.listClaimPoolTasks(userId),
                task -> task.isClaimable(),
                request);
        int take = Math.min(BATCH_LIMIT, claimable.size());
        int claimed = 0;
        int skipped = 0;
        int failed = 0;
        List<String> attempted = new ArrayList<>(take);
        for (int i = 0; i < take; i++) {
            String taskId = claimable.get(i).getTaskId();
            attempted.add(taskId);
            int outcome = claimOne(taskId, userId, portalUsername);
            if (outcome == 1) {
                claimed++;
            } else if (outcome == 0) {
                skipped++;
            } else {
                failed++;
            }
        }
        int remaining = claimable.size() - take;
        return new ClaimBatchResponse(claimed, skipped, failed, remaining, attempted);
    }

    static Set<String> boundedExclude(ClaimBatchRequest request) {
        return boundedIds(
                request == null ? null : request.excludeTaskIds(),
                ClaimBatchRequest.MAX_EXCLUDE_IDS,
                "excludeTaskIds");
    }

    static Set<String> boundedInclude(ClaimBatchRequest request) {
        return boundedIds(
                request == null ? null : request.includeTaskIds(),
                ClaimBatchRequest.MAX_INCLUDE_IDS,
                "includeTaskIds");
    }

    static List<TaskInfo> filterEligible(
            List<TaskInfo> source, Predicate<TaskInfo> keep, ClaimBatchRequest request) {
        Set<String> exclude = boundedExclude(request);
        Set<String> include = boundedInclude(request);
        Map<String, TaskInfo> byId = new LinkedHashMap<>();
        List<TaskInfo> rows = source == null ? List.of() : source;
        for (TaskInfo task : rows) {
            if (task == null || task.getTaskId() == null || !keep.test(task)) {
                continue;
            }
            if (exclude.contains(task.getTaskId())) {
                continue;
            }
            if (!include.isEmpty() && !include.contains(task.getTaskId())) {
                continue;
            }
            byId.putIfAbsent(task.getTaskId(), task);
        }
        if (include.isEmpty()) {
            return new ArrayList<>(byId.values());
        }
        return orderByInclude(byId, request.includeTaskIds());
    }

    private static List<TaskInfo> orderByInclude(Map<String, TaskInfo> byId, List<String> includeOrder) {
        List<TaskInfo> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String id : includeOrder) {
            if (id == null || !seen.add(id)) {
                continue;
            }
            TaskInfo task = byId.get(id);
            if (task != null) {
                out.add(task);
            }
        }
        return out;
    }

    private static Set<String> boundedIds(List<String> raw, int max, String fieldName) {
        List<String> list = raw == null ? List.of() : raw;
        if (list.size() > max) {
            throw new PortalException("400", fieldName + " exceeds " + max);
        }
        return new HashSet<>(list);
    }

    /** 1 claimed, 0 skipped (not allowed / already held), -1 failed. */
    private int claimOne(String taskId, String userId, String portalUsername) {
        try {
            taskProcessComponent.claimTask(taskId, userId, portalUsername);
            return 1;
        } catch (PortalException e) {
            if ("403".equals(e.getCode()) || "409".equals(e.getCode())) {
                log.info("Claim All skipped task {}: {}", taskId, e.getMessage());
                return 0;
            }
            log.warn("Claim All failed for task {}: {}", taskId, e.getMessage());
            return -1;
        } catch (RuntimeException e) {
            log.warn("Claim All failed for task {}: {}", taskId, e.getMessage());
            return -1;
        }
    }
}
