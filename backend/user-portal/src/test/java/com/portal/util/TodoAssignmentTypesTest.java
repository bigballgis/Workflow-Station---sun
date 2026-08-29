package com.portal.util;

import com.portal.dto.TaskInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TodoAssignmentTypesTest {

    @Test
    void emptySelectionMatchesEverything() {
        TaskInfo user = TaskInfo.builder().assignmentType("USER").build();
        assertThat(TodoAssignmentTypes.matches(user, List.of())).isTrue();
        assertThat(TodoAssignmentTypes.matches(user, null)).isTrue();
    }

    @Test
    void buRoleMatchesClaimPoolOnly() {
        TaskInfo pool = TaskInfo.builder().assignmentType("VIRTUAL_GROUP").claimPoolTask(true).build();
        TaskInfo user = TaskInfo.builder().assignmentType("USER").claimPoolTask(false).build();
        assertThat(TodoAssignmentTypes.matchesOne(pool, "BU_ROLE")).isTrue();
        assertThat(TodoAssignmentTypes.matchesOne(user, "BU_ROLE")).isFalse();
        assertThat(TodoAssignmentTypes.matches(pool, List.of("USER"))).isFalse();
    }

    @Test
    void userOrBuRoleIsOr() {
        TaskInfo pool = TaskInfo.builder().assignmentType("VIRTUAL_GROUP").claimPoolTask(true).build();
        TaskInfo user = TaskInfo.builder().assignmentType("USER").build();
        assertThat(TodoAssignmentTypes.matches(pool, List.of("USER", "BU_ROLE"))).isTrue();
        assertThat(TodoAssignmentTypes.matches(user, List.of("USER", "BU_ROLE"))).isTrue();
    }

    @Test
    void delegatedMatchesAssignmentTypeWhenMixedWithUser() {
        TaskInfo delegated = TaskInfo.builder().assignmentType("DELEGATED").build();
        TaskInfo user = TaskInfo.builder().assignmentType("USER").build();
        assertThat(TodoAssignmentTypes.matches(delegated, List.of("USER", "DELEGATED"))).isTrue();
        assertThat(TodoAssignmentTypes.matches(user, List.of("USER", "DELEGATED"))).isTrue();
        assertThat(TodoAssignmentTypes.matches(delegated, List.of("USER"))).isFalse();
    }
}
