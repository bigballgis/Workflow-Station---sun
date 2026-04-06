package com.workflow.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InitiatorOrphanRepairEligibilityTest {

    @Test
    void buRole_shouldNotRepair() {
        assertThat(InitiatorOrphanRepairEligibility.shouldRepair("BU_ROLE", null)).isFalse();
        assertThat(InitiatorOrphanRepairEligibility.shouldRepair("FIXED_BU_ROLE", null)).isFalse();
        assertThat(InitiatorOrphanRepairEligibility.shouldRepair("HIERARCHY_ROLE", null)).isFalse();
    }

    @Test
    void initiatorTypes_shouldRepair() {
        assertThat(InitiatorOrphanRepairEligibility.shouldRepair("INITIATOR", null)).isTrue();
        assertThat(InitiatorOrphanRepairEligibility.shouldRepair("Process_Initiator", null)).isTrue();
        assertThat(InitiatorOrphanRepairEligibility.shouldRepair("  INITIATOR  ", null)).isTrue();
    }

    @Test
    void blankAssigneeType_withFlowableInitiatorExpression_shouldRepair() {
        assertThat(InitiatorOrphanRepairEligibility.shouldRepair(null, "${initiator}")).isTrue();
        assertThat(InitiatorOrphanRepairEligibility.shouldRepair("  ", "${initiatorId}")).isTrue();
    }

    @Test
    void blankAssigneeType_withoutInitiatorExpression_shouldNotRepair() {
        assertThat(InitiatorOrphanRepairEligibility.shouldRepair(null, null)).isFalse();
        assertThat(InitiatorOrphanRepairEligibility.shouldRepair("", "")).isFalse();
    }

    @Test
    void explicitInitiatorType_ignoresFlowableAssignee() {
        assertThat(InitiatorOrphanRepairEligibility.shouldRepair("INITIATOR", "${someOther}")).isTrue();
    }
}
