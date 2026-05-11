package com.platform.common.jdbc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Helpers for multi-column (and single-column) physical sub-table primary keys in MI / sub-task flows.
 */
public final class SubTableRowKeySupport {

    private static final char UNIT_SEP = '\u001f';

    private SubTableRowKeySupport() {
    }

    /** Resolve {@code key} on row map with exact match, then case-insensitive key match (form vs PG lowercased column). */
    public static Object getRowValueIgnoreCase(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    public static Map<String, Object> normalizeStringKeyMap(Map<?, ?> raw) {
        if (raw == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }

    /**
     * Build {@code col1 = ? AND col2 = ?} for validated identifiers.
     */
    public static String buildPkWhereClause(List<String> pkCols) {
        return pkCols.stream().map(c -> c + " = ?").collect(Collectors.joining(" AND "));
    }

    public static Object[] orderedPkParams(List<String> pkCols, Map<String, Object> rowKey) {
        Object[] args = new Object[pkCols.size()];
        for (int i = 0; i < pkCols.size(); i++) {
            args[i] = rowKey.get(pkCols.get(i));
        }
        return args;
    }

    public static boolean isComplete(List<String> pkCols, Map<String, Object> rowKey) {
        if (rowKey == null || pkCols == null || pkCols.isEmpty()) {
            return false;
        }
        for (String c : pkCols) {
            if (!rowKey.containsKey(c) || rowKey.get(c) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Stable string for maps keyed by row identity (e.g. MI progress overlay in portal).
     */
    public static String canonicalRowKeyString(List<String> orderedPkCols, Map<String, Object> rowKey) {
        if (orderedPkCols == null || rowKey == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String col : orderedPkCols) {
            if (!sb.isEmpty()) {
                sb.append(UNIT_SEP);
            }
            sb.append(col).append('=');
            Object v = rowKey.get(col);
            sb.append(v == null ? "" : String.valueOf(v));
        }
        return sb.toString();
    }

    /**
     * From Flowable collection element / runtime {@code currentItem}.
     */
    public static Map<String, Object> rowKeyFromCurrentItem(Map<String, Object> currentItem, List<String> pkCols) {
        if (currentItem == null || pkCols == null || pkCols.isEmpty()) {
            return null;
        }
        Object rawRk = currentItem.get("rowKey");
        if (rawRk instanceof Map<?, ?> m) {
            Map<String, Object> normalized = normalizeStringKeyMap(m);
            Map<String, Object> out = new LinkedHashMap<>();
            for (String c : pkCols) {
                Object val = getRowValueIgnoreCase(normalized, c);
                if (val == null && pkCols.size() == 1 && "id".equalsIgnoreCase(c)) {
                    val = getRowValueIgnoreCase(normalized, "id_idw");
                }
                if (val == null) {
                    return null;
                }
                out.put(c, val);
            }
            return out;
        }
        if (pkCols.size() == 1) {
            String col = pkCols.get(0);
            Object v = getRowValueIgnoreCase(currentItem, "rowId");
            if (v == null) {
                v = getRowValueIgnoreCase(currentItem, col);
            }
            if (v == null && "id".equalsIgnoreCase(col)) {
                v = getRowValueIgnoreCase(currentItem, "id_idw");
            }
            if (v == null) {
                return null;
            }
            return new LinkedHashMap<>(Map.of(col, v));
        }
        return null;
    }

    /**
     * From {@code wf_extended_task_info.extended_properties} JSON (already parsed).
     */
    public static Map<String, Object> rowKeyFromExtendedProps(Map<String, Object> extProps, List<String> pkCols) {
        if (extProps == null || pkCols == null || pkCols.isEmpty()) {
            return null;
        }
        Object rawRk = extProps.get("subTableRowKey");
        if (rawRk instanceof Map<?, ?> m) {
            Map<String, Object> normalized = normalizeStringKeyMap(m);
            Map<String, Object> out = new LinkedHashMap<>();
            for (String bc : pkCols) {
                Object val = getRowValueIgnoreCase(normalized, bc);
                if (val == null) {
                    return null;
                }
                out.put(bc, val);
            }
            return out;
        }
        if (pkCols.size() == 1) {
            Object id = extProps.get("subTableRowId");
            if (id == null) {
                return null;
            }
            return new LinkedHashMap<>(Map.of(pkCols.get(0), id));
        }
        return null;
    }

    /**
     * Sub-table row from initiator variables {@code __subTables__} / merged JSON row.
     */
    public static Map<String, Object> rowKeyFromVariableRow(Map<String, Object> row, List<String> pkCols) {
        if (row == null || pkCols == null || pkCols.isEmpty()) {
            return null;
        }
        Object rawRk = row.get("rowKey");
        if (rawRk instanceof Map<?, ?> m) {
            Map<String, Object> normalized = normalizeStringKeyMap(m);
            Map<String, Object> out = new LinkedHashMap<>();
            for (String c : pkCols) {
                Object val = getRowValueIgnoreCase(normalized, c);
                if (val == null && pkCols.size() == 1 && "id".equalsIgnoreCase(c)) {
                    val = getRowValueIgnoreCase(normalized, "id_idw");
                }
                if (val == null) {
                    return null;
                }
                out.put(c, val);
            }
            return out;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (String col : pkCols) {
            Object v = getRowValueIgnoreCase(row, col);
            if (v == null && pkCols.size() == 1) {
                v = getRowValueIgnoreCase(row, "rowId");
            }
            // Physical PG may expose PK as "id" while designer/json rows use dw_* primary key "id_idw" (e.g. kk subtable).
            if (v == null && pkCols.size() == 1 && "id".equalsIgnoreCase(col)) {
                v = getRowValueIgnoreCase(row, "id_idw");
            }
            if (v == null) {
                return null;
            }
            out.put(col, v);
        }
        return out;
    }

    /**
     * Build row key for assign API: explicit body wins; else single-column from path {@code rowId}.
     */
    public static Map<String, Object> resolveRowKeyForAssign(
            Long pathRowId,
            Map<String, Object> requestRowKey,
            List<String> pkCols) {
        if (pkCols == null || pkCols.isEmpty()) {
            return null;
        }
        if (requestRowKey != null && !requestRowKey.isEmpty()) {
            Map<String, Object> normalized = normalizeStringKeyMap(requestRowKey);
            Map<String, Object> out = new LinkedHashMap<>();
            for (String c : pkCols) {
                Object val = getRowValueIgnoreCase(normalized, c);
                if (val == null) {
                    throw new IllegalArgumentException("rowKey must include all primary key columns: " + pkCols);
                }
                out.put(c, val);
            }
            return out;
        }
        if (pkCols.size() == 1) {
            if (pathRowId == null) {
                return null;
            }
            return new LinkedHashMap<>(Map.of(pkCols.get(0), pathRowId));
        }
        throw new IllegalArgumentException(
                "Composite primary key requires rowKey in request body (path rowId is not sufficient)");
    }

    /**
     * PostgreSQL {@code information_schema} stores unquoted identifiers lowercased.
     */
    public static String informationSchemaTableName(String physicalTableName) {
        return physicalTableName.toLowerCase(Locale.ROOT);
    }
}
