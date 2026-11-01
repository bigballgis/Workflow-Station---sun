package com.workflow.util;

import java.util.Map;
import java.util.Set;
import org.flowable.engine.TaskService;

/** Native task-bound outcome evidence, committed in the same transaction as completion. */
public final class TaskCompletionComments {
    public static final String OUTCOME_PREFIX = "workflow-completion:";
    private static final Set<String> OUTCOMES = Set.of("APPROVE", "REJECT", "SUBMIT");

    private TaskCompletionComments() { }

    public static void appendUserComment(TaskService tasks, String taskId, String processId, Map<String,Object> variables) {
        if (variables == null) return;
        Object comment = variables.get("approverComments");
        if (comment != null && !comment.toString().isBlank()) {
            tasks.addComment(taskId, processId, comment.toString());
        }
    }

    public static void append(TaskService tasks, String taskId, String processId, Map<String,Object> variables) {
        if (variables == null) return;
        if (variables.get("action") instanceof String outcome && OUTCOMES.contains(outcome)) {
            // Empty text keeps older readers from displaying audit metadata as a user comment.
            tasks.addComment(taskId, processId, OUTCOME_PREFIX + outcome, "");
        }
    }

    public static String requireOutcome(String outcome) {
        if (outcome == null || !OUTCOMES.contains(outcome)) {
            throw new IllegalStateException("Invalid task completion outcome record");
        }
        return outcome;
    }
}
