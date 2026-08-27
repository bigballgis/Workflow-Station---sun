package com.workflow.component;

import org.flowable.task.api.TaskQuery;

/**
 * Visibility for "Tasks to Claim": the user is a Flowable candidate, including rows another
 * member already claimed. Plain {@code taskCandidateUser} hides claimed tasks.
 */
public final class ClaimPoolTaskQueries {

    private ClaimPoolTaskQueries() {
    }

    public static TaskQuery visibleIncludingClaimed(TaskQuery query, String userId) {
        return query.active()
                .taskCandidateUser(userId)
                .ignoreAssigneeValue();
    }
}
