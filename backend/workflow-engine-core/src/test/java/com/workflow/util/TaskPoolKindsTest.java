package com.workflow.util;

import com.platform.security.service.LostEntryTaskPolicy.PoolKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** How a task's people were chosen (D-17, D-39); task DTOs carry it to the Portal. */
class TaskPoolKindsTest {

    @Test
    void membershipPools() {
        assertThat(TaskPoolKinds.of("BU_ROLE", false, false, true)).isEqualTo(PoolKind.MEMBERSHIP);
        assertThat(TaskPoolKinds.of("FIXED_BU_ROLE", false, false, true)).isEqualTo(PoolKind.MEMBERSHIP);
        assertThat(TaskPoolKinds.of("HIERARCHY_ROLE", false, false, true)).isEqualTo(PoolKind.MEMBERSHIP);
        assertThat(TaskPoolKinds.of("VIRTUAL_GROUP", false, false, true)).isEqualTo(PoolKind.MEMBERSHIP);
        assertThat(TaskPoolKinds.of("MANUAL_ASSIGN", false, false, true)).isEqualTo(PoolKind.MEMBERSHIP);
        assertThat(TaskPoolKinds.of("ELEMENT_VARIABLE", true, false, true)).isEqualTo(PoolKind.MEMBERSHIP);
        assertThat(TaskPoolKinds.of(null, false, true, false)).isEqualTo(PoolKind.MEMBERSHIP);
    }

    @Test
    void namedPeopleAndSinglePeople() {
        assertThat(TaskPoolKinds.of("ASSIGNEE_FROM_VARIABLE", false, false, true)).isEqualTo(PoolKind.PEOPLE);
        assertThat(TaskPoolKinds.of("MANUAL_ASSIGN", false, false, false)).isEqualTo(PoolKind.NONE);
        assertThat(TaskPoolKinds.of("PROCESS_INITIATOR", false, false, false)).isEqualTo(PoolKind.NONE);
    }

    @Test
    void aNodeWithoutAKnownAssigneeTypeIsJudgedByItsCandidates() {
        assertThat(TaskPoolKinds.of(null, false, false, false)).isEqualTo(PoolKind.NONE);
        assertThat(TaskPoolKinds.of("UNKNOWN", false, false, true)).isEqualTo(PoolKind.PEOPLE);
    }
}
