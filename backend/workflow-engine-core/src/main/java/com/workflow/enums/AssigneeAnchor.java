package com.workflow.enums;

/**
 * In organizational assignments, the reference point for resolving BU chain / manager:
 * initiator, or the physically most recent completed user task assignee.
 * <p>For details, see {@code .kiro/docs/assignee-type-convergence.md}.</p>
 */
public enum AssigneeAnchor {

    INITIATOR("INITIATOR"),

    LAST_TASK_ASSIGNEE("LAST_TASK_ASSIGNEE");

    private final String code;

    AssigneeAnchor(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static AssigneeAnchor fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return INITIATOR;
        }
        String u = raw.trim().toUpperCase();
        if ("LAST_TASK_ASSIGNEE".equals(u) || "LAST".equals(u) || "CURRENT".equals(u)) {
            return LAST_TASK_ASSIGNEE;
        }
        return INITIATOR;
    }
}
