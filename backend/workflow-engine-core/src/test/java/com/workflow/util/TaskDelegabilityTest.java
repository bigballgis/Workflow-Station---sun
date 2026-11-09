package com.workflow.util;

import com.workflow.entity.ExtendedTaskInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A delegation is kept on the extended row, so only a held, open task that has one can be delegated (D-56). */
class TaskDelegabilityTest {

    @Test
    void aHeldTaskWithAnOpenExtendedRowCanBeDelegated() {
        assertThat(TaskDelegability.isDelegable("holder", ExtendedTaskInfo.builder().status("ASSIGNED").build())).isTrue();
    }

    @Test
    void noRowNoHolderOrCompletedMeansNo() {
        assertThat(TaskDelegability.isDelegable("holder", null)).isFalse();
        assertThat(TaskDelegability.isDelegable(" ", ExtendedTaskInfo.builder().status("ASSIGNED").build())).isFalse();
        assertThat(TaskDelegability.isDelegable("holder", ExtendedTaskInfo.builder().status("COMPLETED").build())).isFalse();
    }
}
