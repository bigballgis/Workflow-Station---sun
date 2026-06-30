package com.workflow.util;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves scalar values from {@code __subTables__[bindingId]} row slices for email templates.
 */
public final class SubTableFieldResolver {

    private SubTableFieldResolver() {
    }

    public static String resolveBindingField(Map<String, Object> variables, String bindingId, String fieldName) {
        if (!StringUtils.hasText(bindingId) || !StringUtils.hasText(fieldName) || variables == null) {
            return "";
        }
        return joinValues(collectFieldValues(getRows(variables, bindingId.trim()), fieldName.trim()));
    }

    /**
     * Fallback when designers use {@code ${fieldName}} for a sub-table column: search all bindings.
     */
    public static String resolveFieldAcrossSubTables(Map<String, Object> variables, String fieldName) {
        if (!StringUtils.hasText(fieldName) || variables == null) {
            return "";
        }
        Object subTablesObj = variables.get("__subTables__");
        if (!(subTablesObj instanceof Map<?, ?> subTables)) {
            return "";
        }

        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (Object rowsObj : subTables.values()) {
            if (!(rowsObj instanceof List<?> rows)) {
                continue;
            }
            List<Map<String, Object>> rowMaps = toRowMaps(rows);
            values.addAll(collectFieldValues(rowMaps, fieldName.trim()));
        }
        return joinValues(values);
    }

    private static List<Map<String, Object>> getRows(Map<String, Object> variables, String bindingId) {
        Object subTablesObj = variables.get("__subTables__");
        if (!(subTablesObj instanceof Map<?, ?> subTables)) {
            return List.of();
        }
        Object rowsObj = subTables.get(bindingId);
        if (!(rowsObj instanceof List<?> rows)) {
            return List.of();
        }
        return toRowMaps(rows);
    }

    private static List<Map<String, Object>> toRowMaps(List<?> rows) {
        List<Map<String, Object>> rowMaps = new ArrayList<>();
        for (Object row : rows) {
            if (row instanceof Map<?, ?> rowMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) rowMap;
                rowMaps.add(typed);
            }
        }
        return rowMaps;
    }

    private static List<String> collectFieldValues(List<Map<String, Object>> rows, String fieldName) {
        List<String> values = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object value = row.get(fieldName);
            if (value != null && StringUtils.hasText(value.toString())) {
                values.add(value.toString().trim());
            }
        }
        return values;
    }

    private static String joinValues(Iterable<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(value);
        }
        return out.toString();
    }
}
