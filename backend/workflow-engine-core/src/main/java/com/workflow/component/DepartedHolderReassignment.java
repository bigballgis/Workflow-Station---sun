package com.workflow.component;

import com.platform.security.service.LostEntryTaskPolicy;
import com.platform.security.service.LostEntryTaskPolicy.TaskHolding;
import com.workflow.client.PortalAssignmentEventClient;
import com.workflow.client.PortalAssignmentEventClient.DepartedReassignment;
import com.workflow.dto.response.DepartedReassignResult;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.exception.AdminCenterUnavailableException;
import com.workflow.repository.EngineActorIdentities;
import com.workflow.repository.ExtendedTaskInfoRepository;
import com.workflow.repository.FlowableRuntimeTaskQueries;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Hands the tasks of people who left to their manager (platform access model §7.3, D-42): a task assigned to someone
 * whose account is now INACTIVE or deleted, a pool task they held while nobody else in the pool has Portal entry, and an
 * unclaimed list of named people who all left. Who receives it, and when a task stays, is
 * {@link LostEntryTaskPolicy}'s rule; this class finds the tasks, moves them and tells the Portal for the change history.
 *
 * <p>Triggers: Admin Center after a user is set INACTIVE or deleted
 * ({@code POST /api/v1/internal/departed-users/tasks/reassign}) and the periodic pass that also covers a missed call
 * ({@link DepartedHolderReassignScheduler}). A task whose people's managers are all unavailable stays where it is and
 * is logged as an error on every pass until someone fixes the manager data or the task.</p>
 *
 * <p>People are judged by user id: a task that holds a username instead (created before the platform assigned by id)
 * is found and judged as its user's, by {@link EngineActorIdentities}' rule. A task moves only while it still has the
 * holder this pass read ({@link TaskHandover}), so passes overlapping on two replicas hand it over and report it once.</p>
 */
@Slf4j
@Component
public class DepartedHolderReassignment {

    private final TaskService taskService;
    private final ExtendedTaskInfoRepository extendedTaskInfoRepository;
    private final FlowableRuntimeTaskQueries runtimeTaskQueries;
    private final TaskHoldingResolver taskHoldingResolver;
    private final LostEntryTaskPolicy lostEntryTaskPolicy;
    private final PortalAssignmentEventClient portalAssignmentEventClient;
    private final EngineActorIdentities engineActorIdentities;
    private final TaskHandover taskHandover;

    public DepartedHolderReassignment(TaskService taskService,
                                      ExtendedTaskInfoRepository extendedTaskInfoRepository,
                                      FlowableRuntimeTaskQueries runtimeTaskQueries,
                                      TaskHoldingResolver taskHoldingResolver,
                                      LostEntryTaskPolicy lostEntryTaskPolicy,
                                      PortalAssignmentEventClient portalAssignmentEventClient,
                                      EngineActorIdentities engineActorIdentities,
                                      TaskHandover taskHandover) {
        this.taskService = taskService;
        this.extendedTaskInfoRepository = extendedTaskInfoRepository;
        this.runtimeTaskQueries = runtimeTaskQueries;
        this.taskHoldingResolver = taskHoldingResolver;
        this.lostEntryTaskPolicy = lostEntryTaskPolicy;
        this.portalAssignmentEventClient = portalAssignmentEventClient;
        this.engineActorIdentities = engineActorIdentities;
        this.taskHandover = taskHandover;
    }

    /** The tasks of those among {@code userIds} who left; the others are ignored (a stale or repeated notice). */
    public DepartedReassignResult reassign(Collection<String> userIds) {
        Set<String> departed = lostEntryTaskPolicy.departedAmong(userIds);
        return departed.isEmpty() ? new DepartedReassignResult(0, 0, 0, 0) : apply(tasksOf(departed));
    }

    /** Every open task whose holder or candidates left. */
    public DepartedReassignResult reassignAll() {
        return reassign(new LinkedHashSet<>(engineActorIdentities.userIds(runtimeTaskQueries.openTaskPeople()).values()));
    }

    private List<Task> tasksOf(Set<String> departed) {
        Set<String> actors = engineActorIdentities.actorValues(departed);
        Map<String, Task> tasks = new LinkedHashMap<>();
        // Suspended tasks cannot be reassigned; the periodic pass picks them up once they are active again.
        taskService.createTaskQuery().taskAssigneeIds(actors).active().list()
                .forEach(t -> tasks.put(t.getId(), t));
        List<String> unclaimed = runtimeTaskQueries.unclaimedTaskIdsWithCandidateIn(actors);
        if (!unclaimed.isEmpty()) {
            taskService.createTaskQuery().taskIds(unclaimed).active().list()
                    .forEach(t -> tasks.putIfAbsent(t.getId(), t));
        }
        return new ArrayList<>(tasks.values());
    }

    private DepartedReassignResult apply(List<Task> tasks) {
        if (tasks.isEmpty()) {
            return new DepartedReassignResult(0, 0, 0, 0);
        }
        Map<String, ExtendedTaskInfo> rows = extendedTaskInfoRepository
                .findByTaskIdInAndIsDeletedFalse(tasks.stream().map(Task::getId).toList()).stream()
                .collect(Collectors.toMap(ExtendedTaskInfo::getTaskId, Function.identity(), (a, b) -> a));
        Map<String, TaskHolding> read = new LinkedHashMap<>();
        int failed = 0;
        for (Task task : tasks) {
            try {
                read.put(task.getId(), taskHoldingResolver.read(task, Optional.ofNullable(rows.get(task.getId()))));
            } catch (AdminCenterUnavailableException e) {
                // A legacy virtual group's members could not be read; the next pass retries the task.
                failed++;
                log.error("Task {} of a user who left not examined, its pool is unreadable: {}",
                        task.getId(), e.getMessage());
            }
        }
        Map<String, TaskHolding> holdings = taskHoldingResolver.inUserIds(read);
        Set<String> people = new HashSet<>();
        holdings.values().forEach(holding -> people.addAll(holding.people()));
        Set<String> withEntry = lostEntryTaskPolicy.withPortalEntry(people);
        Set<String> departed = lostEntryTaskPolicy.departedAmong(people);
        List<DepartedReassignment> reassigned = new ArrayList<>();
        int stranded = 0;
        try {
            for (Task task : tasks) {
                TaskHolding holding = holdings.get(task.getId());
                List<String> owners = holding == null ? List.of()
                        : LostEntryTaskPolicy.departedOwners(holding, withEntry, departed);
                if (owners.isEmpty()) {
                    continue;
                }
                Optional<String> manager = lostEntryTaskPolicy.managerFor(owners);
                if (manager.isEmpty()) {
                    stranded++;
                    log.error("Task {} ({}) of {} who left stays where it is: no Entity or Function Manager is active "
                            + "with Portal access", task.getId(), task.getName(), owners);
                    continue;
                }
                try {
                    Optional<HandOver> handOver = hand(task, rows.get(task.getId()), manager.get());
                    if (handOver.isEmpty()) {
                        log.info("Task {} of {} who left not handed to {}: its holder changed meanwhile",
                                task.getId(), owners, manager.get());
                        continue;
                    }
                    log.info("Task {} of {} who left handed to manager {}", task.getId(), owners, manager.get());
                    reassigned.add(new DepartedReassignment(task.getId(), task.getProcessInstanceId(),
                            holding.assigneeId(), manager.get(), Instant.now(), handOver.get().endedDelegate()));
                } catch (FlowableException e) {
                    // The task changed under us (completed, deleted); the next pass sees its new state.
                    failed++;
                    log.warn("Task {} of {} who left not handed to {}: {}", task.getId(), owners, manager.get(),
                            e.getMessage());
                }
            }
        } finally {
            portalAssignmentEventClient.reportDepartedReassignments(reassigned);
        }
        DepartedReassignResult result = new DepartedReassignResult(tasks.size(), reassigned.size(), stranded, failed);
        log.info("Tasks of people who left: {}", result);
        return result;
    }

    /** A hand-over that happened; {@code endedDelegate} as in {@link DepartedReassignment}. */
    private record HandOver(String endedDelegate) {
    }

    /**
     * The manager holds the task as a claimer would; its pool, if any, stays as it was. The previous holder's
     * single-task delegation ends with the hand-over (D-59). Empty when the task no longer has the holder this pass
     * read, or its extended row changed since: another pass moved it, or someone claimed, released or delegated it,
     * meanwhile.
     */
    private Optional<HandOver> hand(Task task, ExtendedTaskInfo row, String manager) {
        String[] endedDelegate = new String[1];
        boolean moved = taskHandover.ifStillHeldBy(task.getId(), task.getAssignee(), () -> {
            endedDelegate[0] = row != null ? row.endDelegationUnlessSameHolder(manager) : null;
            taskService.setAssignee(task.getId(), manager);
            if (row != null) {
                row.claimTask(manager);
                extendedTaskInfoRepository.saveAndFlush(row); // a row changed meanwhile fails here, inside the lock
            }
        });
        return moved ? Optional.of(new HandOver(endedDelegate[0])) : Optional.empty();
    }
}
