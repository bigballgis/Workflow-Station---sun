package com.workflow.component;

import com.platform.security.service.LostEntryTaskPolicy;
import com.platform.security.service.LostEntryTaskPolicy.PoolKind;
import com.platform.security.service.LostEntryTaskPolicy.TaskHolding;
import com.workflow.client.PortalAssignmentEventClient;
import com.workflow.client.PortalAssignmentEventClient.DepartedReassignment;
import com.workflow.dto.response.DepartedReassignResult;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import com.workflow.repository.EngineActorIdentities;
import com.workflow.repository.ExtendedTaskInfoRepository;
import com.workflow.repository.FlowableRuntimeTaskQueries;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Tasks of people who left go to their Entity / Function Manager (platform access model §7.3, D-39, D-42). */
@ExtendWith(MockitoExtension.class)
class DepartedHolderReassignmentTest {

    @Mock
    private TaskService taskService;
    @Mock
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;
    @Mock
    private FlowableRuntimeTaskQueries runtimeTaskQueries;
    @Mock
    private TaskHoldingResolver taskHoldingResolver;
    @Mock
    private LostEntryTaskPolicy policy;
    @Mock
    private PortalAssignmentEventClient portalAssignmentEventClient;
    @Mock
    private EngineActorIdentities engineActorIdentities;
    @Mock
    private TaskHandover taskHandover;

    private final Map<Task, TaskHolding> holdings = new HashMap<>();
    /** User id by username, for tasks that store a username; every other value is an id. */
    private final Map<String, String> idByUsername = new HashMap<>();
    private DepartedHolderReassignment reassignment;

    @BeforeEach
    void setUp() {
        reassignment = new DepartedHolderReassignment(taskService, extendedTaskInfoRepository, runtimeTaskQueries,
                taskHoldingResolver, policy, portalAssignmentEventClient, engineActorIdentities, taskHandover);
        lenient().when(policy.withPortalEntry(any())).thenReturn(Set.of());
        lenient().when(taskHoldingResolver.read(any(Task.class), any()))
                .thenAnswer(inv -> holdings.get(inv.<Task>getArgument(0)));
        lenient().when(taskHoldingResolver.inUserIds(any())).thenAnswer(inv -> {
            Map<String, TaskHolding> ids = new LinkedHashMap<>();
            inv.<Map<String, TaskHolding>>getArgument(0).forEach((taskId, h) -> ids.put(taskId, new TaskHolding(
                    h.held() ? idByUsername.getOrDefault(h.assigneeId(), h.assigneeId()) : null, h.kind(),
                    h.poolMemberIds().stream().map(m -> idByUsername.getOrDefault(m, m))
                            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)))));
            return ids;
        });
        lenient().when(engineActorIdentities.actorValues(any())).thenAnswer(inv -> {
            Set<String> values = new LinkedHashSet<>(inv.<Collection<String>>getArgument(0));
            idByUsername.forEach((username, id) -> {
                if (values.contains(id)) {
                    values.add(username);
                }
            });
            return values;
        });
        lenient().when(engineActorIdentities.userIds(any())).thenAnswer(inv -> {
            Map<String, String> ids = new LinkedHashMap<>();
            inv.<Collection<String>>getArgument(0).forEach(v -> ids.put(v, idByUsername.getOrDefault(v, v)));
            return ids;
        });
        // The task still has the holder the pass read, unless a test says otherwise.
        lenient().when(taskHandover.ifStillHeldBy(anyString(), any(), any())).thenAnswer(inv -> {
            inv.<Runnable>getArgument(2).run();
            return true;
        });
    }

    @Test
    void aDirectTaskOfAHolderWhoLeftGoesToTheirManagerAndThePortalIsTold() {
        Task task = task("t1", "b");
        ExtendedTaskInfo row = ExtendedTaskInfo.builder().taskId("t1").assignmentType(AssignmentType.USER)
                .assignmentTarget("b").status("ASSIGNED").build();
        departed("b");
        tasks(List.of(task), List.of());
        when(extendedTaskInfoRepository.findByTaskIdInAndIsDeletedFalse(List.of("t1"))).thenReturn(List.of(row));
        holding(task, new TaskHolding("b", PoolKind.NONE, Set.of()));
        when(policy.managerFor(List.of("b"))).thenReturn(Optional.of("m"));

        DepartedReassignResult result = reassignment.reassign(List.of("b"));

        verify(taskService).setAssignee("t1", "m");
        assertThat(row.getCurrentAssignee()).isEqualTo("m");
        verify(extendedTaskInfoRepository).saveAndFlush(row);
        assertThat(result).isEqualTo(new DepartedReassignResult(1, 1, 0, 0));
        DepartedReassignment event = reported().get(0);
        assertThat(event.taskId()).isEqualTo("t1");
        assertThat(event.processInstanceId()).isEqualTo("pi-t1");
        assertThat(event.previousAssigneeId()).isEqualTo("b");
        assertThat(event.newAssigneeId()).isEqualTo("m");
    }

    @Test
    void theLeaversSingleTaskDelegationEndsWithTheHandOver() {
        Task task = task("t1", "b");
        ExtendedTaskInfo row = ExtendedTaskInfo.builder().taskId("t1").assignmentType(AssignmentType.USER)
                .assignmentTarget("b").status("ASSIGNED").build();
        row.delegateTask("deputy", "b", "leave");
        departed("b");
        tasks(List.of(task), List.of());
        when(extendedTaskInfoRepository.findByTaskIdInAndIsDeletedFalse(List.of("t1"))).thenReturn(List.of(row));
        holding(task, new TaskHolding("b", PoolKind.NONE, Set.of()));
        when(policy.managerFor(List.of("b"))).thenReturn(Optional.of("m"));

        reassignment.reassign(List.of("b"));

        assertThat(row.isDelegated()).isFalse();
        assertThat(reported().get(0).endedDelegate()).isEqualTo("deputy");
    }

    @Test
    void aUserWhoDidNotLeaveIsIgnored() {
        when(policy.departedAmong(List.of("b"))).thenReturn(Set.of());

        assertThat(reassignment.reassign(List.of("b"))).isEqualTo(new DepartedReassignResult(0, 0, 0, 0));
        verifyNoInteractions(taskService, portalAssignmentEventClient);
    }

    @Test
    void aHeldPoolTaskStaysWhileAColleagueWithEntryCanTakeItOver() {
        Task task = task("t1", "b");
        departed("b");
        tasks(List.of(task), List.of());
        holding(task, new TaskHolding("b", PoolKind.MEMBERSHIP, Set.of("b", "c")));
        when(policy.withPortalEntry(any())).thenReturn(Set.of("c"));

        DepartedReassignResult result = reassignment.reassign(List.of("b"));

        verify(taskService, never()).setAssignee(anyString(), anyString());
        verify(policy, never()).managerFor(any());
        assertThat(result).isEqualTo(new DepartedReassignResult(1, 0, 0, 0));
    }

    @Test
    void anUnclaimedListGoesToAManagerOfItsCandidatesInTheirOrderOnceAllLeft() {
        Task task = task("t2", null);
        when(policy.departedAmong(any())).thenReturn(Set.of("a", "b"));
        tasks(List.of(), List.of(task));
        holding(task, new TaskHolding(null, PoolKind.PEOPLE, new LinkedHashSet<>(List.of("b", "a"))));
        when(policy.managerFor(List.of("b", "a"))).thenReturn(Optional.of("m"));

        reassignment.reassign(List.of("a"));

        verify(taskService).setAssignee("t2", "m");
        assertThat(reported().get(0).previousAssigneeId()).isNull();
    }

    @Test
    void anUnclaimedListWithAColleagueStillThereStays() {
        Task task = task("t2", null);
        when(policy.departedAmong(any())).thenReturn(Set.of("a"));
        tasks(List.of(), List.of(task));
        holding(task, new TaskHolding(null, PoolKind.PEOPLE, Set.of("a", "b")));

        reassignment.reassign(List.of("a"));

        verify(taskService, never()).setAssignee(anyString(), anyString());
    }

    @Test
    void withoutAnAvailableManagerTheTaskStaysAndIsCounted() {
        Task task = task("t1", "b");
        departed("b");
        tasks(List.of(task), List.of());
        holding(task, new TaskHolding("b", PoolKind.NONE, Set.of()));
        when(policy.managerFor(List.of("b"))).thenReturn(Optional.empty());

        DepartedReassignResult result = reassignment.reassign(List.of("b"));

        verify(taskService, never()).setAssignee(anyString(), anyString());
        assertThat(result).isEqualTo(new DepartedReassignResult(1, 0, 1, 0));
        assertThat(reported()).isEmpty();
    }

    @Test
    void aTaskThatChangedMeanwhileIsCountedAndTheRestGoesOn() {
        Task t1 = task("t1", "b");
        Task t2 = task("t2", "b");
        departed("b");
        tasks(List.of(t1, t2), List.of());
        holding(t1, new TaskHolding("b", PoolKind.NONE, Set.of()));
        holding(t2, new TaskHolding("b", PoolKind.NONE, Set.of()));
        when(policy.managerFor(List.of("b"))).thenReturn(Optional.of("m"));
        doThrow(new FlowableObjectNotFoundException("completed")).when(taskService).setAssignee("t1", "m");

        DepartedReassignResult result = reassignment.reassign(List.of("b"));

        verify(taskService).setAssignee("t2", "m");
        assertThat(result).isEqualTo(new DepartedReassignResult(2, 1, 0, 1));
        assertThat(reported()).extracting(DepartedReassignment::taskId).containsExactly("t2");
    }

    @Test
    void theFullPassLooksAtEveryOpenTasksPeople() {
        when(runtimeTaskQueries.openTaskPeople()).thenReturn(List.of("a", "b"));
        when(policy.departedAmong(Set.of("a", "b"))).thenReturn(Set.of());

        reassignment.reassignAll();

        verify(policy).departedAmong(Set.of("a", "b"));
    }

    @Test
    void theFullPassJudgesAHolderStoredByUsernameAsTheirUser() {
        idByUsername.put("bob", "u-bob");
        when(runtimeTaskQueries.openTaskPeople()).thenReturn(List.of("bob", "u-carol"));
        when(policy.departedAmong(Set.of("u-bob", "u-carol"))).thenReturn(Set.of());

        reassignment.reassignAll();

        verify(policy).departedAmong(Set.of("u-bob", "u-carol"));
    }

    @Test
    void aTaskThatStoresTheLeaversUsernameIsFoundAndHandedToTheirManager() {
        idByUsername.put("bob", "u-bob");
        Task task = task("t1", "bob");
        departed("u-bob");
        TaskQuery assignedQuery = tasks(List.of(task), List.of());
        holding(task, new TaskHolding("bob", PoolKind.NONE, Set.of()));
        when(policy.managerFor(List.of("u-bob"))).thenReturn(Optional.of("m"));

        DepartedReassignResult result = reassignment.reassign(List.of("u-bob"));

        verify(assignedQuery).taskAssigneeIds(Set.of("u-bob", "bob"));
        verify(runtimeTaskQueries).unclaimedTaskIdsWithCandidateIn(Set.of("u-bob", "bob"));
        verify(taskService).setAssignee("t1", "m");
        assertThat(result).isEqualTo(new DepartedReassignResult(1, 1, 0, 0));
        assertThat(reported().get(0).previousAssigneeId()).isEqualTo("u-bob");
    }

    @Test
    void aTaskAnotherPassAlreadyHandedOverIsNeitherHandedNorReportedAgain() {
        Task task = task("t1", "b");
        departed("b");
        tasks(List.of(task), List.of());
        holding(task, new TaskHolding("b", PoolKind.NONE, Set.of()));
        when(policy.managerFor(List.of("b"))).thenReturn(Optional.of("m"));
        when(taskHandover.ifStillHeldBy(eq("t1"), eq("b"), any())).thenReturn(false);

        DepartedReassignResult result = reassignment.reassign(List.of("b"));

        verify(taskService, never()).setAssignee(anyString(), anyString());
        assertThat(result).isEqualTo(new DepartedReassignResult(1, 0, 0, 0));
        assertThat(reported()).isEmpty();
    }

    private void departed(String... userIds) {
        when(policy.departedAmong(any())).thenReturn(Set.of(userIds));
    }

    private TaskQuery tasks(List<Task> assigned, List<Task> unclaimed) {
        TaskQuery assignedQuery = mock(TaskQuery.class, RETURNS_SELF);
        when(assignedQuery.list()).thenReturn(assigned);
        if (unclaimed.isEmpty()) {
            when(taskService.createTaskQuery()).thenReturn(assignedQuery);
            when(runtimeTaskQueries.unclaimedTaskIdsWithCandidateIn(any())).thenReturn(List.of());
            return assignedQuery;
        }
        TaskQuery unclaimedQuery = mock(TaskQuery.class, RETURNS_SELF);
        when(unclaimedQuery.list()).thenReturn(unclaimed);
        when(taskService.createTaskQuery()).thenReturn(assignedQuery, unclaimedQuery);
        List<String> unclaimedIds = unclaimed.stream().map(Task::getId).toList();
        when(runtimeTaskQueries.unclaimedTaskIdsWithCandidateIn(any())).thenReturn(unclaimedIds);
        return assignedQuery;
    }

    private void holding(Task task, TaskHolding holding) {
        holdings.put(task, holding);
    }

    @SuppressWarnings("unchecked")
    private List<DepartedReassignment> reported() {
        ArgumentCaptor<List<DepartedReassignment>> captor = ArgumentCaptor.forClass(List.class);
        verify(portalAssignmentEventClient).reportDepartedReassignments(captor.capture());
        return captor.getValue();
    }

    private Task task(String id, String assignee) {
        Task task = mock(Task.class);
        lenient().when(task.getId()).thenReturn(id);
        lenient().when(task.getAssignee()).thenReturn(assignee);
        lenient().when(task.getProcessInstanceId()).thenReturn("pi-" + id);
        return task;
    }
}
