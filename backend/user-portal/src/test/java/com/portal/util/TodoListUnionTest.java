package com.portal.util;

import com.portal.dto.TaskInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TodoListUnionTest {

    @Test
    void poolRowWinsOnIdClashSoClaimFlagsSurvive() {
        TaskInfo mine = TaskInfo.builder().taskId("t1").assignmentType("VIRTUAL_GROUP").claimable(false).build();
        TaskInfo pool = TaskInfo.builder().taskId("t1").assignmentType("VIRTUAL_GROUP")
                .claimable(true).claimedByCurrentUser(false).build();
        TaskInfo onlyMine = TaskInfo.builder().taskId("t2").assignmentType("USER").build();
        TaskInfo heldByOther = TaskInfo.builder().taskId("t3").assignee("alice").claimable(false).build();

        List<TaskInfo> merged = TodoListUnion.merge(List.of(mine, onlyMine), List.of(pool, heldByOther));

        assertThat(merged).extracting(TaskInfo::getTaskId).containsExactly("t1", "t2", "t3");
        assertThat(merged.get(0).isClaimable()).isTrue();
    }

    @Test
    void nullAndBlankIdsAreDropped() {
        List<TaskInfo> merged = TodoListUnion.merge(
                List.of(TaskInfo.builder().taskId(" ").assignmentType("USER").build()),
                List.of(TaskInfo.builder().assignmentType("USER").build()));
        assertThat(merged).isEmpty();
    }
}
