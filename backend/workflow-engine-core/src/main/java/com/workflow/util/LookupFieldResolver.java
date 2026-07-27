package com.workflow.util;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Resolves scalar attributes from Lookup / Related embedded Relation Table row objects
 * stored under a form lookup field key in process variables.
 */
public final class LookupFieldResolver {

    private LookupFieldResolver() {
    }

    /**
     * @param lookupField form lookup widget field name (process variable key)
     * @param targetAttr  attribute on the embedded RT row (or rows when multi-select)
     */
    public static String resolve(Map<String, Object> variables, String lookupField, String targetAttr) {
        if (!StringUtils.hasText(lookupField) || !StringUtils.hasText(targetAttr) || variables == null) {
            return "";
        }
        Object raw = variables.get(lookupField.trim());
        if (raw == null) {
            return "";
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (Map<String, Object> row : asRowList(raw)) {
            Object value = row.get(targetAttr.trim());
            String text = stringifyScalar(value);
            if (StringUtils.hasText(text)) {
                values.add(text);
            }
        }
        return joinValues(values);
    }

    private static List<Map<String, Object>> asRowList(Object lookupValue) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (lookupValue instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            rows.add(typed);
            return rows;
        }
        if (lookupValue instanceof Collection<?> col) {
            for (Object item : col) {
                if (item instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) map;
                    rows.add(typed);
                }
            }
        }
        return rows;
    }

    /** Prefer plain scalars; nested maps are skipped (not meaningful in email text). */
    private static String stringifyScalar(Object value) {
        if (value == null || value instanceof Map<?, ?> || value instanceof Collection<?>) {
            return "";
        }
        String text = value.toString().trim();
        return text;
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
