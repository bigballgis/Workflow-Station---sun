package com.workflow.component;

import com.workflow.client.AdminCenterClient;
import com.workflow.client.PortalAssignmentEventClient;
import com.workflow.client.PortalAssignmentEventClient.PoolMembershipRelease;
import com.workflow.component.ClaimPoolTaskLocator.PoolTask;
import com.workflow.dto.response.ClaimPoolResyncResult;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import com.workflow.exception.AdminCenterUnavailableException;
import com.workflow.repository.EngineActorIdentities;
import com.workflow.repository.ExtendedTaskInfoRepository;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** BU + Role pools follow the current UBR holders (WP8b-2) and a holder who lost the BU + Role is released (D-40). */
@ExtendWith(MockitoExtension.class)
class ClaimPoolMembershipSyncTest {

    @Mock
    private TaskService taskService;
    @Mock
    private AdminCenterClient adminCenterClient;
    @Mock
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;
    @Mock
    private ClaimPoolTaskLocator locator;
    @Mock
    private PortalAssignmentEventClient portalAssignmentEventClient;
    @Mock
    private TaskHandover taskHandover;
    @Mock
    private EngineActorIdentities engineActorIdentities;

    private ClaimPoolMembershipSync sync;

    @BeforeEach
    void setUp() {
        sync = new ClaimPoolMembershipSync(taskService, adminCenterClient, extendedTaskInfoRepository, locator,
                portalAssignmentEventClient, taskHandover, engineActorIdentities);
        usernames(Map.of()); // every value is a user id, unless a test says otherwise
        // The task still has the holder the pass read, unless a test says otherwise.
        lenient().when(taskHandover.ifStillHeldBy(anyString(), any(), any())).thenAnswer(call -> {
            call.<Runnable>getArgument(2).run();
            return true;
        });
        lenient().when(adminCenterClient.isEligibleRole("FIN", "MANAGER")).thenReturn(true);
    }

    @Test
    void newHoldersJoinAndFormerHoldersLeaveTheCandidates() {
        Task task = task("t1", null);
        candidates("t1", "a", "gone");
        holders("a", "b");
        when(locator.all()).thenReturn(List.of(new PoolTask(task, "FIN", List.of("MANAGER"), null)));

        ClaimPoolResyncResult result = sync.resyncAll();

        verify(taskService).deleteCandidateUser("t1", "gone");
        verify(taskService).addCandidateUser("t1", "b");
        verify(taskService, never()).unclaim(anyString());
        assertThat(result).isEqualTo(new ClaimPoolResyncResult(1, 1, 0, 0, 0));
    }

    @Test
    void aHolderWhoLostTheBuRoleIsReleasedAndThePortalTold() {
        Task task = task("t1", "gone");
        candidates("t1", "gone", "a");
        holders("a");
        when(locator.all()).thenReturn(List.of(new PoolTask(task, "FIN", List.of("MANAGER"), null)));

        ClaimPoolResyncResult result = sync.resyncAll();

        verify(taskService).unclaim("t1");
        verify(taskService).deleteCandidateUser("t1", "gone");
        assertThat(result.holdersReleased()).isEqualTo(1);
        PoolMembershipRelease release = reportedReleases().get(0);
        assertThat(release.taskId()).isEqualTo("t1");
        assertThat(release.previousAssigneeId()).isEqualTo("gone");
        assertThat(release.businessUnitCode()).isEqualTo("FIN");
        assertThat(release.roleCodes()).containsExactly("MANAGER");
    }

    @Test
    void aHolderAnotherPassAlreadyReleasedIsLeftToTheNextPassAndNotReportedAgain() {
        Task task = task("t1", "gone");
        candidates("t1", "gone", "a");
        holders("a", "b");
        when(taskHandover.ifStillHeldBy(eq("t1"), eq("gone"), any())).thenReturn(false);
        when(locator.all()).thenReturn(List.of(new PoolTask(task, "FIN", List.of("MANAGER"), null)));

        ClaimPoolResyncResult result = sync.resyncAll();

        verify(taskService, never()).unclaim(anyString());
        verify(taskService, never()).deleteCandidateUser(anyString(), anyString());
        verify(taskService, never()).addCandidateUser(anyString(), anyString());
        assertThat(result).isEqualTo(new ClaimPoolResyncResult(1, 0, 0, 1, 0));
        assertThat(reportedReleases()).isEmpty();
    }

    @Test
    void anMiRowAnotherPassAlreadyReleasedIsNotWrittenBackFromThisPassesStaleRead() {
        Task task = task("t1", "gone");
        candidates("t1", "gone", "a");
        holders("a");
        ExtendedTaskInfo row = ExtendedTaskInfo.builder().taskId("t1").claimedBy("gone").status("CLAIMED")
                .assignmentType(AssignmentType.CANDIDATE_USERS).assignmentTarget("gone,a").build();
        row.delegateTask("deputy", "gone", "leave");
        when(taskHandover.ifStillHeldBy(eq("t1"), eq("gone"), any())).thenReturn(false);
        when(locator.all()).thenReturn(List.of(new PoolTask(task, "FIN", List.of("MANAGER"), row)));

        ClaimPoolResyncResult result = sync.resyncAll();

        verifyNoInteractions(extendedTaskInfoRepository);
        verify(taskService, never()).deleteCandidateUser(anyString(), anyString());
        assertThat(row.getClaimedBy()).isEqualTo("gone");
        assertThat(row.isDelegated()).isTrue();
        assertThat(row.getAssignmentTarget()).isEqualTo("gone,a");
        assertThat(result.tasksSkipped()).isEqualTo(1);
        assertThat(reportedReleases()).isEmpty();
    }

    @Test
    void aHolderWhoWasNeverInThePoolKeepsTheTask() {
        Task task = task("t1", "transferee");
        candidates("t1", "a");
        holders("a");
        when(locator.all()).thenReturn(List.of(new PoolTask(task, "FIN", List.of("MANAGER"), null)));

        ClaimPoolResyncResult result = sync.resyncAll();

        verify(taskService, never()).unclaim(anyString());
        assertThat(result).isEqualTo(new ClaimPoolResyncResult(1, 0, 0, 0, 0));
    }

    @Test
    void anMiRowIsReleasedAndRemembersItsPool() {
        Task task = task("t1", "gone");
        candidates("t1", "gone");
        holders("a");
        ExtendedTaskInfo row = ExtendedTaskInfo.builder().taskId("t1").claimedBy("gone").status("CLAIMED")
                .assignmentType(AssignmentType.CANDIDATE_USERS).build();
        when(locator.all()).thenReturn(List.of(new PoolTask(task, "FIN", List.of("MANAGER"), row)));

        sync.resyncAll();

        verify(taskService).unclaim("t1");
        assertThat(row.isClaimed()).isFalse();
        assertThat(row.getAssignmentTarget()).isEqualTo("a");
        verify(extendedTaskInfoRepository).saveAndFlush(row);
    }

    @Test
    void anMiRowIsSavedOnceInsideTheReleasesTransaction() {
        Task task = task("t1", "gone");
        candidates("t1", "gone");
        holders("a");
        ExtendedTaskInfo row = ExtendedTaskInfo.builder().taskId("t1").claimedBy("gone").status("CLAIMED")
                .assignmentType(AssignmentType.CANDIDATE_USERS).build();
        AtomicBoolean inTransaction = new AtomicBoolean();
        List<Boolean> savedInTransaction = new ArrayList<>();
        when(taskHandover.ifStillHeldBy(eq("t1"), eq("gone"), any())).thenAnswer(call -> {
            inTransaction.set(true);
            try {
                call.<Runnable>getArgument(2).run();
            } finally {
                inTransaction.set(false);
            }
            return true;
        });
        when(extendedTaskInfoRepository.saveAndFlush(row)).thenAnswer(call -> {
            savedInTransaction.add(inTransaction.get());
            return row;
        });
        when(locator.all()).thenReturn(List.of(new PoolTask(task, "FIN", List.of("MANAGER"), row)));

        sync.resyncAll();

        assertThat(savedInTransaction).containsExactly(true);
        assertThat(row.getAssignmentTarget()).isEqualTo("a");
    }

    @Test
    void aHolderStoredByUsernameWhoLostTheBuRoleIsReleasedAndReportedByUserId() {
        Task task = task("t1", "gone.user");
        candidates("t1", "u-gone", "a");
        holders("a");
        usernames(Map.of("gone.user", "u-gone"));
        when(locator.all()).thenReturn(List.of(new PoolTask(task, "FIN", List.of("MANAGER"), null)));

        ClaimPoolResyncResult result = sync.resyncAll();

        verify(taskHandover).ifStillHeldBy(eq("t1"), eq("gone.user"), any());
        verify(taskService).unclaim("t1");
        assertThat(result.holdersReleased()).isEqualTo(1);
        assertThat(reportedReleases()).singleElement()
                .satisfies(r -> assertThat(r.previousAssigneeId()).isEqualTo("u-gone"));
    }

    @Test
    void aHolderStoredByUsernameWhoStillHoldsTheBuRoleKeepsTheTask() {
        // A pool from before the platform assigned by id: holder and candidate are both the username.
        Task task = task("t1", "alice");
        candidates("t1", "alice");
        holders("u-alice");
        usernames(Map.of("alice", "u-alice"));
        when(locator.all()).thenReturn(List.of(new PoolTask(task, "FIN", List.of("MANAGER"), null)));

        ClaimPoolResyncResult result = sync.resyncAll();

        verify(taskHandover, never()).ifStillHeldBy(anyString(), any(), any());
        verify(taskService, never()).unclaim(anyString());
        assertThat(result.holdersReleased()).isZero();
        assertThat(reportedReleases()).isEmpty();
    }

    @Test
    void aHolderWhoStillHoldsTheBuRoleByIdNeedsNoUserLookup() {
        Task task = task("t1", "a");
        candidates("t1", "a");
        holders("a");
        when(locator.all()).thenReturn(List.of(new PoolTask(task, "FIN", List.of("MANAGER"), null)));

        assertThat(sync.resyncAll()).isEqualTo(new ClaimPoolResyncResult(1, 0, 0, 0, 0));
        verifyNoInteractions(engineActorIdentities);
    }

    @Test
    void aRoleTheBuDoesNotAdmitLeavesTheTaskAlone() {
        Task task = task("t1", null);
        when(adminCenterClient.isEligibleRole("FIN", "CLERK")).thenReturn(false);
        when(locator.all()).thenReturn(List.of(new PoolTask(task, "FIN", List.of("CLERK"), null)));

        ClaimPoolResyncResult result = sync.resyncAll();

        verify(taskService, never()).getIdentityLinksForTask(anyString());
        assertThat(result.tasksSkipped()).isEqualTo(1);
    }

    @Test
    void holdersAreReadOncePerPoolInAPass() {
        Task t1 = task("t1", null);
        Task t2 = task("t2", null);
        candidates("t1", "a");
        candidates("t2", "a");
        holders("a");
        when(locator.all()).thenReturn(List.of(
                new PoolTask(t1, "FIN", List.of("MANAGER"), null), new PoolTask(t2, "FIN", List.of("MANAGER"), null)));

        sync.resyncAll();

        verify(adminCenterClient, times(1)).getUsersByBusinessUnitAndRole("FIN", "MANAGER");
    }

    @Test
    void aTaskThatChangedMeanwhileIsCountedAndTheRestGoesOn() {
        Task t1 = task("t1", "gone");
        Task t2 = task("t2", null);
        candidates("t1", "gone");
        candidates("t2", "a");
        holders("b");
        doThrow(new FlowableObjectNotFoundException("completed")).when(taskService).unclaim("t1");
        when(locator.all()).thenReturn(List.of(
                new PoolTask(t1, "FIN", List.of("MANAGER"), null), new PoolTask(t2, "FIN", List.of("MANAGER"), null)));

        ClaimPoolResyncResult result = sync.resyncAll();

        assertThat(result.tasksFailed()).isEqualTo(1);
        verify(taskService).addCandidateUser("t2", "b");
    }

    @Test
    void adminCenterDownStopsThePassAndStillReportsReleasesMadeSoFar() {
        Task t1 = task("t1", "gone");
        Task t2 = task("t2", null);
        candidates("t1", "gone");
        holders("a");
        when(adminCenterClient.isEligibleRole("FIN", "CLERK"))
                .thenThrow(new AdminCenterUnavailableException("down", null));
        when(locator.all()).thenReturn(List.of(
                new PoolTask(t1, "FIN", List.of("MANAGER"), null), new PoolTask(t2, "FIN", List.of("CLERK"), null)));

        assertThatThrownBy(() -> sync.resyncAll()).isInstanceOf(AdminCenterUnavailableException.class);
        assertThat(reportedReleases()).hasSize(1);
    }

    @Test
    void releasesOneAfterAnotherShareTheirHolderLookups() {
        Task t1 = task("t1", null);
        Task t2 = task("t2", null);
        candidates("t1", "a");
        candidates("t2", "a");
        holders("a");
        TaskQuery query = mock(TaskQuery.class, org.mockito.Mockito.RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(t1, t2);
        when(locator.of(t1)).thenReturn(java.util.Optional.of(new PoolTask(t1, "FIN", List.of("MANAGER"), null)));
        when(locator.of(t2)).thenReturn(java.util.Optional.of(new PoolTask(t2, "FIN", List.of("MANAGER"), null)));

        sync.resyncTask("t1");
        sync.resyncTask("t2");

        verify(adminCenterClient, times(1)).getUsersByBusinessUnitAndRole("FIN", "MANAGER");
    }

    @Test
    void aReleaseEndsTheHoldersSingleTaskDelegationAndThePortalHearsOfIt() {
        Task task = task("t1", "gone");
        candidates("t1", "gone");
        holders("a");
        ExtendedTaskInfo row = ExtendedTaskInfo.builder().taskId("t1").claimedBy("gone").status("CLAIMED")
                .assignmentType(AssignmentType.CANDIDATE_USERS).build();
        row.delegateTask("deputy", "gone", "leave");
        when(locator.all()).thenReturn(List.of(new PoolTask(task, "FIN", List.of("MANAGER"), row)));

        sync.resyncAll();

        assertThat(row.isDelegated()).isFalse();
        assertThat(reportedReleases()).singleElement()
                .satisfies(r -> assertThat(r.endedDelegate()).isEqualTo("deputy"));
    }

    @Test
    void theReadPathRepairsABpmnPoolTaskWithANonRoleExtendedRowButLeavesMiRoleRows() {
        Task bpmn = task("t1", null);
        Task miRole = task("t2", null);
        candidates("t1", "gone");
        holders("a");
        ExtendedTaskInfo plainRow = ExtendedTaskInfo.builder().taskId("t1")
                .extendedProperties("{\"multiInstance\":true}").build();
        ExtendedTaskInfo roleRow = ExtendedTaskInfo.builder().taskId("t2")
                .extendedProperties("{\"assigneeMode\":\"role\",\"businessUnitCode\":\"FIN\",\"roleCodes\":[\"MANAGER\"]}")
                .build();
        when(locator.of(bpmn)).thenReturn(java.util.Optional.of(new PoolTask(bpmn, "FIN", List.of("MANAGER"), plainRow)));
        when(locator.of(miRole)).thenReturn(java.util.Optional.of(new PoolTask(miRole, "FIN", List.of("MANAGER"), roleRow)));

        sync.resyncUnclaimed(List.of(bpmn, miRole));

        verify(taskService).addCandidateUser("t1", "a");
        verify(taskService, never()).getIdentityLinksForTask("t2");
    }

    @Test
    void aPushOrAFullPassMakesTheNextSingleTaskResyncReadHoldersAgain() {
        Task t1 = task("t1", null);
        candidates("t1", "a");
        holders("a");
        TaskQuery query = mock(TaskQuery.class, org.mockito.Mockito.RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(t1);
        when(locator.of(t1)).thenReturn(java.util.Optional.of(new PoolTask(t1, "FIN", List.of("MANAGER"), null)));
        when(locator.all()).thenReturn(List.of());

        sync.resyncTask("t1");
        sync.resyncAll();
        sync.resyncTask("t1");

        verify(adminCenterClient, times(2)).getUsersByBusinessUnitAndRole("FIN", "MANAGER");
    }

    private Task task(String id, String assignee) {
        Task task = mock(Task.class);
        lenient().when(task.getId()).thenReturn(id);
        lenient().when(task.getAssignee()).thenReturn(assignee);
        lenient().when(task.getProcessInstanceId()).thenReturn("pi-" + id);
        return task;
    }

    private void candidates(String taskId, String... userIds) {
        List<IdentityLink> links = new ArrayList<>();
        for (String userId : userIds) {
            IdentityLink link = mock(IdentityLink.class);
            when(link.getType()).thenReturn("candidate");
            when(link.getUserId()).thenReturn(userId);
            links.add(link);
        }
        when(taskService.getIdentityLinksForTask(taskId)).thenReturn(links);
    }

    private void holders(String... userIds) {
        when(adminCenterClient.getUsersByBusinessUnitAndRole("FIN", "MANAGER")).thenReturn(List.of(userIds));
    }

    /** Values naming a user by username; any other value is a user id or names nobody, and stays as it is. */
    private void usernames(Map<String, String> idByUsername) {
        lenient().doAnswer(call -> {
            Map<String, String> ids = new LinkedHashMap<>();
            for (String value : call.<Collection<String>>getArgument(0)) {
                ids.put(value, idByUsername.getOrDefault(value, value));
            }
            return ids;
        }).when(engineActorIdentities).userIds(any());
    }

    @SuppressWarnings("unchecked")
    private List<PoolMembershipRelease> reportedReleases() {
        ArgumentCaptor<List<PoolMembershipRelease>> captor = ArgumentCaptor.forClass(List.class);
        verify(portalAssignmentEventClient).reportPoolMembershipReleases(captor.capture());
        return captor.getValue();
    }
}
