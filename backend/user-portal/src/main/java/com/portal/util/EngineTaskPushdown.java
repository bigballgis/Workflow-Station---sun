package com.portal.util;

import com.portal.dto.TaskQueryRequest;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decides when Portal todo-list chrome can be pushed into workflow-engine TaskQuery
 * (engine window page + exact engine total) instead of a portal fullScan.
 *
 * <p>Pushable today: single {@code taskName} text filter + sort on createTime/dueDate/priority/name.
 * Keyword / initiator / processName / requestId / assignmentType / label priority lists stay portal-side.
 */
public final class EngineTaskPushdown {

    private static final Set<String> PUSHABLE_SORTS = Set.of(
            "createtime", "create_time",
            "duedate", "due_date",
            "priority",
            "name", "taskname", "task_name");

    private static final Set<String> PUSHABLE_NAME_OPS = Set.of(
            "contains", "eq", "startswith", "endswith");

    private EngineTaskPushdown() {
    }

    /**
     * Criteria forwarded as query params on {@code GET /api/v1/tasks}.
     */
    public record Criteria(
            String taskNameLike,
            String taskNameExact,
            Integer priority,
            String sortBy,
            String sortDirection
    ) {
        public static Criteria empty() {
            return new Criteria(null, null, null, null, null);
        }

        public boolean hasAny() {
            return (taskNameLike != null && !taskNameLike.isBlank())
                    || (taskNameExact != null && !taskNameExact.isBlank())
                    || priority != null
                    || (sortBy != null && !sortBy.isBlank())
                    || (sortDirection != null && !sortDirection.isBlank());
        }
    }

    /**
     * True when the request can be satisfied by one engine page (no portal fullScan).
     */
    public static boolean canFullyPush(TaskQueryRequest request) {
        if (request == null) {
            return true;
        }
        if (hasMemoryOnlyListFilters(request)) {
            return false;
        }
        List<TaskQueryColumnFilters.ColumnFilter> filters =
                TaskQueryColumnFilters.parseFilters(request.getFilters());
        if (filters.isEmpty()) {
            return isPushableSort(request.getSortBy());
        }
        if (filters.size() != 1) {
            return false;
        }
        TaskQueryColumnFilters.ColumnFilter only = filters.get(0);
        if (!"taskName".equals(only.field())) {
            return false;
        }
        if (!PUSHABLE_NAME_OPS.contains(normalizeOp(only.operator()))) {
            return false;
        }
        return isPushableSort(request.getSortBy());
    }

    /**
     * Best-effort extract of pushable fragments (also used to shrink fullScan engine walks).
     */
    public static Criteria from(TaskQueryRequest request) {
        if (request == null) {
            return Criteria.empty();
        }
        String taskNameLike = null;
        String taskNameExact = null;
        List<TaskQueryColumnFilters.ColumnFilter> filters =
                TaskQueryColumnFilters.parseFilters(request.getFilters());
        for (TaskQueryColumnFilters.ColumnFilter f : filters) {
            if (!"taskName".equals(f.field())) {
                continue;
            }
            String op = normalizeOp(f.operator());
            String value = f.value() != null ? f.value().trim() : "";
            if (value.isEmpty()) {
                break;
            }
            switch (op) {
                case "eq" -> taskNameExact = value;
                case "startswith" -> taskNameLike = value + "%";
                case "endswith" -> taskNameLike = "%" + value;
                case "contains" -> taskNameLike = "%" + value + "%";
                default -> {
                    // not pushable as name constraint
                }
            }
            break; // Flowable accepts one name predicate
        }

        String sortBy = null;
        String sortDirection = null;
        if (request.getSortBy() != null && !request.getSortBy().isBlank()) {
            String raw = request.getSortBy().trim();
            if (isPushableSort(raw)) {
                sortBy = normalizeSortField(raw);
                sortDirection = request.getSortDirection() != null && !request.getSortDirection().isBlank()
                        ? request.getSortDirection().trim()
                        : "desc";
            }
        } else if (request.getSortDirection() != null
                && !request.getSortDirection().isBlank()
                && !"desc".equalsIgnoreCase(request.getSortDirection().trim())) {
            sortBy = "createTime";
            sortDirection = request.getSortDirection().trim();
        }

        return new Criteria(taskNameLike, taskNameExact, null, sortBy, sortDirection);
    }

    private static boolean hasMemoryOnlyListFilters(TaskQueryRequest request) {
        if (request.getPriorities() != null && !request.getPriorities().isEmpty()) {
            return true;
        }
        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            return true;
        }
        if (request.getProcessTypes() != null && !request.getProcessTypes().isEmpty()) {
            return true;
        }
        if (request.getStatuses() != null && !request.getStatuses().isEmpty()) {
            return true;
        }
        if (request.getStartTime() != null || request.getEndTime() != null) {
            return true;
        }
        return Boolean.TRUE.equals(request.getIncludeOverdue());
    }

    private static boolean isPushableSort(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return true;
        }
        return PUSHABLE_SORTS.contains(sortBy.trim().toLowerCase(Locale.ROOT));
    }

    private static String normalizeSortField(String sortBy) {
        String s = sortBy.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "due_date", "duedate" -> "dueDate";
            case "task_name", "taskname", "name" -> "name";
            case "priority" -> "priority";
            default -> "createTime";
        };
    }

    private static String normalizeOp(String operator) {
        return operator != null ? operator.trim().toLowerCase(Locale.ROOT) : "";
    }
}
