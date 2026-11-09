package com.workflow.component;

import com.workflow.client.AdminCenterClient;
import com.workflow.client.PortalAssignmentEventClient;
import com.workflow.client.PortalAssignmentEventClient.PoolMembershipRelease;
import com.workflow.component.ClaimPoolTaskLocator.PoolTask;
import com.workflow.dto.request.ClaimPoolResyncRequest;
import com.workflow.dto.response.ClaimPoolResyncResult;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import com.workflow.exception.AdminCenterUnavailableException;
import com.workflow.repository.EngineActorIdentities;
import com.workflow.repository.ExtendedTaskInfoRepository;
import com.workflow.util.MiRoleRowProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps BU + Role claim pools on the current UBR holders instead of the holders at task creation (platform access
 * model §7.3, WP8b-2): adds new holders as candidates and removes people who no longer hold the BU + Role. A holder
 * who lost the BU + Role of a task they claimed has the task released back to the pool (D-40); the Portal records
 * that in the change history. Someone who holds the task without being in its pool (a manager's transfer, a
 * reassignment outside the pool) keeps it: only pool membership can be lost.
 *
 * <p>Triggers: Admin Center after each UBR change ({@code POST /api/v1/internal/claim-pools/resync}), the periodic
 * full pass that also covers changes made outside Admin Center ({@link ClaimPoolResyncScheduler}, D-37), and the
 * orphan repair on read paths. A holder is released only while the task still has the holder the pass read
 * ({@link TaskHandover}), so triggers overlapping on two replicas release it and report it once; the pass that finds
 * the holder changed leaves the task to the next pass. A holder stored by username (a task from before the platform
 * assigned by id) is judged, and reported, as its user, by {@link EngineActorIdentities}' rule.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimPoolMembershipSync {

    /** What a resync did to one task. */
    public enum Outcome { UNCHANGED, UPDATED, RESTORED, SKIPPED, NOT_A_POOL }

    /**
     * Single-task resyncs (each release of a pool task, "Unclaim All" releasing one task after another) share their
     * holder lookups for this long, so a batch asks Admin Center once per BU + Role instead of once per task. Passes
     * (Admin push, periodic resync, read-path repair) always read fresh holders.
     */
    static final long RECENT_HOLDERS_MS = 30_000L;

    private final TaskService taskService;
    private final AdminCenterClient adminCenterClient;
    private final ExtendedTaskInfoRepository extendedTaskInfoRepository;
    private final ClaimPoolTaskLocator claimPoolTaskLocator;
    private final PortalAssignmentEventClient portalAssignmentEventClient;
    private final TaskHandover taskHandover;
    private final EngineActorIdentities engineActorIdentities;
    private volatile Holders recentHolders;
    private volatile long recentHoldersAt;

    /**
     * Pools Admin Center names after a UBR change.
     *
     * @throws AdminCenterUnavailableException when the holders cannot be read; nothing after the failure is changed
     */
    public ClaimPoolResyncResult resync(List<ClaimPoolResyncRequest.Pool> pools) {
        recentHolders = null; // holders just changed: single-task resyncs must not reuse what they read before
        return apply(claimPoolTaskLocator.of(pools));
    }

    /** Every open BU + Role pool task (D-37). */
    public ClaimPoolResyncResult resyncAll() {
        recentHolders = null;
        return apply(claimPoolTaskLocator.all());
    }

    /** One task by id, re-read so a claim or unclaim just made is seen. */
    public Outcome resyncTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            return Outcome.NOT_A_POOL;
        }
        Optional<PoolTask> poolTask = claimPoolTaskLocator.of(task);
        if (poolTask.isEmpty()) {
            return Outcome.NOT_A_POOL;
        }
        List<PoolMembershipRelease> releases = new ArrayList<>();
        Outcome outcome = applyTo(poolTask.get(), recentHolders(), releases);
        portalAssignmentEventClient.reportPoolMembershipReleases(releases);
        return outcome;
    }

    private Holders recentHolders() {
        long now = System.currentTimeMillis();
        Holders holders = recentHolders;
        if (holders == null || now - recentHoldersAt > RECENT_HOLDERS_MS) {
            holders = new Holders(adminCenterClient);
            recentHolders = holders;
            recentHoldersAt = now;
        }
        return holders;
    }

    /**
     * One orphan-repair pass over unclaimed tasks, sharing one holder lookup. Returns the ids whose empty pool was
     * filled again, so the repair can clear their assignment-failure trace.
     */
    public List<String> resyncUnclaimed(List<Task> unclaimed) {
        Holders holders = new Holders(adminCenterClient);
        List<PoolMembershipRelease> releases = new ArrayList<>();
        List<String> restored = new ArrayList<>();
        try {
            for (Task task : unclaimed) {
                try {
                    // MI rows assigned by role are repaired by the MI orphan repair; any other pool task here.
                    Optional<PoolTask> poolTask = claimPoolTaskLocator.of(task).filter(p -> p.miRow() == null
                            || MiRoleRowProperties.parse(p.miRow().getExtendedProperties()).isEmpty());
                    if (poolTask.isPresent() && applyTo(poolTask.get(), holders, releases) == Outcome.RESTORED) {
                        restored.add(task.getId());
                    }
                } catch (FlowableException e) {
                    log.warn("Claim-pool resync of task {} failed: {}", task.getId(), e.getMessage());
                } catch (IllegalStateException e) {
                    log.error("Claim-pool resync of task {} skipped, its MI row is unreadable: {}",
                            task.getId(), e.getMessage());
                }
            }
        } finally {
            portalAssignmentEventClient.reportPoolMembershipReleases(releases);
        }
        return restored;
    }

    private ClaimPoolResyncResult apply(List<PoolTask> poolTasks) {
        Holders holders = new Holders(adminCenterClient);
        List<PoolMembershipRelease> releases = new ArrayList<>();
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        try {
            for (PoolTask poolTask : poolTasks) {
                try {
                    Outcome outcome = applyTo(poolTask, holders, releases);
                    if (outcome == Outcome.UPDATED || outcome == Outcome.RESTORED) {
                        updated++;
                    } else if (outcome == Outcome.SKIPPED) {
                        skipped++;
                    }
                } catch (FlowableException e) {
                    // The task changed under us (completed, deleted); the next pass sees its new state.
                    failed++;
                    log.warn("Claim-pool resync of task {} failed: {}", poolTask.task().getId(), e.getMessage());
                }
            }
        } finally {
            portalAssignmentEventClient.reportPoolMembershipReleases(releases);
        }
        ClaimPoolResyncResult result = new ClaimPoolResyncResult(
                poolTasks.size(), updated, releases.size(), skipped, failed);
        log.info("Claim-pool resync: {}", result);
        return result;
    }

    private Outcome applyTo(PoolTask poolTask, Holders holders, List<PoolMembershipRelease> releases) {
        Task task = poolTask.task();
        Optional<Set<String>> expected = holders.of(poolTask.businessUnitCode(), poolTask.roleCodes());
        if (expected.isEmpty()) {
            log.warn("Claim pool {}/{} of task {} is not admitted by Admin Center; left as it is",
                    poolTask.businessUnitCode(), poolTask.roleCodes(), task.getId());
            return Outcome.SKIPPED;
        }
        Set<String> wanted = expected.get();
        Set<String> current = candidateUsers(task.getId());
        Optional<String> lostHolder = holderWhoLostMembership(task, current, wanted);
        if (lostHolder.isPresent() && !release(poolTask, lostHolder.get(), wanted, releases)) {
            // Another pass released or moved the task meanwhile, so what this pass read of it is stale: the
            // candidates and the MI row are left to the next pass, which reads them again.
            return Outcome.SKIPPED;
        }
        boolean released = lostHolder.isPresent();
        boolean changed = released;
        for (String member : current) {
            if (!wanted.contains(member)) {
                taskService.deleteCandidateUser(task.getId(), member);
                changed = true;
            }
        }
        for (String member : wanted) {
            if (!current.contains(member)) {
                taskService.addCandidateUser(task.getId(), member);
                changed = true;
            }
        }
        if (!changed) {
            return Outcome.UNCHANGED;
        }
        if (!released) {
            rememberPool(poolTask.miRow(), wanted); // a release saved the row together with the unclaim
        }
        log.info("Claim pool of task {} now follows {}/{}: {} candidates{}", task.getId(),
                poolTask.businessUnitCode(), poolTask.roleCodes(), wanted.size(),
                released ? ", holder released" : "");
        return current.isEmpty() && !wanted.isEmpty() ? Outcome.RESTORED : Outcome.UPDATED;
    }

    /**
     * The user id of the task's holder when they are in its pool but no longer hold its BU + Role; empty when the task
     * is unclaimed, its holder still holds the BU + Role, or holds the task without being in the pool. The holder and
     * the candidates are compared as users, so a holder or candidate stored by username counts as its user.
     */
    private Optional<String> holderWhoLostMembership(Task task, Set<String> current, Set<String> wanted) {
        String assignee = StringUtils.hasText(task.getAssignee()) ? task.getAssignee().trim() : null;
        if (assignee == null || wanted.contains(assignee)) {
            return Optional.empty();
        }
        Set<String> actors = new LinkedHashSet<>(current);
        actors.add(assignee);
        Map<String, String> userIds = engineActorIdentities.userIds(actors);
        String holderId = userIds.get(assignee);
        boolean inPool = current.stream().map(userIds::get).anyMatch(holderId::equals);
        return inPool && !wanted.contains(holderId) ? Optional.of(holderId) : Optional.empty();
    }

    /**
     * Unclaims the task and, in the same transaction, the MI row with its new pool, so the two cannot disagree.
     * False when the task no longer has the holder this pass read, or its MI row changed since: someone else released,
     * moved or delegated it meanwhile.
     */
    private boolean release(PoolTask poolTask, String holderId, Set<String> wanted,
                            List<PoolMembershipRelease> releases) {
        Task task = poolTask.task();
        ExtendedTaskInfo row = poolTask.miRow();
        String[] endedDelegate = new String[1];
        boolean released = taskHandover.ifStillHeldBy(task.getId(), task.getAssignee(), () -> {
            taskService.unclaim(task.getId());
            if (row != null) {
                // The holder's single-task delegation ends with the hold (D-59).
                endedDelegate[0] = row.endDelegationUnlessSameHolder(null);
                if (row.isClaimed()) {
                    row.unclaimTask();
                }
                rememberPool(row, wanted);
            }
        });
        if (released) {
            releases.add(new PoolMembershipRelease(task.getId(), task.getProcessInstanceId(), holderId,
                    poolTask.businessUnitCode(), poolTask.roleCodes(), Instant.now(), endedDelegate[0]));
        } else {
            log.info("Task {} not released from {}: its holder changed meanwhile", task.getId(), task.getAssignee());
        }
        return released;
    }

    /**
     * MI rows keep their candidate list in {@code assignment_target}, as {@code TaskClaimSupport} does. Flushed at
     * once, so a row changed since this pass read it fails here, inside a release's transaction ({@link TaskHandover}).
     */
    private void rememberPool(ExtendedTaskInfo row, Set<String> wanted) {
        if (row == null) {
            return;
        }
        row.setAssignmentType(AssignmentType.CANDIDATE_USERS);
        row.setAssignmentTarget(String.join(",", wanted));
        extendedTaskInfoRepository.saveAndFlush(row);
    }

    private Set<String> candidateUsers(String taskId) {
        Set<String> ids = new LinkedHashSet<>();
        for (IdentityLink link : taskService.getIdentityLinksForTask(taskId)) {
            if ("candidate".equals(link.getType()) && StringUtils.hasText(link.getUserId())) {
                ids.add(link.getUserId().trim());
            }
        }
        return ids;
    }

    /** UBR holders per BU + Role code, read once per pass. */
    private static final class Holders {
        private final AdminCenterClient adminCenterClient;
        private final Map<String, Optional<List<String>>> byPool = new ConcurrentHashMap<>();

        Holders(AdminCenterClient adminCenterClient) {
            this.adminCenterClient = adminCenterClient;
        }

        /** Union of the roles' holders, or empty when the BU does not admit one of the roles. */
        Optional<Set<String>> of(String businessUnitCode, List<String> roleCodes) {
            Set<String> union = new LinkedHashSet<>();
            for (String role : roleCodes) {
                Optional<List<String>> users = byPool.computeIfAbsent(businessUnitCode + '|' + role,
                        key -> adminCenterClient.isEligibleRole(businessUnitCode, role)
                                ? Optional.of(adminCenterClient.getUsersByBusinessUnitAndRole(businessUnitCode, role))
                                : Optional.empty());
                if (users.isEmpty()) {
                    return Optional.empty();
                }
                users.get().stream().filter(StringUtils::hasText).map(String::trim).forEach(union::add);
            }
            return Optional.of(union);
        }
    }
}
