package com.workflow.component;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** An MI row assigned by role carries every role it names to the Portal, not only the first (WP8a review). */
class TaskInfoAssemblerMiRoleCodesTest {

    @Test
    void everyRoleOfAMultiRoleRowIsKeptInOrder() {
        assertThat(TaskInfoAssembler.miRoleCodesOf(List.of("CLERK", " APPROVER "))).containsExactly("CLERK", "APPROVER");
    }

    @Test
    void aSingleCodeIsAListOfOne() {
        assertThat(TaskInfoAssembler.miRoleCodesOf("CLERK")).containsExactly("CLERK");
    }

    @Test
    void blankEntriesAreDroppedAndNoRoleIsNull() {
        assertThat(TaskInfoAssembler.miRoleCodesOf(Arrays.asList("", null, "CLERK"))).containsExactly("CLERK");
        assertThat(TaskInfoAssembler.miRoleCodesOf(List.of())).isNull();
        assertThat(TaskInfoAssembler.miRoleCodesOf(" ")).isNull();
        assertThat(TaskInfoAssembler.miRoleCodesOf(null)).isNull();
    }
}
