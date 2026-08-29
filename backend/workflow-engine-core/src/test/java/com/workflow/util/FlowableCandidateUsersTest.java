package com.workflow.util;

import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowableCandidateUsersTest {

    @Mock
    private TaskService taskService;

    @Test
    void collectsDistinctCandidateUserIdsInLinkOrder() {
        IdentityLink u1 = mock(IdentityLink.class);
        when(u1.getType()).thenReturn("candidate");
        when(u1.getUserId()).thenReturn("c84a748d-8ff5-43b0-a36a-d6665b78d80f");
        IdentityLink group = mock(IdentityLink.class);
        when(group.getType()).thenReturn("candidate");
        when(group.getUserId()).thenReturn(null);
        IdentityLink dup = mock(IdentityLink.class);
        when(dup.getType()).thenReturn("candidate");
        when(dup.getUserId()).thenReturn("c84a748d-8ff5-43b0-a36a-d6665b78d80f");
        IdentityLink u2 = mock(IdentityLink.class);
        when(u2.getType()).thenReturn("candidate");
        when(u2.getUserId()).thenReturn("e30f1bdf-a830-4481-922e-d5d1439dfec0");
        IdentityLink assignee = mock(IdentityLink.class);
        when(assignee.getType()).thenReturn("assignee");

        when(taskService.getIdentityLinksForTask("t1"))
                .thenReturn(List.of(u1, group, dup, u2, assignee));

        assertThat(FlowableCandidateUsers.userIds(taskService, "t1")).containsExactly(
                "c84a748d-8ff5-43b0-a36a-d6665b78d80f",
                "e30f1bdf-a830-4481-922e-d5d1439dfec0");
    }

    @Test
    void emptyWhenNoTaskOrNoUserCandidates() {
        assertThat(FlowableCandidateUsers.userIds(taskService, null)).isEmpty();
        when(taskService.getIdentityLinksForTask("t2")).thenReturn(List.of());
        assertThat(FlowableCandidateUsers.userIds(taskService, "t2")).isEmpty();
    }
}
