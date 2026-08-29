package com.portal.util;

import com.platform.common.list.ListColumnFilter;
import com.portal.dto.TaskInfo;
import com.portal.dto.TaskQueryRequest;

import java.util.List;
import java.util.stream.Collectors;

/**
 * In-memory Mine / To Do filters: legacy request fields plus shared-list column filters.
 */
public final class TaskInfoQueryFilters {

    private TaskInfoQueryFilters() {
    }

    public static boolean needsRequestIdEnrichment(TaskQueryRequest request) {
        var filters = TaskQueryColumnFilters.normalize(request.getFilters());
        return filters.stream().anyMatch(f -> "requestId".equals(f.field()))
                || (request.getKeyword() != null && !request.getKeyword().isBlank())
                || (request.getSortBy() != null && "requestId".equalsIgnoreCase(request.getSortBy().trim()));
    }

    public static List<TaskInfo> apply(List<TaskInfo> tasks, TaskQueryRequest request) {
        List<ListColumnFilter> columnFilters = TaskQueryColumnFilters.normalize(request.getFilters());
        return tasks.stream()
                .filter(t -> matchesLegacyAndChrome(t, request, columnFilters))
                .collect(Collectors.toList());
    }

    private static boolean matchesLegacyAndChrome(
            TaskInfo task, TaskQueryRequest request, List<ListColumnFilter> columnFilters) {
        if (request.getPriorities() != null && !request.getPriorities().isEmpty()
                && !request.getPriorities().contains(task.getPriority())) {
            return false;
        }
        if (request.getProcessTypes() != null && !request.getProcessTypes().isEmpty()
                && !request.getProcessTypes().contains(task.getProcessDefinitionKey())) {
            return false;
        }
        if (request.getStatuses() != null && !request.getStatuses().isEmpty()
                && !request.getStatuses().contains(task.getStatus())) {
            return false;
        }
        if (request.getStartTime() != null && task.getCreateTime() != null
                && task.getCreateTime().isBefore(request.getStartTime())) {
            return false;
        }
        if (request.getEndTime() != null && task.getCreateTime() != null
                && task.getCreateTime().isAfter(request.getEndTime())) {
            return false;
        }
        if (Boolean.TRUE.equals(request.getIncludeOverdue()) && !Boolean.TRUE.equals(task.getIsOverdue())) {
            return false;
        }
        if (!TaskQueryColumnFilters.toolbarKeywordMatches(task, request.getKeyword())) {
            return false;
        }
        if (!columnFilters.isEmpty() && !TaskQueryColumnFilters.matches(task, columnFilters)) {
            return false;
        }
        return TodoAssignmentTypes.matches(task, request.getAssignmentTypes());
    }
}
