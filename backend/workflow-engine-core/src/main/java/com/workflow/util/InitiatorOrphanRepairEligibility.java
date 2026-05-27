package com.workflow.util;

import java.util.Locale;

/**
 * Determines whether the "initiator orphan task repair" is allowed to idempotently
 * write unassigned tasks back to the process initiator.
 * <p>
 * Only allowed when the BPMN node has initiator-handling semantics; non-initiator
 * nodes such as BU_ROLE and HIERARCHY must not be repaired, otherwise the task
 * would be incorrectly assigned to the initiator when querying the to-do list
 * (see {@code TaskManagerComponent.appendUnassignedInitiatorTasks}).
 * </p>
 */
public final class InitiatorOrphanRepairEligibility {

    private InitiatorOrphanRepairEligibility() {
    }

    /**
     * @param assigneeTypeExtension custom:property assigneeType, may be null/blank
     * @param flowableUserTaskAssignee Flowable UserTask standard assignee expression
     *        (used only when the extension does not specify assigneeType)
     * @return whether the unassigned task should be repaired to the current initiator user
     */
    public static boolean shouldRepair(String assigneeTypeExtension, String flowableUserTaskAssignee) {
        if (assigneeTypeExtension != null && !assigneeTypeExtension.isBlank()) {
            return isInitiatorAssigneeType(assigneeTypeExtension);
        }
        if (flowableUserTaskAssignee != null && !flowableUserTaskAssignee.isBlank()) {
            return isInitiatorExpression(flowableUserTaskAssignee.trim());
        }
        return false;
    }

    private static boolean isInitiatorAssigneeType(String raw) {
        if (raw == null) {
            return false;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        return "INITIATOR".equals(u) || "PROCESS_INITIATOR".equals(u);
    }

    /**
     * Aligned with {@link com.workflow.listener.TaskAssignmentListener#isInitiatorExpression(String)}.
     */
    private static boolean isInitiatorExpression(String expr) {
        if (expr == null || expr.isEmpty()) {
            return false;
        }
        String e = expr.trim();
        if ("${initiator}".equals(e) || "${initiatorId}".equalsIgnoreCase(e)) {
            return true;
        }
        return e.matches("(?i)^\\$\\{\\s*initiator\\s*}$") || e.matches("(?i)^\\$\\{\\s*initiatorId\\s*}$");
    }
}
