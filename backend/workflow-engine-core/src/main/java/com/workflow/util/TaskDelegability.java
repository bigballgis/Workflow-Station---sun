package com.workflow.util;

import com.workflow.entity.ExtendedTaskInfo;
import org.springframework.util.StringUtils;

/**
 * Whether a task can be delegated for this one task (platform access model D-56): the delegation is kept on the task's
 * extended row, so a task without one (today: any task that is not a multi-instance row) cannot be; neither can an
 * unclaimed or completed one. Task DTOs carry the answer so the Portal offers "Delegate" only where it works.
 */
public final class TaskDelegability {

    private TaskDelegability() {
    }

    public static boolean isDelegable(String assignee, ExtendedTaskInfo row) {
        return row != null && StringUtils.hasText(assignee) && !row.isCompleted();
    }
}
