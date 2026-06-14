package com.portal.util;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Filter, search, and sort helpers for portal Main Table view row projection.
 */
public final class PortalMainTableViewFilterUtils {

    private PortalMainTableViewFilterUtils() {}

    public static boolean matchesFilter(Map<String, Object> row, Map<String, Object> filterConfig) {
        if (filterConfig == null || filterConfig.isEmpty()) {
            return true;
        }
        return matchesFilterNode(row, filterConfig);
    }

    @SuppressWarnings("unchecked")
    public static boolean matchesFilterNode(Map<String, Object> row, Map<String, Object> node) {
        if (node == null || node.isEmpty()) {
            return true;
        }
        String logic = stringVal(node.get("logic"));
        boolean useOr = "or".equalsIgnoreCase(logic);

        Object conditionsObj = node.get("conditions");
        List<Map<String, Object>> conditions = new ArrayList<>();
        if (conditionsObj instanceof List<?> rawConditions) {
            for (Object condObj : rawConditions) {
                if (condObj instanceof Map<?, ?> cond) {
                    conditions.add((Map<String, Object>) cond);
                }
            }
        }

        Object groupsObj = node.get("groups");
        List<Map<String, Object>> groups = new ArrayList<>();
        if (groupsObj instanceof List<?> rawGroups) {
            for (Object groupObj : rawGroups) {
                if (groupObj instanceof Map<?, ?> group) {
                    groups.add((Map<String, Object>) group);
                }
            }
        }

        if (conditions.isEmpty() && groups.isEmpty()) {
            return true;
        }

        if (useOr) {
            for (Map<String, Object> cond : conditions) {
                if (evaluateFilterCondition(row, cond)) {
                    return true;
                }
            }
            for (Map<String, Object> group : groups) {
                if (matchesFilterNode(row, group)) {
                    return true;
                }
            }
            return false;
        }

        for (Map<String, Object> cond : conditions) {
            if (!evaluateFilterCondition(row, cond)) {
                return false;
            }
        }
        for (Map<String, Object> group : groups) {
            if (!matchesFilterNode(row, group)) {
                return false;
            }
        }
        return true;
    }

    public static boolean matchesSearch(Map<String, Object> row, String needle) {
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey().startsWith("_")) {
                continue;
            }
            if (e.getValue() != null && String.valueOf(e.getValue()).toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    public static void applyViewSort(List<Map<String, Object>> rows, List<Map<String, Object>> sortConfig) {
        if (sortConfig == null || sortConfig.isEmpty()) {
            return;
        }
        rows.sort((a, b) -> {
            for (Map<String, Object> spec : sortConfig) {
                String field = stringVal(spec.get("fieldName"));
                if (field == null) {
                    continue;
                }
                Object va = a.get(field);
                Object vb = b.get(field);
                int cmp = compareSortValues(va, vb);
                if (cmp != 0) {
                    String dir = stringVal(spec.get("direction"));
                    return "DESC".equalsIgnoreCase(dir) ? -cmp : cmp;
                }
            }
            return 0;
        });
    }

    private static boolean evaluateFilterCondition(Map<String, Object> row, Map<String, Object> cond) {
        String fieldName = stringVal(cond.get("fieldName"));
        String operator = stringVal(cond.get("operator"));
        Object expected = cond.get("value");
        Object actual = row.get(fieldName);
        return evaluateCondition(actual, operator, expected);
    }

    private static boolean evaluateCondition(Object actual, String operator, Object expected) {
        if (operator == null || operator.isBlank()) {
            return true;
        }
        String op = operator.trim();
        if ("isNull".equals(op)) {
            return actual == null || String.valueOf(actual).isBlank();
        }
        if ("isNotNull".equals(op)) {
            return actual != null && !String.valueOf(actual).isBlank();
        }
        if (actual == null) {
            return false;
        }
        String actualStr = String.valueOf(actual);
        String expectedStr = expected != null ? String.valueOf(expected) : "";
        return switch (op) {
            case "eq" -> actualStr.equalsIgnoreCase(expectedStr);
            case "ne" -> !actualStr.equalsIgnoreCase(expectedStr);
            case "contains" -> actualStr.toLowerCase(Locale.ROOT).contains(expectedStr.toLowerCase(Locale.ROOT));
            case "notContains" -> !actualStr.toLowerCase(Locale.ROOT).contains(expectedStr.toLowerCase(Locale.ROOT));
            case "startsWith" -> actualStr.toLowerCase(Locale.ROOT).startsWith(expectedStr.toLowerCase(Locale.ROOT));
            case "notStartsWith" -> !actualStr.toLowerCase(Locale.ROOT).startsWith(expectedStr.toLowerCase(Locale.ROOT));
            case "endsWith" -> actualStr.toLowerCase(Locale.ROOT).endsWith(expectedStr.toLowerCase(Locale.ROOT));
            case "notEndsWith" -> !actualStr.toLowerCase(Locale.ROOT).endsWith(expectedStr.toLowerCase(Locale.ROOT));
            case "gt" -> compareAsDouble(actualStr, expectedStr) > 0;
            case "lt" -> compareAsDouble(actualStr, expectedStr) < 0;
            case "in" -> {
                if (expected instanceof Collection<?> col) {
                    yield col.stream().anyMatch(v -> actualStr.equalsIgnoreCase(String.valueOf(v)));
                }
                yield Arrays.stream(expectedStr.split(","))
                        .map(String::trim)
                        .anyMatch(v -> actualStr.equalsIgnoreCase(v));
            }
            default -> true;
        };
    }

    private static int compareAsDouble(String a, String b) {
        try {
            return Double.compare(Double.parseDouble(a), Double.parseDouble(b));
        } catch (NumberFormatException e) {
            return a.compareToIgnoreCase(b);
        }
    }

    private static int compareSortValues(Object a, Object b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        if (a instanceof LocalDateTime ta && b instanceof LocalDateTime tb) {
            return ta.compareTo(tb);
        }
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue());
        }
        return String.valueOf(a).compareToIgnoreCase(String.valueOf(b));
    }

    private static String stringVal(Object o) {
        return o != null ? String.valueOf(o) : null;
    }
}
