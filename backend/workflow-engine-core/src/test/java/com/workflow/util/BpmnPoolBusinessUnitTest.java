package com.workflow.util;

import com.workflow.enums.AssigneeType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** One rule for the BU of a BU + Role node: assignment, the To Do workspace filter and the pool resync read it. */
class BpmnPoolBusinessUnitTest {

    @Test
    void theBusinessUnitExtensionWins() {
        assertThat(BpmnPoolBusinessUnit.resolve(AssigneeType.BU_ROLE, " FIN ", "OLD")).isEqualTo("FIN");
    }

    @Test
    void olderBuRoleBpmnKeepsTheBusinessUnitInAssigneeValue() {
        assertThat(BpmnPoolBusinessUnit.resolve(AssigneeType.BU_ROLE, null, "FIN")).isEqualTo("FIN");
        assertThat(BpmnPoolBusinessUnit.resolve(AssigneeType.BU_ROLE, " ", "FIN")).isEqualTo("FIN");
    }

    @Test
    void otherNodesDoNotTakeAssigneeValueAsABusinessUnit() {
        assertThat(BpmnPoolBusinessUnit.resolve(AssigneeType.ASSIGNEE_FROM_VARIABLE, null, "applicant")).isNull();
        assertThat(BpmnPoolBusinessUnit.resolve(null, null, "x")).isNull();
    }
}
