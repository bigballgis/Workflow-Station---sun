package com.portal.component;

import com.portal.dto.ClaimBatchRequest;
import com.portal.dto.ClaimBatchResponse;
import com.portal.dto.TaskInfo;
import com.portal.exception.PortalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnclaimBatchComponentTest {

    @Mock
    private TaskQueryComponent taskQueryComponent;
    @Mock
    private TaskProcessComponent taskProcessComponent;

    private UnclaimBatchComponent component;

    @BeforeEach
    void setUp() {
        component = new UnclaimBatchComponent(taskQueryComponent, taskProcessComponent);
    }

    @Test
    void unclaimsOnlyOwnHoldsAndSkipsColleagueHolds() {
        when(taskQueryComponent.listMergedTodoTasks("u1")).thenReturn(List.of(
                mine("mine-1"),
                colleague("other"),
                free("free"),
                mine("mine-2")));
        when(taskProcessComponent.unclaimTask(any(), eq("u1"), any(), any(), eq("alice")))
                .thenReturn(TaskInfo.builder().build());

        ClaimBatchResponse result = component.unclaimNextBatch("u1", "alice", new ClaimBatchRequest(List.of()));

        assertThat(result.claimed()).isEqualTo(2);
        assertThat(result.attemptedTaskIds()).containsExactly("mine-1", "mine-2");
        verify(taskProcessComponent).unclaimTask("mine-1", "u1", "VIRTUAL_GROUP", "u1", "alice");
        verify(taskProcessComponent).unclaimTask("mine-2", "u1", "VIRTUAL_GROUP", "u1", "alice");
        verify(taskProcessComponent, never()).unclaimTask(eq("other"), any(), any(), any(), any());
        verify(taskProcessComponent, never()).unclaimTask(eq("free"), any(), any(), any(), any());
    }

    @Test
    void excludesAlreadyAttempted() {
        when(taskQueryComponent.listMergedTodoTasks("u1")).thenReturn(List.of(mine("a"), mine("b")));
        when(taskProcessComponent.unclaimTask(eq("b"), eq("u1"), any(), any(), any()))
                .thenReturn(TaskInfo.builder().build());

        ClaimBatchResponse result = component.unclaimNextBatch("u1", "n", new ClaimBatchRequest(List.of("a")));

        assertThat(result.attemptedTaskIds()).containsExactly("b");
        assertThat(result.claimed()).isEqualTo(1);
        verify(taskProcessComponent, never()).unclaimTask(eq("a"), any(), any(), any(), any());
    }

    @Test
    void missingAssignmentMetadataCountsAsFailedNotForceUnclaim() {
        TaskInfo incomplete = TaskInfo.builder()
                .taskId("bare")
                .claimedByCurrentUser(true)
                .claimPoolTask(true)
                .build();
        when(taskQueryComponent.listMergedTodoTasks("u1")).thenReturn(List.of(incomplete));

        ClaimBatchResponse result = component.unclaimNextBatch("u1", "n", new ClaimBatchRequest(List.of()));

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.claimed()).isZero();
        verify(taskProcessComponent, never()).unclaimTask(any(), any(), any(), any(), any());
    }

    @Test
    void counts403AsSkipped() {
        when(taskQueryComponent.listMergedTodoTasks("u1")).thenReturn(List.of(mine("a")));
        when(taskProcessComponent.unclaimTask(eq("a"), eq("u1"), any(), any(), any()))
                .thenThrow(new PortalException("403", "not holder"));

        ClaimBatchResponse result = component.unclaimNextBatch("u1", "n", new ClaimBatchRequest(List.of()));

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.claimed()).isZero();
    }

    @Test
    void capsOneBatchAtLimitAndLeavesRemaining() {
        List<TaskInfo> many = new ArrayList<>();
        for (int i = 0; i < ClaimBatchComponent.BATCH_LIMIT + 2; i++) {
            many.add(mine("t-" + i));
        }
        when(taskQueryComponent.listMergedTodoTasks("u1")).thenReturn(many);
        when(taskProcessComponent.unclaimTask(any(), eq("u1"), any(), any(), any()))
                .thenReturn(TaskInfo.builder().build());

        ClaimBatchResponse result = component.unclaimNextBatch("u1", "n", new ClaimBatchRequest(List.of()));

        assertThat(result.claimed()).isEqualTo(ClaimBatchComponent.BATCH_LIMIT);
        assertThat(result.remaining()).isEqualTo(2);
        verify(taskProcessComponent, times(ClaimBatchComponent.BATCH_LIMIT))
                .unclaimTask(any(), eq("u1"), any(), any(), any());
    }

    @Test
    void includeTaskIdsUnclaimsOnlyThoseHolds() {
        when(taskQueryComponent.listMergedTodoTasks("u1")).thenReturn(List.of(
                mine("mine-1"),
                colleague("other"),
                mine("mine-2")));
        when(taskProcessComponent.unclaimTask(any(), eq("u1"), any(), any(), eq("alice")))
                .thenReturn(TaskInfo.builder().build());

        ClaimBatchResponse result = component.unclaimNextBatch(
                "u1", "alice", new ClaimBatchRequest(List.of(), List.of("other", "mine-2")));

        assertThat(result.claimed()).isEqualTo(1);
        assertThat(result.attemptedTaskIds()).containsExactly("mine-2");
        verify(taskProcessComponent).unclaimTask("mine-2", "u1", "VIRTUAL_GROUP", "u1", "alice");
        verify(taskProcessComponent, never()).unclaimTask(eq("mine-1"), any(), any(), any(), any());
        verify(taskProcessComponent, never()).unclaimTask(eq("other"), any(), any(), any(), any());
    }

    @Test
    void unclaimsMineOnlyBuRoleHoldNotInClaimPool() {
        TaskInfo mineOnly = TaskInfo.builder()
                .taskId("sole-hold")
                .assignmentType("USER")
                .assignee("u1")
                .claimedByCurrentUser(true)
                .claimPoolTask(true)
                .build();
        when(taskQueryComponent.listMergedTodoTasks("u1")).thenReturn(List.of(mineOnly));
        when(taskProcessComponent.unclaimTask(any(), eq("u1"), any(), any(), eq("alice")))
                .thenReturn(TaskInfo.builder().build());

        ClaimBatchResponse result = component.unclaimNextBatch("u1", "alice", new ClaimBatchRequest(List.of()));

        assertThat(result.claimed()).isEqualTo(1);
        verify(taskProcessComponent).unclaimTask("sole-hold", "u1", "USER", "u1", "alice");
    }

    private static TaskInfo mine(String id) {
        return TaskInfo.builder()
                .taskId(id)
                .assignmentType("VIRTUAL_GROUP")
                .assignee("u1")
                .claimedByCurrentUser(true)
                .claimPoolTask(true)
                .build();
    }

    private static TaskInfo colleague(String id) {
        return TaskInfo.builder()
                .taskId(id)
                .assignmentType("VIRTUAL_GROUP")
                .assignee("other")
                .claimedByCurrentUser(false)
                .claimPoolTask(true)
                .build();
    }

    private static TaskInfo free(String id) {
        return TaskInfo.builder()
                .taskId(id)
                .claimable(true)
                .claimedByCurrentUser(false)
                .claimPoolTask(true)
                .build();
    }
}
