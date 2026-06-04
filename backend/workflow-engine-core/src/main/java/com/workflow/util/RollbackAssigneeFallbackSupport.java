package com.workflow.util;

/**
 * Process variables set during {@code returnTask} so {@link com.workflow.listener.TaskAssignmentListener}
 * can assign the rollback target to the previous handler when BPMN rules resolve nobody.
 */
public final class RollbackAssigneeFallbackSupport {

    public static final String VAR_FALLBACK_ACTIVE = "_rollbackAssigneeFallback";
    public static final String VAR_TARGET_ACTIVITY_ID = "_rollbackTargetActivityId";

    private RollbackAssigneeFallbackSupport() {
    }
}
