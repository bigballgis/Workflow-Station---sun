package com.portal.component;

import com.portal.entity.ChangeHistory;
import com.portal.enums.AssignmentChangeTypes;
import com.portal.enums.ChangeType;
import com.portal.repository.ChangeHistoryRepository;
import com.portal.testsupport.PortalTransactionTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D-40: a system release is a FORCE_UNCLAIM by "system" back to the pool, with the reason. D-42: a hand-over to the
 * manager of a holder who left is a MANAGER_REASSIGN by "system", from the former holder to the manager.
 */
class TaskAssignmentHistoryRecorderPoolReleaseTest {

    @Test
    void recordsWhoHeldTheTaskAndWhyTheSystemReleasedIt() {
        Instant at = Instant.parse("2026-11-01T08:00:00Z");

        ChangeHistory row = TaskAssignmentHistoryRecorder.poolMembershipRelease("pi-1", "t1", "gone", at);
        assertThat(row.getChangeType()).isEqualTo(ChangeType.FORCE_UNCLAIM);
        assertThat(row.getUserId()).isEqualTo(AssignmentChangeTypes.SYSTEM_ACTOR);
        assertThat(row.getReason()).isEqualTo(AssignmentChangeTypes.REASON_POOL_MEMBERSHIP_LOST);
        assertThat(row.getOldValue()).isEqualTo("gone");
        assertThat(row.getNewValue()).isNull();
        assertThat(row.getFieldName()).isEqualTo(AssignmentChangeTypes.FIELD_NAME);
        assertThat(row.getTaskInstanceId()).isEqualTo("t1");
        assertThat(row.getTimestamp()).isEqualTo(at);
    }

    @Test
    void recordsTheFormerHolderTheManagerAndWhyTheSystemHandedItOver() {
        Instant at = Instant.parse("2026-11-01T09:00:00Z");

        ChangeHistory row = TaskAssignmentHistoryRecorder.departedReassignment("pi-1", "t1", "gone", "manager", at);
        assertThat(row.getChangeType()).isEqualTo(ChangeType.MANAGER_REASSIGN);
        assertThat(row.getUserId()).isEqualTo(AssignmentChangeTypes.SYSTEM_ACTOR);
        assertThat(row.getReason()).isEqualTo(AssignmentChangeTypes.REASON_ASSIGNEE_DEPARTED);
        assertThat(row.getOldValue()).isEqualTo("gone");
        assertThat(row.getNewValue()).isEqualTo("manager");
        assertThat(row.getFieldName()).isEqualTo(AssignmentChangeTypes.FIELD_NAME);
        assertThat(row.getTaskInstanceId()).isEqualTo("t1");
        assertThat(row.getTimestamp()).isEqualTo(at);
    }

    @Test
    void aDelegationTheEngineEndedWithTheReassignmentIsRecordedInTheSameBatch() {
        ChangeHistoryRepository repository = mock(ChangeHistoryRepository.class);
        TaskAssignmentHistoryRecorder recorder = new TaskAssignmentHistoryRecorder(
                repository, PortalTransactionTestSupport.noopPlatformTransactionManager());

        recorder.record(delegated("a"), held("b"), "b", ChangeType.REASSIGN, "b");

        ChangeHistory ended = savedRows(repository, 2).get(1);
        assertThat(ended.getChangeType()).isEqualTo(ChangeType.DELEGATION_ENDED);
        assertThat(ended.getFieldName()).isEqualTo(AssignmentChangeTypes.DELEGATED_TO_FIELD);
        assertThat(ended.getOldValue()).isEqualTo("c");
        assertThat(ended.getReason()).isEqualTo(AssignmentChangeTypes.REASON_HOLDER_CHANGED);
        assertThat(ended.getUserId()).isEqualTo("b");
        assertThat(ended.getBatchId()).isEqualTo(savedRows(repository, 2).get(0).getBatchId());
    }

    @Test
    void theHolderUnclaimingRecordsTheEndOfTheirDelegation() {
        ChangeHistoryRepository repository = mock(ChangeHistoryRepository.class);
        TaskAssignmentHistoryRecorder recorder = new TaskAssignmentHistoryRecorder(
                repository, PortalTransactionTestSupport.noopPlatformTransactionManager());

        recorder.record(delegated("a"), held(null), "a", ChangeType.UNCLAIM, null);

        List<ChangeHistory> rows = savedRows(repository, 2);
        assertThat(rows.get(0).getChangeType()).isEqualTo(ChangeType.UNCLAIM);
        assertThat(rows.get(1).getChangeType()).isEqualTo(ChangeType.DELEGATION_ENDED);
        assertThat(rows.get(1).getOldValue()).isEqualTo("c");
        assertThat(rows.get(1).getUserId()).isEqualTo("a");
    }

    @Test
    void aDelegationTheTaskStillCarriesAfterTheChangeIsNotRecordedAsEnded() {
        ChangeHistoryRepository repository = mock(ChangeHistoryRepository.class);
        TaskAssignmentHistoryRecorder recorder = new TaskAssignmentHistoryRecorder(
                repository, PortalTransactionTestSupport.noopPlatformTransactionManager());

        recorder.record(delegated("a"), delegated("a"), "a", ChangeType.CLAIM, "a");

        savedRows(repository, 1);
    }

    /** A transfer writes no assignment row of its own; the delegation it ended is still recorded (D-59). */
    @Test
    void aTransferThatEndedTheHoldersDelegationRecordsOnlyItsEnd() {
        ChangeHistoryRepository repository = mock(ChangeHistoryRepository.class);
        TaskAssignmentHistoryRecorder recorder = new TaskAssignmentHistoryRecorder(
                repository, PortalTransactionTestSupport.noopPlatformTransactionManager());

        recorder.recordDelegationEnded(delegated("a"), held("x"), "a");

        ChangeHistory ended = savedRows(repository, 1).get(0);
        assertThat(ended.getChangeType()).isEqualTo(ChangeType.DELEGATION_ENDED);
        assertThat(ended.getFieldName()).isEqualTo(AssignmentChangeTypes.DELEGATED_TO_FIELD);
        assertThat(ended.getOldValue()).isEqualTo("c");
        assertThat(ended.getUserId()).isEqualTo("a");
        assertThat(ended.getReason()).isEqualTo(AssignmentChangeTypes.REASON_HOLDER_CHANGED);
    }

    @Test
    void aTransferOfATaskWithoutDelegationOrStillDelegatedRecordsNothing() {
        ChangeHistoryRepository repository = mock(ChangeHistoryRepository.class);
        TaskAssignmentHistoryRecorder recorder = new TaskAssignmentHistoryRecorder(
                repository, PortalTransactionTestSupport.noopPlatformTransactionManager());

        recorder.recordDelegationEnded(held("a"), held("x"), "a");
        recorder.recordDelegationEnded(delegated("a"), delegated("a"), "a");
        recorder.recordDelegationEnded(delegated("a"), null, "a");

        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    private static com.portal.dto.TaskInfo delegated(String holder) {
        return com.portal.dto.TaskInfo.builder().taskId("t1").processInstanceId("pi-1")
                .assignee(holder).delegatorId(holder).delegatedTargetType("USER").delegatedTo("c").build();
    }

    private static com.portal.dto.TaskInfo held(String holder) {
        return com.portal.dto.TaskInfo.builder().taskId("t1").processInstanceId("pi-1").assignee(holder).build();
    }

    private static List<ChangeHistory> savedRows(ChangeHistoryRepository repository, int count) {
        org.mockito.ArgumentCaptor<ChangeHistory> saved = org.mockito.ArgumentCaptor.forClass(ChangeHistory.class);
        verify(repository, org.mockito.Mockito.times(count)).save(saved.capture());
        return saved.getAllValues();
    }

    @Test
    void systemEventsAreSavedTogether() {
        ChangeHistoryRepository repository = mock(ChangeHistoryRepository.class);
        TaskAssignmentHistoryRecorder recorder = new TaskAssignmentHistoryRecorder(
                repository, PortalTransactionTestSupport.noopPlatformTransactionManager());
        List<ChangeHistory> rows = List.of(
                TaskAssignmentHistoryRecorder.poolMembershipRelease("pi-1", "t1", "gone", Instant.EPOCH),
                TaskAssignmentHistoryRecorder.departedReassignment("pi-2", "t2", "left", "manager", Instant.EPOCH));

        recorder.recordSystemEvents(rows);

        verify(repository).saveAll(rows);
    }

    @Test
    void aFailedSaveOfSystemEventsIsThrownForTheEngineToLog() {
        ChangeHistoryRepository repository = mock(ChangeHistoryRepository.class);
        when(repository.saveAll(anyList())).thenThrow(new DataAccessResourceFailureException("column missing"));
        TaskAssignmentHistoryRecorder recorder = new TaskAssignmentHistoryRecorder(
                repository, PortalTransactionTestSupport.noopPlatformTransactionManager());
        List<ChangeHistory> rows = List.of(
                TaskAssignmentHistoryRecorder.poolMembershipRelease("pi-1", "t1", "gone", Instant.EPOCH));

        assertThatThrownBy(() -> recorder.recordSystemEvents(rows)).isInstanceOf(DataAccessResourceFailureException.class);
    }
}
