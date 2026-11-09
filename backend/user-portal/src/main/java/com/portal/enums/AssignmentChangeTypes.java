package com.portal.enums;

/**
 * Change-history rows that are user assignment actions, not form fields.
 */
public final class AssignmentChangeTypes {

    public static final String FIELD_NAME = "claimed_by";

    /** {@code up_change_history.reason}: the system released the task because the holder no longer holds its BU + Role. */
    public static final String REASON_POOL_MEMBERSHIP_LOST = "POOL_MEMBERSHIP_LOST";

    /** {@code up_change_history.reason}: the system handed the task to a manager because its holder left (D-42). */
    public static final String REASON_ASSIGNEE_DEPARTED = "ASSIGNEE_DEPARTED";

    /** {@code up_change_history.field_name} of {@link ChangeType#DELEGATION_ENDED}: whom the delegation named. */
    public static final String DELEGATED_TO_FIELD = "delegated_to";

    /** {@code up_change_history.reason}: a single-task delegation ended because the task changed hands (D-59). */
    public static final String REASON_HOLDER_CHANGED = "HOLDER_CHANGED";

    /** {@code up_change_history.user_id} of changes the system made on its own. */
    public static final String SYSTEM_ACTOR = "system";

    private AssignmentChangeTypes() {
    }

    public static boolean isAssignmentAction(ChangeType changeType) {
        return changeType == ChangeType.CLAIM
                || changeType == ChangeType.UNCLAIM
                || changeType == ChangeType.FORCE_UNCLAIM
                || changeType == ChangeType.REASSIGN
                || changeType == ChangeType.MANAGER_REASSIGN
                || changeType == ChangeType.DELEGATION_ENDED;
    }

    public static boolean isAssignmentActionName(String changeType) {
        if (changeType == null || changeType.isBlank()) {
            return false;
        }
        try {
            return isAssignmentAction(ChangeType.valueOf(changeType.trim()));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
