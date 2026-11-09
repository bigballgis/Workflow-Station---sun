package com.portal.controller;

import com.platform.security.util.ServiceTokenVerifier;
import com.portal.component.TaskAssignmentHistoryRecorder;
import com.portal.dto.TaskAssignmentEventsRequest;
import com.portal.dto.TaskAssignmentEventsRequest.DepartedReassignment;
import com.portal.dto.TaskAssignmentEventsRequest.PoolMembershipRelease;
import com.portal.entity.ChangeHistory;
import com.portal.enums.ChangeType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** The engine's D-40 releases and D-42 hand-overs reach the change history only with the service token. */
class InternalTaskAssignmentEventControllerTest {

    private static final Instant AT = Instant.parse("2026-11-01T00:00:00Z");
    private static final TaskAssignmentEventsRequest REQUEST = new TaskAssignmentEventsRequest(
            List.of(new PoolMembershipRelease("t1", "pi-1", "gone", "FIN", List.of("MANAGER"), AT, null)),
            List.of(new DepartedReassignment("t2", "pi-2", "left", "manager", AT, "deputy")));

    private final TaskAssignmentHistoryRecorder recorder = mock(TaskAssignmentHistoryRecorder.class);
    private final InternalTaskAssignmentEventController controller =
            new InternalTaskAssignmentEventController(new ServiceTokenVerifier("svc-token"), recorder);

    @Test
    void withoutTheServiceTokenNothingIsRecorded() {
        assertThat(controller.record("other", REQUEST).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.record(null, REQUEST).getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(recorder);
    }

    @Test
    void eachReleaseIsRecordedAsASystemRelease() {
        assertThat(controller.record("svc-token", REQUEST).getStatusCode().value()).isEqualTo(200);
        ArgumentCaptor<List<ChangeHistory>> rows = rowsCaptor();
        verify(recorder).recordSystemEvents(rows.capture());
        assertThat(rows.getValue()).extracting(ChangeHistory::getChangeType, ChangeHistory::getTaskInstanceId,
                        ChangeHistory::getOldValue, ChangeHistory::getNewValue)
                .containsExactly(tuple(ChangeType.FORCE_UNCLAIM, "t1", "gone", null),
                        tuple(ChangeType.MANAGER_REASSIGN, "t2", "left", "manager"),
                        tuple(ChangeType.DELEGATION_ENDED, "t2", "deputy", null));
    }

    @Test
    void aListTheEngineLeavesOutCountsAsEmpty() {
        TaskAssignmentEventsRequest onlyReleases = new TaskAssignmentEventsRequest(
                List.of(new PoolMembershipRelease("t1", "pi-1", "gone", "FIN", List.of("MANAGER"), AT, null)), null);

        assertThat(controller.record("svc-token", onlyReleases).getStatusCode().value()).isEqualTo(200);
        assertThat(onlyReleases.departedReassignments()).isEmpty();
    }

    @Test
    void aHistoryFailureIsNotAnsweredAsSuccess() {
        doThrow(new IllegalStateException("history down")).when(recorder).recordSystemEvents(anyList());

        assertThatThrownBy(() -> controller.record("svc-token", REQUEST)).isInstanceOf(IllegalStateException.class);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<ChangeHistory>> rowsCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }
}
