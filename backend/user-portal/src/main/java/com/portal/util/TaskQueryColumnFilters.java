package com.portal.util;

import com.portal.dto.TaskInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * In-memory column filters for Portal task lists (MTV-shaped {@code {field:{operator,value}}}).
 *
 * <p>Whitelist fields: taskName, requestId, processDefinitionName, initiatorName, priority,
 * assignmentType, currentNode (alias of currentStepName).
 */
public final class TaskQueryColumnFilters {

    public static final Set<String> FILTER_FIELDS = Set.of(
            "taskName",
            "requestId",
            "processDefinitionName",
            "initiatorName",
            "priority",
            "assignmentType",
            "currentNode",
            "currentStepName");

    private TaskQueryColumnFilters() {
    }

    public record ColumnFilter(String field, String operator, String value) {
    }

    public static boolean hasFilters(Map<String, Map<String, Object>> raw) {
        return !parseFilters(raw).isEmpty();
    }

    public static List<ColumnFilter> parseFilters(Map<String, Map<String, Object>> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<ColumnFilter> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : raw.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String field = normalizeField(e.getKey().trim());
            if (field == null) {
                continue;
            }
            Map<String, Object> body = e.getValue();
            Object opObj = body.get("operator");
            String operator = opObj != null ? String.valueOf(opObj).trim() : "";
            if (operator.isEmpty()) {
                continue;
            }
            Object valObj = body.get("value");
            String value = valObj != null ? String.valueOf(valObj) : "";
            if (!"isNull".equals(operator) && !"isNotNull".equals(operator) && value.isBlank()) {
                continue;
            }
            out.add(new ColumnFilter(field, operator, value));
        }
        return out;
    }

/**
 * Coerce loosely typed body map into {@code Map<field, Map<operator|value>>}.
 */
public static Map<String, Map<String, Object>> coerceFilterMap(Map<String, ?> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map.Entry<String, ?> e : raw.entrySet()) {
            if (e.getKey() == null || !(e.getValue() instanceof Map<?, ?> body)) {
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> be : body.entrySet()) {
                if (be.getKey() != null) {
                    copy.put(String.valueOf(be.getKey()), be.getValue());
                }
            }
            out.put(e.getKey(), copy);
        }
        return out;
    }

    public static boolean matches(TaskInfo task, List<ColumnFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (ColumnFilter filter : filters) {
            if (!matchesOne(task, filter)) {
                return false;
            }
        }
        return true;
    }

    static String normalizeField(String feField) {
        if (feField == null || feField.isBlank()) {
            return null;
        }
        if ("currentStepName".equals(feField) || "currentNode".equals(feField)) {
            return "currentNode";
        }
        if (!FILTER_FIELDS.contains(feField)) {
            return null;
        }
        return feField;
    }

    private static boolean matchesOne(TaskInfo task, ColumnFilter filter) {
        if (filter == null || filter.field() == null || filter.operator() == null) {
            return true;
        }
        String actual = resolveFieldValue(task, filter.field());
        return textMatches(actual, filter.operator(), filter.value() != null ? filter.value() : "");
    }

    private static String resolveFieldValue(TaskInfo task, String field) {
        Function<TaskInfo, String> getter = switch (field) {
            case "taskName" -> TaskInfo::getTaskName;
            case "requestId" -> TaskInfo::getRequestId;
            case "processDefinitionName" -> TaskInfo::getProcessDefinitionName;
            case "initiatorName" -> TaskInfo::getInitiatorName;
            case "priority" -> TaskInfo::getPriority;
            case "assignmentType" -> TaskInfo::getAssignmentType;
            case "currentNode" -> t -> t.getCurrentStepName() != null ? t.getCurrentStepName() : t.getTaskName();
            default -> t -> null;
        };
        return getter.apply(task);
    }

    static boolean textMatches(String actual, String operator, String expected) {
        String op = operator != null ? operator.trim() : "";
        String value = expected != null ? expected : "";
        String left = actual != null ? actual : "";
        return switch (op) {
            case "isNull" -> left.isBlank();
            case "isNotNull" -> !left.isBlank();
            case "eq" -> left.equalsIgnoreCase(value);
            case "ne" -> !left.equalsIgnoreCase(value);
            case "contains" -> left.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT));
            case "notContains" -> !left.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT));
            case "startsWith" -> left.toLowerCase(Locale.ROOT).startsWith(value.toLowerCase(Locale.ROOT));
            case "endsWith" -> left.toLowerCase(Locale.ROOT).endsWith(value.toLowerCase(Locale.ROOT));
            default -> false; // unknown operator: do not match
        };
    }
}
