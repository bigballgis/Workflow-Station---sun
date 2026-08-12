package com.portal.util;

import com.portal.dto.TaskQueryRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EngineTaskPushdown")
class EngineTaskPushdownTest {

    @Test
    void canFullyPush_defaultSortAndNoFilters() {
        assertThat(EngineTaskPushdown.canFullyPush(TaskQueryRequest.builder().build())).isTrue();
        assertThat(EngineTaskPushdown.canFullyPush(TaskQueryRequest.builder()
                .sortBy("dueDate")
                .sortDirection("asc")
                .build())).isTrue();
    }

    @Test
    void canFullyPush_singleTaskNameContains() {
        TaskQueryRequest req = TaskQueryRequest.builder()
                .filters(Map.of("taskName", Map.of("operator", "contains", "value", "审批")))
                .sortBy("createTime")
                .sortDirection("desc")
                .build();
        assertThat(EngineTaskPushdown.canFullyPush(req)).isTrue();
        EngineTaskPushdown.Criteria c = EngineTaskPushdown.from(req);
        assertThat(c.taskNameLike()).isEqualTo("%审批%");
        assertThat(c.taskNameExact()).isNull();
    }

    @Test
    void canFullyPush_falseForKeywordOrInitiator() {
        assertThat(EngineTaskPushdown.canFullyPush(TaskQueryRequest.builder()
                .keyword("x")
                .build())).isFalse();
        assertThat(EngineTaskPushdown.canFullyPush(TaskQueryRequest.builder()
                .filters(Map.of("initiatorName", Map.of("operator", "contains", "value", "a")))
                .build())).isFalse();
    }

    @Test
    void from_extractsNameEvenWhenOtherFiltersForceFullScan() {
        TaskQueryRequest req = TaskQueryRequest.builder()
                .filters(Map.of(
                        "taskName", Map.of("operator", "startsWith", "value", "Leave"),
                        "initiatorName", Map.of("operator", "contains", "value", "Ann")))
                .sortBy("priority")
                .sortDirection("asc")
                .build();
        assertThat(EngineTaskPushdown.canFullyPush(req)).isFalse();
        EngineTaskPushdown.Criteria c = EngineTaskPushdown.from(req);
        assertThat(c.taskNameLike()).isEqualTo("Leave%");
        assertThat(c.sortBy()).isEqualTo("priority");
        assertThat(c.sortDirection()).isEqualTo("asc");
    }

    @Test
    void from_eqUsesExact() {
        EngineTaskPushdown.Criteria c = EngineTaskPushdown.from(TaskQueryRequest.builder()
                .filters(Map.of("taskName", Map.of("operator", "eq", "value", "Review")))
                .build());
        assertThat(c.taskNameExact()).isEqualTo("Review");
        assertThat(c.taskNameLike()).isNull();
    }

    @Test
    void canFullyPush_falseForNonPushableSort() {
        assertThat(EngineTaskPushdown.canFullyPush(TaskQueryRequest.builder()
                .sortBy("processDefinitionName")
                .build())).isFalse();
    }

    @Test
    void canFullyPush_falseForPrioritiesList() {
        assertThat(EngineTaskPushdown.canFullyPush(TaskQueryRequest.builder()
                .priorities(List.of("HIGH"))
                .build())).isFalse();
    }
}
