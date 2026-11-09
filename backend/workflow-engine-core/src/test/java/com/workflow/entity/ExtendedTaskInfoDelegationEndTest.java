package com.workflow.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A single-task delegation is its holder's own arrangement: it ends when the task changes hands or its hold ends, and
 * nobody gets it back by holding the task later (D-59). Only a repeated claim by the same holder keeps it.
 */
class ExtendedTaskInfoDelegationEndTest {

    @Test
    void anotherHolderEndsIt() {
        ExtendedTaskInfo row = delegatedByA();

        row.claimTask("b");

        assertThat(row.isDelegated()).isFalse();
        assertThat(row.getDelegatedBy()).isNull();
        assertThat(row.getClaimedBy()).isEqualTo("b");
    }

    @Test
    void theSameHolderClaimingAgainKeepsIt() {
        ExtendedTaskInfo row = delegatedByA();

        row.claimTask("a");

        assertThat(row.getDelegatedTo()).isEqualTo("c");
    }

    @Test
    void theDelegatorClaimingATaskThatWentBackToThePoolDoesNotGetItBack() {
        // A row released before D-59 cleared delegations on release: back in the pool, still carrying one.
        ExtendedTaskInfo row = ExtendedTaskInfo.builder().taskId("t1").build();
        row.delegateTask("c", "a", "leave");

        row.claimTask("a");

        assertThat(row.isDelegated()).isFalse();
        assertThat(row.getClaimedBy()).isEqualTo("a");
    }

    @Test
    void aReleaseEndsItAndSaysWhoTheDelegateWas() {
        ExtendedTaskInfo row = delegatedByA();

        assertThat(row.endDelegationUnlessSameHolder(null)).isEqualTo("c");
        assertThat(row.isDelegated()).isFalse();
        assertThat(row.endDelegationUnlessSameHolder(null)).isNull();
    }

    @Test
    void aDelegationToABuRoleIsNamedByItsPair() {
        ExtendedTaskInfo row = ExtendedTaskInfo.builder().taskId("t1").claimedBy("a").build();
        row.delegateTaskToBuRole("FIN", "MANAGER", "a", "leave");

        assertThat(row.endDelegationUnlessSameHolder("b")).isEqualTo("FIN / MANAGER");
    }

    private static ExtendedTaskInfo delegatedByA() {
        ExtendedTaskInfo row = ExtendedTaskInfo.builder().taskId("t1").claimedBy("a").build();
        row.delegateTask("c", "a", "leave");
        return row;
    }
}
