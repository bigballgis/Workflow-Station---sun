package com.workflow.util;

import com.workflow.enums.AssigneeAnchor;
import com.workflow.enums.AssigneeType;

/** The existing designer anchor setting and legacy assignment-type aliases share one interpretation. */
public final class AssigneeAnchorPolicy {
    private AssigneeAnchorPolicy() { }
    public static AssigneeAnchor resolve(String raw,AssigneeType type,String configured) {
        if (configured!=null && !configured.isBlank()) return AssigneeAnchor.fromCode(configured);
        if (type!=AssigneeType.HIERARCHY_ROLE || raw==null) return AssigneeAnchor.INITIATOR;
        return switch (raw.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "CURRENT_BU_ROLE","CURRENT_PARENT_BU_ROLE","DEPT_OTHERS","DEPTOTHERS","PARENT_DEPT","PARENTDEPT" -> AssigneeAnchor.LAST_TASK_ASSIGNEE;
            default -> AssigneeAnchor.INITIATOR;
        };
    }
}
