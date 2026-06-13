package com.portal.component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stateless helpers for {@code __subTables__} variable payloads used when building multi-instance (MI)
 * collections: alias-key canonicalization, slice-key ordering, row merge, and assignee text extraction.
 * Extracted from {@link TaskProcessComponent}; pure functions only (no Spring dependencies).
 */
final class MiSubTableVariableSupport {

    private MiSubTableVariableSupport() {
    }

    /**
     * Stops alias-slice key monotonic growth (bindingId / tableName / normalizedName) for JSONB {@code __subTables__}.
     * <p>
     * When numeric keys exist at a given {@code __subTables__} level, keep only those numeric keys and recursively
     * canonicalize nested {@code __subTables__} stored under each row.
     */
    static Map<String, Object> canonicalizeSubTablesAliasKeys(Map<String, Object> subTables) {
        if (subTables == null || subTables.isEmpty()) {
            return subTables;
        }
        boolean hasNumeric = false;
        for (String k : subTables.keySet()) {
            if (isDigitsKey(k)) {
                hasNumeric = true;
                break;
            }
        }
        Map<String, Object> out = hasNumeric ? new LinkedHashMap<>() : subTables;
        for (Map.Entry<String, Object> e : subTables.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (hasNumeric && !isDigitsKey(e.getKey())) {
                continue;
            }
            Object v = e.getValue();
            canonicalizeNestedSubTablesInValue(v);
            if (hasNumeric) {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }

    private static void canonicalizeNestedSubTablesInValue(Object value) {
        if (!(value instanceof List<?> rows)) {
            return;
        }
        for (Object rowObj : rows) {
            if (!(rowObj instanceof Map<?, ?> rowMap)) {
                continue;
            }
            Object nestedObj = rowMap.get("__subTables__");
            if (!(nestedObj instanceof Map<?, ?> nestedMap) || nestedMap.isEmpty()) {
                continue;
            }
            // Rebuild with String keys to avoid ClassCastException during canonicalization.
            Map<String, Object> nestedStringKeyMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> ne : nestedMap.entrySet()) {
                if (ne.getKey() == null || ne.getValue() == null) {
                    continue;
                }
                nestedStringKeyMap.put(String.valueOf(ne.getKey()), ne.getValue());
            }
            Map<String, Object> nestedCanonical = canonicalizeSubTablesAliasKeys(nestedStringKeyMap);
            @SuppressWarnings("unchecked")
            Map<String, Object> rowMapString = (Map<String, Object>) rowMap;
            rowMapString.put("__subTables__", nestedCanonical);
        }
    }

    private static boolean isDigitsKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            if (!Character.isDigit(key.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Numeric binding ids sort ascending so higher ids (runtime canvas binding) overwrite stale sibling slices on merge. */
    static int parseNumericSubTableSliceKey(String sliceKey) {
        if (sliceKey != null && sliceKey.matches("\\d+")) {
            try {
                return Integer.parseInt(sliceKey);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return Integer.MAX_VALUE;
    }

    static Map<String, Object> mergeMiCollectionRowPreferIncoming(Map<String, Object> existing, Map<String, Object> incoming) {
        Map<String, Object> out = new LinkedHashMap<>(existing);
        for (Map.Entry<String, Object> e : incoming.entrySet()) {
            if (e.getValue() != null) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    /** Extract Flowable user id from assignee cell (plain id or user snapshot map). */
    static String normalizeMiAssigneeText(Object assigneeValue) {
        if (assigneeValue == null) {
            return "";
        }
        if (assigneeValue instanceof Map<?, ?> map) {
            for (String key : new String[]{"id", "userId", "user_id", "value"}) {
                Object v = map.get(key);
                if (v != null && !String.valueOf(v).trim().isEmpty()) {
                    return String.valueOf(v).trim();
                }
            }
            return "";
        }
        return String.valueOf(assigneeValue).trim();
    }
}
