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
class ClaimBatchComponentTest {

    @Mock
    private TaskQueryComponent taskQueryComponent;
    @Mock
    private TaskProcessComponent taskProcessComponent;

    private ClaimBatchComponent component;

    @BeforeEach
    void setUp() {
        component = new ClaimBatchComponent(taskQueryComponent, taskProcessComponent);
    }

    @Test
    void claimsOnlyClaimableAndReportsRemaining() {
        when(taskQueryComponent.listClaimPoolTasks("u1")).thenReturn(List.of(
                pool("free-1", true),
                pool("held", false),
                pool("free-2", true),
                pool("mine", false)));
        when(taskProcessComponent.claimTask(any(), eq("u1"), eq("alice")))
                .thenReturn(TaskInfo.builder().build());

        ClaimBatchResponse result = component.claimNextBatch("u1", "alice", new ClaimBatchRequest(List.of()));

        assertThat(result.claimed()).isEqualTo(2);
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isZero();
        assertThat(result.remaining()).isZero();
        assertThat(result.attemptedTaskIds()).containsExactly("free-1", "free-2");
        verify(taskProcessComponent).claimTask("free-1", "u1", "alice");
        verify(taskProcessComponent).claimTask("free-2", "u1", "alice");
        verify(taskProcessComponent, never()).claimTask(eq("held"), any(), any());
    }

    @Test
    void excludesAlreadyAttemptedAndCounts403AsSkipped() {
        when(taskQueryComponent.listClaimPoolTasks("u1")).thenReturn(List.of(
                pool("a", true), pool("b", true)));
        when(taskProcessComponent.claimTask(eq("b"), eq("u1"), any()))
                .thenThrow(new PortalException("403", "already held"));

        ClaimBatchResponse result = component.claimNextBatch("u1", "n", new ClaimBatchRequest(List.of("a")));

        assertThat(result.attemptedTaskIds()).containsExactly("b");
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.claimed()).isZero();
        assertThat(result.remaining()).isZero();
    }

    @Test
    void capsOneBatchAtLimitAndLeavesRemaining() {
        List<TaskInfo> many = new ArrayList<>();
        for (int i = 0; i < ClaimBatchComponent.BATCH_LIMIT + 3; i++) {
            many.add(pool("t-" + i, true));
        }
        when(taskQueryComponent.listClaimPoolTasks("u1")).thenReturn(many);
        when(taskProcessComponent.claimTask(any(), eq("u1"), any())).thenReturn(TaskInfo.builder().build());

        ClaimBatchResponse result = component.claimNextBatch("u1", "n", new ClaimBatchRequest(List.of()));

        assertThat(result.claimed()).isEqualTo(ClaimBatchComponent.BATCH_LIMIT);
        assertThat(result.remaining()).isEqualTo(3);
        verify(taskProcessComponent, times(ClaimBatchComponent.BATCH_LIMIT)).claimTask(any(), eq("u1"), any());
    }

    @Test
    void rejectsExcludeListOverMax() {
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < ClaimBatchRequest.MAX_EXCLUDE_IDS + 1; i++) {
            tooMany.add("t-" + i);
        }
        org.junit.jupiter.api.Assertions.assertThrows(PortalException.class,
                () -> component.claimNextBatch("u1", "n", new ClaimBatchRequest(tooMany)));
        verify(taskProcessComponent, never()).claimTask(any(), any(), any());
    }

    @Test
    void includeTaskIdsClaimsOnlyThoseAndKeepsRequestOrder() {
        when(taskQueryComponent.listClaimPoolTasks("u1")).thenReturn(List.of(
                pool("free-1", true),
                pool("held", false),
                pool("free-2", true),
                pool("free-3", true)));
        when(taskProcessComponent.claimTask(any(), eq("u1"), eq("alice")))
                .thenReturn(TaskInfo.builder().build());

        ClaimBatchResponse result = component.claimNextBatch(
                "u1", "alice", new ClaimBatchRequest(List.of(), List.of("free-3", "held", "free-1")));

        assertThat(result.claimed()).isEqualTo(2);
        assertThat(result.attemptedTaskIds()).containsExactly("free-3", "free-1");
        verify(taskProcessComponent).claimTask("free-3", "u1", "alice");
        verify(taskProcessComponent).claimTask("free-1", "u1", "alice");
        verify(taskProcessComponent, never()).claimTask(eq("free-2"), any(), any());
        verify(taskProcessComponent, never()).claimTask(eq("held"), any(), any());
    }

    @Test
    void rejectsIncludeListOverMax() {
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < ClaimBatchRequest.MAX_INCLUDE_IDS + 1; i++) {
            tooMany.add("t-" + i);
        }
        org.junit.jupiter.api.Assertions.assertThrows(PortalException.class,
                () -> component.claimNextBatch("u1", "n", new ClaimBatchRequest(List.of(), tooMany)));
        verify(taskProcessComponent, never()).claimTask(any(), any(), any());
    }

    private static TaskInfo pool(String id, boolean claimable) {
        return TaskInfo.builder().taskId(id).claimable(claimable).claimPoolTask(true).build();
    }
}
