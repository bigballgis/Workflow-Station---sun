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
import java.util.List;
import java.util.Set;

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
        Set<String> exclude = boundedExclude(request);
        List<TaskInfo> claimable = new ArrayList<>();
        for (TaskInfo task : taskQueryComponent.listClaimPoolTasks(userId)) {
            if (task != null && task.isClaimable() && task.getTaskId() != null && !exclude.contains(task.getTaskId())) {
                claimable.add(task);
            }
        }
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
        List<String> raw = request == null || request.excludeTaskIds() == null
                ? List.of() : request.excludeTaskIds();
        if (raw.size() > ClaimBatchRequest.MAX_EXCLUDE_IDS) {
            throw new PortalException("400", "excludeTaskIds exceeds " + ClaimBatchRequest.MAX_EXCLUDE_IDS);
        }
        return new HashSet<>(raw);
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
