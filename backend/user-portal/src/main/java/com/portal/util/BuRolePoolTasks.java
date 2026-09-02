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

    /**
     * MI 子任务的角色分派**判不出来自 BPMN 的 assigneeType**：内层 userTask 上写的恒是
     * {@code ELEMENT_VARIABLE}（逐行取 currentItem），真正的「按角色分派」配置在父 SubProcess 上
     * （{@code assigneeMode} / {@code roleField} / {@code buField}）。而 {@code assigneeMode=both}
     * 时还要**逐行**二选一：同一个节点上有的行按人派、有的行按角色派，节点级配置无法回答某一行。
     *
     * <p>所以这里用引擎逐行落地的结果：{@code ExtendedTaskInfo.extendedProperties.assigneeMode=role}
     * 由 {@code MultiInstanceTaskWriter.handleRoleModeAssignment} 在真的走了 BU_ROLE 解析时写入
     * （引擎再经 {@code TaskInfoAssembler} 透出为 {@code miAssigneeMode}）。实测同一节点下
     * 按角色派的行有 {@code role}、按人派的行为空，区分干净。
     *
     * <p>不用 {@code assignmentType=CANDIDATE_USERS} 判：那只是角色解析出多人时的**下游结果**，
     * 角色池恰好只有一人时会落成 {@code USER}，判据会漏。
     */
    public static boolean isMiRoleAssignedTask(TaskInfo task) {
        return task != null
                && task.getMiAssigneeMode() != null
                && "role".equalsIgnoreCase(task.getMiAssigneeMode().trim());
    }

    public static boolean isClaimPoolTask(TaskInfo task) {
        return task != null
                && (isClaimPoolAssigneeType(task.getBpmnAssigneeType()) || isMiRoleAssignedTask(task));
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
