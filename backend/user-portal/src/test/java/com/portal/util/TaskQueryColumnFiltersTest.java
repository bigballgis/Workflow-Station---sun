package com.portal.util;

import com.portal.dto.TaskInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TaskQueryColumnFilters")
class TaskQueryColumnFiltersTest {

    @Test
    void parseFilters_whitelistAndAliases() {
        Map<String, Map<String, Object>> raw = new LinkedHashMap<>();
        raw.put("currentStepName", Map.of("operator", "contains", "value", "Approve"));
        raw.put("taskName", Map.of("operator", "eq", "value", "Review"));
        raw.put("unknown", Map.of("operator", "eq", "value", "x"));
        raw.put("priority", Map.of("operator", "eq", "value", ""));

        List<TaskQueryColumnFilters.ColumnFilter> filters = TaskQueryColumnFilters.parseFilters(raw);

        assertThat(filters).extracting(TaskQueryColumnFilters.ColumnFilter::field)
                .containsExactly("currentNode", "taskName");
    }

    @Test
    void matches_appliesOperators() {
        TaskInfo task = TaskInfo.builder()
                .taskName("Leave Approval")
                .processDefinitionName("HR Leave")
                .initiatorName("Alice")
                .priority("HIGH")
                .assignmentType("USER")
                .currentStepName("Approve")
                .requestId("HR-001")
                .build();

        assertThat(TaskQueryColumnFilters.matches(task, List.of(
                new TaskQueryColumnFilters.ColumnFilter("taskName", "contains", "leave")))).isTrue();
        assertThat(TaskQueryColumnFilters.matches(task, List.of(
                new TaskQueryColumnFilters.ColumnFilter("priority", "eq", "LOW")))).isFalse();
        assertThat(TaskQueryColumnFilters.matches(task, List.of(
                new TaskQueryColumnFilters.ColumnFilter("currentNode", "startsWith", "App")))).isTrue();
        assertThat(TaskQueryColumnFilters.matches(task, List.of(
                new TaskQueryColumnFilters.ColumnFilter("requestId", "eq", "HR-001")))).isTrue();
    }

    @Test
    void textMatches_nullAndBlank() {
        assertThat(TaskQueryColumnFilters.textMatches(null, "isNull", "")).isTrue();
        assertThat(TaskQueryColumnFilters.textMatches("x", "isNotNull", "")).isTrue();
        assertThat(TaskQueryColumnFilters.textMatches("abc", "endsWith", "bc")).isTrue();
        assertThat(TaskQueryColumnFilters.textMatches("abc", "notContains", "z")).isTrue();
    }

    @Test
    void hasFilters_falseWhenEmptyOrUnknown() {
        assertThat(TaskQueryColumnFilters.hasFilters(null)).isFalse();
        assertThat(TaskQueryColumnFilters.hasFilters(Map.of())).isFalse();
        assertThat(TaskQueryColumnFilters.hasFilters(Map.of(
                "hack", Map.of("operator", "eq", "value", "1")))).isFalse();
        assertThat(TaskQueryColumnFilters.hasFilters(Map.of(
                "taskName", Map.of("operator", "contains", "value", "a")))).isTrue();
    }
}
