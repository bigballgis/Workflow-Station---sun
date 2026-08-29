package com.portal.component;

import com.portal.client.AdminCenterClient;
import com.portal.dto.TaskInfo;
import com.portal.util.BuRolePoolTasks;
import com.portal.util.ClaimPoolTaskIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sets {@link TaskInfo#canForceUnclaim} after claim flags. One admin-center call per list.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimForceUnclaimAnnotator {

    private final AdminCenterClient adminCenterClient;

    public void annotate(TaskInfo task, String userId) {
        if (task != null) {
            annotate(List.of(task), userId);
        }
    }

    public void annotate(List<TaskInfo> tasks, String userId) {
        if (tasks == null || tasks.isEmpty() || userId == null || userId.isBlank()) {
            return;
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (TaskInfo task : tasks) {
            if (!needsEvaluate(task)) {
                task.setCanForceUnclaim(false);
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("taskId", task.getTaskId());
            item.put("businessUnitId", ClaimPoolTaskIdentity.businessUnit(task));
            item.put("roleIds", ClaimPoolTaskIdentity.roleIds(task));
            items.add(item);
        }
        if (items.isEmpty()) {
            return;
        }
        Map<String, Boolean> flags = adminCenterClient.evaluateForceUnclaim(userId, items);
        for (TaskInfo task : tasks) {
            if (!needsEvaluate(task)) {
                continue;
            }
            task.setCanForceUnclaim(Boolean.TRUE.equals(flags.get(task.getTaskId())));
        }
    }

    public boolean canForceUnclaim(TaskInfo task, String userId) {
        if (task == null || userId == null || userId.isBlank()) {
            return false;
        }
        annotate(task, userId);
        return task.isCanForceUnclaim();
    }

    private static boolean needsEvaluate(TaskInfo task) {
        return task != null
                && task.getTaskId() != null
                && BuRolePoolTasks.isClaimPoolTask(task)
                && BuRolePoolTasks.isHeld(task)
                && !task.isClaimedByCurrentUser();
    }
}
