package com.workflow.util;

import com.workflow.enums.AssigneeType;
import org.springframework.util.StringUtils;

/**
 * The business unit of a BPMN BU + Role pool node. Older BPMN wrote it in {@code assigneeValue} instead of the
 * {@code businessUnitId} extension; task assignment, the To Do workspace filter and the claim-pool resync must all
 * read the same value.
 */
public final class BpmnPoolBusinessUnit {

    private BpmnPoolBusinessUnit() {
    }

    /** {@code businessUnitId} when set; for a BU + Role node without it, {@code assigneeValue}; otherwise null. */
    public static String resolve(AssigneeType type, String businessUnitId, String assigneeValue) {
        if (StringUtils.hasText(businessUnitId)) {
            return businessUnitId.trim();
        }
        if (type == AssigneeType.BU_ROLE && StringUtils.hasText(assigneeValue)) {
            return assigneeValue.trim();
        }
        return null;
    }
}
