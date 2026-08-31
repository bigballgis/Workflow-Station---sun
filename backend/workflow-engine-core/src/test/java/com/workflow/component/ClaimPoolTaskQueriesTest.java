package com.workflow.component;

import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("ClaimPoolTaskQueries")
class ClaimPoolTaskQueriesTest {

    @Test
    void keepsClaimedCandidateTasksVisible() {
        TaskQuery query = mock(TaskQuery.class, Answers.RETURNS_SELF);

        ClaimPoolTaskQueries.visibleIncludingClaimed(query, "user-1");

        verify(query).active();
        verify(query).taskCandidateUser("user-1");
        verify(query).ignoreAssigneeValue();
    }
}
