package com.workflow.util;

import com.platform.security.service.LostEntryTaskPolicy.PoolKind;
import com.workflow.enums.AssigneeType;

import java.util.Locale;
import java.util.Set;

/**
 * How a task's people were chosen (platform access model §7.3, D-17, D-39). The engine decides it once; task DTOs
 * carry it so the Portal does not repeat the rule.
 *
 * <p>Membership: BPMN BU + Role and hierarchy-role nodes, manual BU + Role (it lands in candidates), MI rows assigned by
 * role, and legacy virtual-group tasks (candidate groups, MI virtual-group rows, or BPMN {@code VIRTUAL_GROUP} nodes
 * whose members were written as candidates). Any other task with candidates is a list of named people; a task with
 * none is assigned to one person.</p>
 */
public final class TaskPoolKinds {

    private static final Set<AssigneeType> MEMBERSHIP_TYPES = Set.of(AssigneeType.BU_ROLE, AssigneeType.HIERARCHY_ROLE);
    private static final Set<String> LEGACY_VIRTUAL_GROUP_TYPES = Set.of("VIRTUAL_GROUP", "VIRTUALGROUP");

    private TaskPoolKinds() {
    }

    /**
     * @param rawBpmnAssigneeType the node's {@code assigneeType} extension as written
     * @param miRoleRow           the task is an MI row assigned by role
     * @param virtualGroup        the task has candidate groups or is an MI virtual-group row
     * @param hasCandidateUsers   the task has candidate users
     */
    public static PoolKind of(String rawBpmnAssigneeType, boolean miRoleRow, boolean virtualGroup,
                              boolean hasCandidateUsers) {
        if (virtualGroup || miRoleRow) {
            return PoolKind.MEMBERSHIP;
        }
        AssigneeType type = AssigneeType.fromCode(rawBpmnAssigneeType);
        boolean legacyVirtualGroupNode = rawBpmnAssigneeType != null
                && LEGACY_VIRTUAL_GROUP_TYPES.contains(rawBpmnAssigneeType.trim().toUpperCase(Locale.ROOT));
        if ((type != null && MEMBERSHIP_TYPES.contains(type)) || legacyVirtualGroupNode
                || (type == AssigneeType.MANUAL_ASSIGN && hasCandidateUsers)) {
            return PoolKind.MEMBERSHIP;
        }
        return hasCandidateUsers ? PoolKind.PEOPLE : PoolKind.NONE;
    }
}
