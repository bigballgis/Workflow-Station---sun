package com.portal.component;

import com.portal.client.AdminCenterClient;
import com.portal.dto.TaskInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimForceUnclaimAnnotatorTest {

    @Mock
    private AdminCenterClient adminCenterClient;

    private ClaimForceUnclaimAnnotator annotator;

    @BeforeEach
    void setUp() {
        annotator = new ClaimForceUnclaimAnnotator(adminCenterClient);
    }

    @Test
    void skipsEvaluateWhenNothingIsHeldBySomeoneElse() {
        TaskInfo free = poolTask("t-free", null, false);
        TaskInfo mine = poolTask("t-mine", "me", true);

        annotator.annotate(List.of(free, mine), "me");

        assertThat(free.isCanForceUnclaim()).isFalse();
        assertThat(mine.isCanForceUnclaim()).isFalse();
        verify(adminCenterClient, never()).evaluateForceUnclaim(eq("me"), anyList());
    }

    @Test
    void oneEvaluateCallSetsForceFlagOnlyOnHeldForeignRows() {
        TaskInfo held = poolTask("t-held", "alice", false);
        TaskInfo mine = poolTask("t-mine", "me", true);
        when(adminCenterClient.evaluateForceUnclaim(eq("leader"), anyList()))
                .thenReturn(Map.of("t-held", true));

        annotator.annotate(List.of(held, mine), "leader");

        assertThat(held.isCanForceUnclaim()).isTrue();
        assertThat(mine.isCanForceUnclaim()).isFalse();
        verify(adminCenterClient).evaluateForceUnclaim(eq("leader"), anyList());
    }

    @Test
    void failClosedWhenAdminCenterReturnsEmpty() {
        TaskInfo held = poolTask("t-held", "alice", false);
        when(adminCenterClient.evaluateForceUnclaim(eq("u1"), anyList())).thenReturn(Map.of());

        annotator.annotate(List.of(held), "u1");

        assertThat(held.isCanForceUnclaim()).isFalse();
    }

    private static TaskInfo poolTask(String taskId, String assignee, boolean claimedByCurrentUser) {
        return TaskInfo.builder()
                .taskId(taskId)
                .bpmnAssigneeType("BU_ROLE")
                .assignee(assignee)
                .claimedByCurrentUser(claimedByCurrentUser)
                .bpmnBusinessUnitId("bu-1")
                .bpmnRoleIds(List.of("role-1"))
                .build();
    }
}
