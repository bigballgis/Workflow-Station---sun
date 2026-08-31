package com.portal.component;

import com.portal.dto.ClaimBatchRequest;
import com.portal.dto.ClaimBatchResponse;
import com.portal.dto.TaskInfo;
import com.portal.exception.PortalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Unclaim All: one HTTP slice of at most {@link ClaimBatchComponent#BATCH_LIMIT} To Do rows
 * the current user holds (Mine ∪ claim-pool). Does not force-unclaim a colleague's hold.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnclaimBatchComponent {

    private final TaskQueryComponent taskQueryComponent;
    private final TaskProcessComponent taskProcessComponent;

    public ClaimBatchResponse unclaimNextBatch(String userId, String portalUsername, ClaimBatchRequest request) {
        if (userId == null || userId.isBlank()) {
            throw new PortalException("401", "Authenticated user id is required");
        }
        taskQueryComponent.invalidateMineTaskListCache();
        List<TaskInfo> heldByMe = ClaimBatchComponent.filterEligible(
                taskQueryComponent.listMergedTodoTasks(userId),
                task -> task.isClaimedByCurrentUser(),
                request);
        int take = Math.min(ClaimBatchComponent.BATCH_LIMIT, heldByMe.size());
        int unclaimed = 0;
        int skipped = 0;
        int failed = 0;
        List<String> attempted = new ArrayList<>(take);
        for (int i = 0; i < take; i++) {
            TaskInfo task = heldByMe.get(i);
            String taskId = task.getTaskId();
            attempted.add(taskId);
            int outcome = unclaimOne(task, userId, portalUsername);
            if (outcome == 1) {
                unclaimed++;
            } else if (outcome == 0) {
                skipped++;
            } else {
                failed++;
            }
        }
        int remaining = heldByMe.size() - take;
        return new ClaimBatchResponse(unclaimed, skipped, failed, remaining, attempted);
    }

    /** 1 unclaimed, 0 skipped, -1 failed. */
    private int unclaimOne(TaskInfo task, String userId, String portalUsername) {
        String taskId = task.getTaskId();
        String assignmentType = task.getAssignmentType();
        String assignee = task.getAssignee();
        if (assignmentType == null || assignmentType.isBlank() || assignee == null || assignee.isBlank()) {
            log.warn("Unclaim All skipped task {}: missing assignmentType or assignee", taskId);
            return -1;
        }
        try {
            taskProcessComponent.unclaimTask(taskId, userId, assignmentType, assignee, portalUsername);
            return 1;
        } catch (PortalException e) {
            if ("403".equals(e.getCode()) || "409".equals(e.getCode())) {
                log.info("Unclaim All skipped task {}: {}", taskId, e.getMessage());
                return 0;
            }
            log.warn("Unclaim All failed for task {}: {}", taskId, e.getMessage());
            return -1;
        } catch (RuntimeException e) {
            log.warn("Unclaim All failed for task {}: {}", taskId, e.getMessage());
            return -1;
        }
    }
}
