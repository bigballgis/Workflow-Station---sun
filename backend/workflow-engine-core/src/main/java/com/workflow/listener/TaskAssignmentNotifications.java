package com.workflow.listener;

import com.platform.messaging.support.NotificationDispatchHelper;
import com.workflow.assignment.AssignmentCreationBuffer;

/** Hold creation notifications until the final responsibility-filtered assignment is known. */
public final class TaskAssignmentNotifications {
    private TaskAssignmentNotifications() { }

    public static void send(NotificationDispatchHelper helper, Notice notice) {
        if (notice.userId() == null || notice.userId().isBlank() || helper == null) return;
        // FALLBACK(ux): existing task-name/process-label display behavior; never used for a write decision.
        String label = notice.taskName() != null && !notice.taskName().isBlank() ? notice.taskName() : notice.taskId();
        String process = notice.processInstanceId() != null ? notice.processInstanceId() : "-";
        String title = notice.candidate() ? "New Candidate Task" : "New Task";
        String body = notice.candidate()
                ? String.format("You have been added as a candidate for task \"%s\". You can claim it from your pending tasks. Process: %s", label, process)
                : String.format("You have a new task \"%s\". Process: %s", label, process);
        Runnable publish = () -> helper.publishToUserAfterCommit(notice.userId().trim(), "TASK", title, body,
                "/tasks/" + notice.taskId(), "workflow-engine");
        if (!AssignmentCreationBuffer.defer(notice.taskId(), notice.userId().trim(), publish)) publish.run();
    }

    public record Notice(String userId, String taskId, String taskName, String processInstanceId, boolean candidate) { }
}
