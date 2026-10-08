package com.portal.component;

import com.portal.entity.ProcessInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Decides which terminal status a finished process instance lands on.
 *
 * <h2>Why this exists</h2>
 * A rejection and an approval travel the same road: both complete their task, and the BPMN gateway
 * then routes the token to whichever end event the designer wired up. Nothing about that told the
 * portal which outcome occurred, so every finished instance was stored as {@code COMPLETED} and
 * "was it rejected?" was answered downstream by matching the end event's <em>name</em> against the
 * words "rejected", "拒绝" and "驳回".
 *
 * <p>That guess fails on any Function Unit whose designer named the node something else
 * ("Declined", "Not approved", "不通过"), and it left the portal's REJECTED filter tab matching
 * nothing at all. It also cannot answer the question a called Function Unit now has to answer —
 * "did my child end in rejection, so that I must fail too?" — with any reliability.
 *
 * <p>So the outcome is read from the {@code approvalStatus} process variable, which
 * {@link TaskApprovalCompletionComponent} already writes on every approve/reject. That is a value
 * the platform itself sets, not a label a designer is free to rename.
 */
@Slf4j
@Component
public class ProcessTerminalStatusResolver {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";

    /** Process variable written by the platform whenever a task is approved or rejected. */
    private static final String APPROVAL_STATUS_VARIABLE = "approvalStatus";
    private static final String REJECTED_VALUE = "REJECTED";

    /**
     * Statuses that mean "this instance is over". Used to skip work on instances that already
     * ended, and to grey out tasks that can no longer be acted on.
     */
    private static final Set<String> TERMINAL_STATUSES =
            Set.of(STATUS_COMPLETED, STATUS_REJECTED, STATUS_WITHDRAWN);

    /**
     * The status a just-finished instance should be stored with.
     *
     * @return {@link #STATUS_REJECTED} when the last approval decision was a rejection,
     *         otherwise {@link #STATUS_COMPLETED}
     */
    public String resolveFinishedStatus(ProcessInstance instance) {
        return endedInRejection(instance) ? STATUS_REJECTED : STATUS_COMPLETED;
    }

    /**
     * Whether this instance finished because someone rejected it.
     *
     * <p>Absent or unreadable variables mean "not a rejection": a process can finish without any
     * approval step at all, and treating that as a rejection would mislabel ordinary completions.
     */
    public boolean endedInRejection(ProcessInstance instance) {
        if (instance == null) {
            return false;
        }
        Map<String, Object> variables = instance.getVariables();
        if (variables == null) {
            return false;
        }
        Object approvalStatus = variables.get(APPROVAL_STATUS_VARIABLE);
        return approvalStatus instanceof String status && REJECTED_VALUE.equalsIgnoreCase(status.trim());
    }

    /** Whether the status means the instance has ended, however it ended. */
    public static boolean isTerminal(String status) {
        return status != null && TERMINAL_STATUSES.contains(status);
    }

    /** Whether the instance is still in flight and may be acted on. */
    public static boolean isRunning(String status) {
        return STATUS_RUNNING.equals(status);
    }
}
