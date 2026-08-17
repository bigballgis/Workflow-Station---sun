package com.portal.util;

import com.portal.dto.TaskInfo;
import com.portal.dto.TaskQueryRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TaskInfoListOps")
class TaskInfoListOpsTest {

    @Test
    void needsMemoryScanWhenColumnFiltersPresent() {
        TaskQueryRequest req = new TaskQueryRequest();
        Map<String, Map<String, Object>> filters = new LinkedHashMap<>();
        filters.put("taskName", Map.of("operator", "contains", "value", "x"));
        req.setFilters(filters);
        assertThat(TaskInfoListOps.needsMemoryScanForCompleted(req)).isTrue();
    }

    @Test
    void noMemoryScanForDefaultCompletedSort() {
        TaskQueryRequest req = new TaskQueryRequest();
        req.setSortBy("completedTime");
        req.setSortDirection("desc");
        assertThat(TaskInfoListOps.needsMemoryScanForCompleted(req)).isFalse();
    }

    @Test
    void applySortingAndPage() {
        TaskInfo a = TaskInfo.builder().taskId("1").taskName("B").createTime(LocalDateTime.now()).build();
        TaskInfo b = TaskInfo.builder().taskId("2").taskName("A").createTime(LocalDateTime.now()).build();
        TaskQueryRequest req = new TaskQueryRequest();
        req.setSortBy("taskName");
        req.setSortDirection("asc");

        List<TaskInfo> sorted = TaskInfoListOps.applySorting(List.of(a, b), req);
        assertThat(sorted).extracting(TaskInfo::getTaskName).containsExactly("A", "B");
        assertThat(TaskInfoListOps.pageOf(sorted, 0, 1)).hasSize(1);
        assertThat(TaskInfoListOps.pageOf(sorted, 0, 1).get(0).getTaskName()).isEqualTo("A");
    }

    @Test
    void applySorting_processDefinitionName() {
        TaskInfo a = TaskInfo.builder().taskId("1").processDefinitionName("Beta").createTime(LocalDateTime.now()).build();
        TaskInfo b = TaskInfo.builder().taskId("2").processDefinitionName("Alpha").createTime(LocalDateTime.now()).build();
        TaskQueryRequest req = new TaskQueryRequest();
        req.setSortBy("processDefinitionName");
        req.setSortDirection("asc");

        List<TaskInfo> sorted = TaskInfoListOps.applySorting(List.of(a, b), req);
        assertThat(sorted).extracting(TaskInfo::getProcessDefinitionName).containsExactly("Alpha", "Beta");
    }
}
