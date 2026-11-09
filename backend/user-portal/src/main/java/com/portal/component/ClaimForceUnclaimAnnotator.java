package com.portal.component;

import com.platform.security.service.LostEntryTaskPolicy;
import com.platform.security.service.LostEntryTaskPolicy.PoolKind;
import com.platform.security.service.LostEntryTaskPolicy.TaskHolding;
import com.portal.client.AdminCenterClient;
import com.portal.dto.TaskInfo;
import com.portal.util.BuRolePoolTasks;
import com.portal.util.ClaimPoolTaskIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Sets the release / reassign / take-over flags of pool tasks after the claim flags (platform access model §7.3).
 *
 * <ul>
 *   <li>While the holder has Portal entry: the BU Approver or a Leader of the BU + Role (Admin Center, one call per
 *       list; BU + Role pools only).</li>
 *   <li>Once the holder lost it: any pool member with entry, for BU + Role pools and lists of named people alike
 *       (D-07, D-17), judged by {@link LostEntryTaskPolicy}. The engine checks the same rule again when the action is
 *       sent. Tasks of legacy virtual groups get no such actions here (D-58: the platform no longer assigns any).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimForceUnclaimAnnotator {

    private final AdminCenterClient adminCenterClient;
    private final LostEntryTaskPolicy lostEntryTaskPolicy;

    public void annotate(TaskInfo task, String userId) {
        if (task != null) {
            annotate(List.of(task), userId);
        }
    }

    public void annotate(List<TaskInfo> tasks, String userId) {
        if (tasks == null || tasks.isEmpty() || userId == null || userId.isBlank()) {
            return;
        }
        Map<String, Boolean> approverOrLeader = evaluateAdminRule(tasks, userId);
        Map<TaskInfo, TaskHolding> holdings = new HashMap<>();
        Set<String> people = new LinkedHashSet<>();
        people.add(userId);
        for (TaskInfo task : tasks) {
            TaskHolding holding = holdingOf(task);
            holdings.put(task, holding);
            people.addAll(holding.people());
        }
        Set<String> withEntry = lostEntryTaskPolicy.withPortalEntry(people);
        for (TaskInfo task : tasks) {
            TaskHolding holding = holdings.get(task);
            boolean takeOver = LostEntryTaskPolicy.mayTakeOver(userId, holding, withEntry);
            boolean authorized = takeOver || Boolean.TRUE.equals(approverOrLeader.get(task.getTaskId()));
            task.setAssigneeLostPortalEntry(holding.held() && !withEntry.contains(holding.assigneeId()));
            task.setCanTakeOver(takeOver);
            task.setCanReassign(authorized);
            task.setCanForceUnclaim(authorized && holding.held() && !task.isClaimedByCurrentUser());
            task.setReassignTargetUserIds(new ArrayList<>(LostEntryTaskPolicy.takeoverTargets(holding, withEntry)));
        }
    }

    /**
     * Of the engine's claim-pool page (tasks where the user is a candidate, held or not), the ones outside BU + Role
     * pools that someone holds after losing Portal entry: a list of named people whose other members may now take the
     * task over (D-17). Other members never saw such a held task before.
     */
    public List<TaskInfo> heldByUsersWithoutEntry(List<TaskInfo> claimPoolPage, String userId) {
        List<TaskInfo> held = claimPoolPage.stream()
                .filter(t -> !BuRolePoolTasks.isClaimPoolTask(t) && BuRolePoolTasks.isHeld(t))
                .filter(t -> !t.getAssignee().equals(userId))
                .toList();
        if (held.isEmpty()) {
            return List.of();
        }
        Set<String> withEntry = lostEntryTaskPolicy.withPortalEntry(held.stream().map(TaskInfo::getAssignee).toList());
        return held.stream().filter(t -> !withEntry.contains(t.getAssignee())).toList();
    }

    public boolean canForceUnclaim(TaskInfo task, String userId) {
        if (task == null || userId == null || userId.isBlank()) {
            return false;
        }
        annotate(task, userId);
        return task.isCanForceUnclaim();
    }

    public boolean canReassign(TaskInfo task, String userId) {
        if (task == null || userId == null || userId.isBlank()) {
            return false;
        }
        annotate(task, userId);
        return task.isCanReassign();
    }

    /** The Admin Center rule (BU Approver, Leader) for BU + Role pool tasks; fails closed. */
    private Map<String, Boolean> evaluateAdminRule(List<TaskInfo> tasks, String userId) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (TaskInfo task : tasks) {
            if (task.getTaskId() != null && BuRolePoolTasks.isClaimPoolTask(task)) {
                Map<String, Object> item = new HashMap<>();
                item.put("taskId", task.getTaskId());
                item.put("businessUnitId", ClaimPoolTaskIdentity.businessUnit(task));
                item.put("roleIds", ClaimPoolTaskIdentity.roleIds(task));
                items.add(item);
            }
        }
        return items.isEmpty() ? Map.of() : adminCenterClient.evaluateForceUnclaim(userId, items);
    }

    /**
     * The task as the policy sees it: its pool is its candidate users (D-58: no legacy virtual-group members). The pool
     * kind comes from the engine ({@code poolKind}); a task without one (not read from the engine's runtime lists) gets
     * no lost-entry actions, which the engine would judge anyway.
     */
    private static TaskHolding holdingOf(TaskInfo task) {
        PoolKind kind = task.getPoolKind() == null ? PoolKind.NONE : PoolKind.valueOf(task.getPoolKind());
        Set<String> members = new LinkedHashSet<>();
        if (task.getCandidateUserIds() != null) {
            task.getCandidateUserIds().stream().filter(Objects::nonNull).forEach(members::add);
        }
        return new TaskHolding(task.getAssignee(), kind, members);
    }
}
