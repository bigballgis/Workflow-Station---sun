package com.portal.util;

import com.portal.dto.TaskQueryRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
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

    /**
     * TaskQueryComponent used to ask a second, hand-maintained predicate whether the request
     * needed the full engine walk. Every input that predicate listed must already be
     * non-pushable here, otherwise routing them through this one decision changes behaviour.
     */
    @Test
    void canFullyPush_falseForEveryInputThatNeedsAllEnginePages() {
        assertThat(EngineTaskPushdown.canFullyPush(TaskQueryRequest.builder()
                .processTypes(List.of("leave"))
                .build())).isFalse();
        assertThat(EngineTaskPushdown.canFullyPush(TaskQueryRequest.builder()
                .statuses(List.of("RUNNING"))
                .build())).isFalse();
        assertThat(EngineTaskPushdown.canFullyPush(TaskQueryRequest.builder()
                .startTime(LocalDateTime.parse("2026-08-01T00:00:00"))
                .build())).isFalse();
        assertThat(EngineTaskPushdown.canFullyPush(TaskQueryRequest.builder()
                .endTime(LocalDateTime.parse("2026-08-31T23:59:59"))
                .build())).isFalse();
        assertThat(EngineTaskPushdown.canFullyPush(TaskQueryRequest.builder()
                .includeOverdue(true)
                .build())).isFalse();
        assertThat(EngineTaskPushdown.canFullyPush(TaskQueryRequest.builder()
                .filters(Map.of("requestId", Map.of("operator", "contains", "value", "REQ")))
                .build())).isFalse();
        // A name filter the engine cannot express (only contains/eq/startsWith/endsWith are pushable).
        assertThat(EngineTaskPushdown.canFullyPush(TaskQueryRequest.builder()
                .filters(Map.of("taskName", Map.of("operator", "notContains", "value", "審批")))
                .build())).isFalse();
    }

    /**
     * The inverse: a sort direction alone stays on the engine window path, and the direction is
     * carried over as an explicit createTime sort rather than silently dropped.
     */
    @Test
    void canFullyPush_ascendingDefaultSortStaysOnEngineWindow() {
        TaskQueryRequest req = TaskQueryRequest.builder().sortDirection("asc").build();
        assertThat(EngineTaskPushdown.canFullyPush(req)).isTrue();
        EngineTaskPushdown.Criteria c = EngineTaskPushdown.from(req);
        assertThat(c.sortBy()).isEqualTo("createTime");
        assertThat(c.sortDirection()).isEqualTo("asc");
    }
}
