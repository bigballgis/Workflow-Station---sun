package com.portal.util;

import com.portal.dto.TaskInfo;
import com.portal.dto.TaskQueryRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared in-memory filter / sort / page helpers for Portal task lists (todo + completed).
 */
public final class TaskInfoListOps {

    private TaskInfoListOps() {
    }

    public static List<TaskInfo> applyColumnFilters(List<TaskInfo> tasks, Map<String, Map<String, Object>> filters) {
        List<TaskQueryColumnFilters.ColumnFilter> parsed = TaskQueryColumnFilters.parseFilters(filters);
        if (parsed.isEmpty()) {
            return tasks;
        }
        return tasks.stream()
                .filter(t -> TaskQueryColumnFilters.matches(t, parsed))
                .collect(Collectors.toList());
    }

    /**
     * Sort whitelist for completed + pending lists. Unknown fields fall back to {@code createTime}.
     */
    public static List<TaskInfo> applySorting(List<TaskInfo> tasks, TaskQueryRequest request) {
        String sortBy = request.getSortBy() != null ? request.getSortBy().trim() : "";
        if (sortBy.isEmpty()) {
            sortBy = "createTime";
        }
        boolean ascending = "asc".equalsIgnoreCase(request.getSortDirection());

        Comparator<TaskInfo> comparator = switch (sortBy) {
            case "priority" -> Comparator.comparing(TaskInfo::getPriority, Comparator.nullsLast(Comparator.naturalOrder()));
            case "dueDate" -> Comparator.comparing(TaskInfo::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()));
            case "taskName" -> Comparator.comparing(TaskInfo::getTaskName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "processDefinitionName" -> Comparator.comparing(
                    TaskInfo::getProcessDefinitionName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "completedTime", "endTime" -> Comparator.comparing(
                    TaskInfo::getCompletedTime, Comparator.nullsLast(Comparator.naturalOrder()));
            case "createTime" -> Comparator.comparing(
                    TaskInfo::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()));
            case "requestId" -> Comparator.comparing(
                    TaskInfo::getRequestId, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "initiatorName" -> Comparator.comparing(
                    TaskInfo::getInitiatorName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "assignmentType" -> Comparator.comparing(
                    TaskInfo::getAssignmentType, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "currentStepName", "currentNode" -> Comparator.comparing(
                    t -> t.getCurrentStepName() != null ? t.getCurrentStepName() : t.getTaskName(),
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "action" -> Comparator.comparing(
                    TaskInfo::getAction, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "durationInMillis" -> Comparator.comparing(
                    TaskInfo::getDurationInMillis, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(TaskInfo::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()));
        };

        if (!ascending) {
            comparator = comparator.reversed();
        }
        return tasks.stream().sorted(comparator).collect(Collectors.toList());
    }

    public static List<TaskInfo> pageOf(List<TaskInfo> tasks, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, size);
        int start = safePage * safeSize;
        if (start >= tasks.size()) {
            return List.of();
        }
        int end = Math.min(start + safeSize, tasks.size());
        return new ArrayList<>(tasks.subList(start, end));
    }

    public static boolean needsMemoryScanForCompleted(TaskQueryRequest request) {
        if (TaskQueryColumnFilters.hasFilters(request.getFilters())) {
            return true;
        }
        String sortBy = request.getSortBy();
        if (sortBy != null && !sortBy.isBlank()) {
            String s = sortBy.trim().toLowerCase(Locale.ROOT);
            if (!"completedtime".equals(s) && !"endtime".equals(s) && !"createtime".equals(s)) {
                return true;
            }
        }
        String sortDir = request.getSortDirection();
        return sortDir != null && !sortDir.isBlank() && "asc".equalsIgnoreCase(sortDir.trim());
    }
}
