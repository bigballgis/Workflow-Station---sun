package com.portal.util;

import com.portal.dto.TaskInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Identifies the one pool the portal exposes as a claim pool ("Tasks to Claim"): a user task whose
 * BPMN assignment names a business unit + role, so every member of that role competes for the same
 * request and must Hold it before editing.
 *
 * <p>The accepted {@code assigneeType} values are the ones workflow-engine maps to
 * {@code AssigneeType.BU_ROLE} (see {@code com.workflow.enums.AssigneeType#fromLegacyCode}). Keep
 * {@link #BU_ROLE_ASSIGNEE_TYPES} in lockstep with that switch; {@link BuRolePoolTasksTest} is the
 * tripwire.
 *
 * <p>Deploy does <strong>not</strong> rewrite in-flight Flowable rows. {@link #staysOnTodoList}
 * is the upgrade rule for My To Do.
 */
public final class BuRolePoolTasks {

    private static final Set<String> BU_ROLE_ASSIGNEE_TYPES =
            Set.of("BU_ROLE", "FIXED_BU_ROLE", "FIXEDDEPT", "FIXED_DEPT");

    private BuRolePoolTasks() {
    }

    public static boolean isClaimPoolAssigneeType(String bpmnAssigneeType) {
        if (bpmnAssigneeType == null || bpmnAssigneeType.isBlank()) {
            return false;
        }
        return BU_ROLE_ASSIGNEE_TYPES.contains(bpmnAssigneeType.trim().toUpperCase(Locale.ROOT));
    }

    public static boolean isClaimPoolTask(TaskInfo task) {
        return task != null && isClaimPoolAssigneeType(task.getBpmnAssigneeType());
    }

    /**
     * Held tasks carry the claimer as Flowable assignee; an empty assignee means the request is
     * still free for anyone in the role.
     */
    public static boolean isHeld(TaskInfo task) {
        return task != null && task.getAssignee() != null && !task.getAssignee().isBlank();
    }

    /**
     * Mine engine query: non-pool tasks stay; pool tasks stay only when already held.
     * Unheld and colleague-held pool rows join To Do via {@code TodoListUnion}, not this predicate.
     */
    public static boolean staysOnTodoList(TaskInfo task) {
        return !isClaimPoolTask(task) || isHeld(task);
    }

    public static List<TaskInfo> retainClaimPoolTasks(List<TaskInfo> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        List<TaskInfo> out = new ArrayList<>();
        for (TaskInfo task : tasks) {
            if (isClaimPoolTask(task)) {
                out.add(task);
            }
        }
        return out;
    }

    /**
     * Distinguishes an engine transport failure (empty Optional) from a real empty page.
     */
    public static Map<String, Object> requireEnginePage(Optional<Map<String, Object>> result, int page) {
        if (result == null || result.isEmpty()) {
            throw new IllegalStateException(
                    "Failed to query claim pool tasks from Flowable at page " + page);
        }
        return result.get();
    }
}
