package com.portal.component;

import com.platform.common.jdbc.SubTableRowIdentity;
import com.platform.common.jdbc.SubTableRowKeySupport;
import com.portal.dto.SubTableBindingScope;
import com.portal.exception.PortalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Binding-grained write checks for table-keyed {@code __subTables__}.
 *
 * <p>Empty/null scopes leave the V1 path unchanged. When scopes are present, a row that
 * changed must be claimed by a binding whose declared filter FK matches server-resolved
 * context — not a client-supplied parent value.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SubTableBindingScopeGuard {

    static final String ROW_VERSION_FIELD = "_wsRowVersion";

    private final JdbcTemplate jdbcTemplate;

    void assertAndApply(List<SubTableBindingScope> scopes, String functionUnitCode,
                        Map<String, Object> formData, Map<String, Object> submitted,
                        Map<String, Object> baseline) {
        if (scopes == null || scopes.isEmpty() || submitted == null) {
            return;
        }
        if (!StringUtils.hasText(functionUnitCode)) {
            throw new PortalException("403", "Binding-scoped submit requires a function unit on the process");
        }
        Map<String, Object> base = baseline != null ? baseline : Map.of();
        Map<String, List<String>> pkByStoreKey = new HashMap<>();
        for (SubTableBindingScope scope : scopes) {
            applyOneScope(scope, functionUnitCode.trim(), formData, submitted, base, pkByStoreKey);
        }
        rejectUnclaimedChanges(scopes, submitted, base, pkByStoreKey);
    }

    private void applyOneScope(SubTableBindingScope scope, String functionUnitCode,
                               Map<String, Object> formData, Map<String, Object> submitted,
                               Map<String, Object> baseline, Map<String, List<String>> pkByStoreKey) {
        BindingMeta meta = loadBinding(scope, functionUnitCode);
        pkByStoreKey.put(meta.storeKey(), meta.pkColumns());
        List<Map<String, Object>> claimed = scope.getRowKeys() != null ? scope.getRowKeys() : List.of();
        Object expected = expectedFilterValue(meta, formData, submitted, baseline);
        List<Object> submittedRows = rowsOf(submitted, meta.storeKey());
        List<Object> baselineRows = rowsOf(baseline, meta.storeKey());
        for (Map<String, Object> rowKey : claimed) {
            Map<String, Object> row = findRow(submittedRows, rowKey, meta.pkColumns());
            if (row == null) {
                row = findRow(baselineRows, rowKey, meta.pkColumns());
            }
            if (row == null) {
                throw new PortalException("403", "Binding scope claimed a row that is not in the table store");
            }
            assertRowMatchesFilter(meta, expected, row);
            assertRowVersion(row, findRow(baselineRows, rowKey, meta.pkColumns()));
        }
        if (Boolean.TRUE.equals(scope.getEmptied()) && meta.filterField() != null && expected != null) {
            submitted.put(meta.storeKey(), removeMatching(submittedRows, meta.filterField(), expected));
        }
        bumpVersions(submitted, meta.storeKey(), claimed, meta.pkColumns(), baselineRows);
    }

    private void rejectUnclaimedChanges(List<SubTableBindingScope> scopes,
                                        Map<String, Object> submitted, Map<String, Object> baseline,
                                        Map<String, List<String>> pkByStoreKey) {
        Map<String, List<Map<String, Object>>> claimedByKey = new HashMap<>();
        for (SubTableBindingScope scope : scopes) {
            if (scope.getStoreKey() == null || scope.getRowKeys() == null) {
                continue;
            }
            claimedByKey.computeIfAbsent(scope.getStoreKey(), k -> new ArrayList<>())
                    .addAll(scope.getRowKeys());
        }
        for (Map.Entry<String, List<Map<String, Object>>> e : claimedByKey.entrySet()) {
            rejectUnclaimedForKey(e.getKey(), e.getValue(), submitted, baseline,
                    pkByStoreKey.getOrDefault(e.getKey(), List.of()));
        }
    }

    private void rejectUnclaimedForKey(String storeKey, List<Map<String, Object>> claimed,
                                       Map<String, Object> submitted, Map<String, Object> baseline,
                                       List<String> pkColumns) {
        List<String> pkGuess = new ArrayList<>(pkColumns != null ? pkColumns : List.of());
        if (pkGuess.isEmpty() && !claimed.isEmpty() && claimed.get(0) != null) {
            pkGuess.addAll(claimed.get(0).keySet());
        }
        pkGuess.remove(ROW_VERSION_FIELD);
        pkGuess.remove(SubTableRowIdentity.CANONICAL_FIELD);
        List<Object> submittedRows = rowsOf(submitted, storeKey);
        List<Object> baselineRows = rowsOf(baseline, storeKey);
        for (Object raw : submittedRows) {
            if (!(raw instanceof Map<?, ?>)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) raw;
            Map<String, Object> key = rowKeyOf(row, pkGuess);
            Map<String, Object> before = findRow(baselineRows, key, pkGuess);
            if (before != null && sameBusinessPayload(before, row)) {
                continue;
            }
            if (!claimedContains(claimed, key, pkGuess)) {
                throw new PortalException("403",
                        "A sub-table row changed without a binding scope that claims it");
            }
        }
    }

    private BindingMeta loadBinding(SubTableBindingScope scope, String functionUnitCode) {
        if (scope == null || !StringUtils.hasText(scope.getBindingId())) {
            throw new PortalException("400", "Binding scope is missing bindingId");
        }
        long bindingId;
        try {
            bindingId = Long.parseLong(scope.getBindingId().trim());
        } catch (NumberFormatException e) {
            throw new PortalException("400", "Binding scope bindingId is not a number");
        }
        List<BindingMeta> rows = jdbcTemplate.query(
                """
                        SELECT b.id, lower(t.table_name) AS table_name, ffk.field_name AS filter_field,
                               ref.table_type AS filter_ref_type,
                               lower(ref.table_name) AS filter_ref_table_name,
                               ref.id AS filter_ref_table_id
                        FROM dw_form_table_bindings b
                        JOIN dw_form_definitions fd ON fd.id = b.form_id
                        JOIN dw_function_units fu ON fu.id = fd.function_unit_id
                        JOIN dw_table_definitions t ON t.id = b.table_id
                        LEFT JOIN dw_field_definitions ffk ON ffk.id = b.filter_fk_field_id
                        LEFT JOIN dw_table_definitions ref ON ref.id = ffk.ref_table_id
                        WHERE b.id = ? AND fu.code = ?
                        """,
                (rs, n) -> {
                    long refRaw = rs.getLong("filter_ref_table_id");
                    Long refTableId = rs.wasNull() ? null : refRaw;
                    return new BindingMeta(
                            rs.getLong("id"),
                            "dw:" + rs.getString("table_name"),
                            rs.getString("filter_field"),
                            rs.getString("filter_ref_type"),
                            rs.getString("filter_ref_table_name"),
                            refTableId,
                            pkColumns(bindingId));
                },
                bindingId, functionUnitCode);
        if (rows.isEmpty()) {
            throw new PortalException("403", "Binding is not part of this function unit");
        }
        BindingMeta meta = rows.get(0);
        if (StringUtils.hasText(scope.getStoreKey())
                && !meta.storeKey().equalsIgnoreCase(scope.getStoreKey().trim())) {
            throw new PortalException("403", "Binding scope storeKey does not match the binding's table");
        }
        return meta;
    }

    private List<String> pkColumns(long bindingId) {
        return jdbcTemplate.queryForList(
                """
                        SELECT f.field_name
                        FROM dw_field_definitions f
                        JOIN dw_form_table_bindings b ON b.table_id = f.table_id
                        WHERE b.id = ? AND COALESCE(f.is_primary_key, false) = true
                        ORDER BY f.sort_order NULLS LAST, f.id
                        """,
                String.class, bindingId);
    }

    private Object expectedFilterValue(BindingMeta meta, Map<String, Object> formData,
                                       Map<String, Object> submitted, Map<String, Object> baseline) {
        if (!StringUtils.hasText(meta.filterField())) {
            return null;
        }
        if (meta.filterRefType() != null && "MAIN".equalsIgnoreCase(meta.filterRefType())) {
            return mainRecordFilterKeys(meta, formData);
        }
        Object fromCurrent = singleCurrentItemValue(formData);
        if (fromCurrent != null) {
            return fromCurrent;
        }
        if (hasCurrentItemObject(formData)) {
            throw new PortalException("403",
                    "Cannot authorize a binding-scoped write without the current row context");
        }
        return siblingParentKeys(meta, submitted, baseline);
    }

    private Object mainRecordFilterKeys(BindingMeta meta, Map<String, Object> formData) {
        if (formData == null || meta.filterRefTableId() == null) {
            return null;
        }
        List<String> pk = pkColumnsOfTable(meta.filterRefTableId());
        if (pk.isEmpty()) {
            return null;
        }
        List<Object> keys = new ArrayList<>();
        for (String col : pk) {
            Object value = formData.get(col);
            if (value == null) {
                value = ignoreCase(formData, col);
            }
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                keys.add(value);
            }
        }
        return keys.isEmpty() ? null : keys;
    }

    private List<Object> siblingParentKeys(BindingMeta meta, Map<String, Object> submitted,
                                           Map<String, Object> baseline) {
        if (!StringUtils.hasText(meta.filterRefTableName()) || meta.filterRefTableId() == null) {
            return List.of();
        }
        String parentKey = "dw:" + meta.filterRefTableName();
        List<Object> parentRows = rowsOf(submitted, parentKey);
        if (parentRows.isEmpty()) {
            parentRows = rowsOf(baseline, parentKey);
        }
        List<String> pk = pkColumnsOfTable(meta.filterRefTableId());
        List<Object> keys = new ArrayList<>();
        for (Object raw : parentRows) {
            if (!(raw instanceof Map<?, ?>)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) raw;
            for (String col : pk) {
                Object value = row.get(col);
                if (value == null) {
                    value = ignoreCase(row, col);
                }
                if (value != null && StringUtils.hasText(String.valueOf(value))) {
                    keys.add(value);
                }
            }
        }
        return keys;
    }

    private List<String> pkColumnsOfTable(long tableId) {
        return jdbcTemplate.queryForList(
                """
                        SELECT f.field_name
                        FROM dw_field_definitions f
                        WHERE f.table_id = ? AND COALESCE(f.is_primary_key, false) = true
                        ORDER BY f.sort_order NULLS LAST, f.id
                        """,
                String.class, tableId);
    }

    private void assertRowMatchesFilter(BindingMeta meta, Object expected, Map<String, Object> row) {
        if (!StringUtils.hasText(meta.filterField()) || expected == null) {
            return;
        }
        Object actual = row.get(meta.filterField());
        if (actual == null) {
            actual = ignoreCase(row, meta.filterField());
        }
        if (!valueMatchesExpected(expected, actual)) {
            throw new PortalException("403", "Binding is not allowed to write this sub-table row");
        }
    }

    private void assertRowVersion(Map<String, Object> submittedRow, Map<String, Object> baselineRow) {
        if (baselineRow == null) {
            return;
        }
        Object expected = baselineRow.get(ROW_VERSION_FIELD);
        if (expected == null) {
            return;
        }
        Object provided = submittedRow.get(ROW_VERSION_FIELD);
        if (!sameValue(expected, provided)) {
            throw new PortalException("409", "Sub-table row was modified by another save; reload and retry");
        }
    }

    private void bumpVersions(Map<String, Object> submitted, String storeKey,
                              List<Map<String, Object>> claimed, List<String> pkColumns,
                              List<Object> baselineRows) {
        List<Object> rows = rowsOf(submitted, storeKey);
        for (int i = 0; i < rows.size(); i++) {
            Object raw = rows.get(i);
            if (!(raw instanceof Map<?, ?>)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) raw;
            Map<String, Object> key = rowKeyOf(row, pkColumns);
            if (!claimedContains(claimed, key, pkColumns)) {
                continue;
            }
            Map<String, Object> before = findRow(baselineRows, key, pkColumns);
            int next = 1;
            if (before != null && before.get(ROW_VERSION_FIELD) != null) {
                try {
                    next = Integer.parseInt(String.valueOf(before.get(ROW_VERSION_FIELD)).trim()) + 1;
                } catch (NumberFormatException ignored) {
                    next = 1;
                }
            }
            Map<String, Object> mutable = new LinkedHashMap<>(row);
            mutable.put(ROW_VERSION_FIELD, next);
            rows.set(i, mutable);
        }
        submitted.put(storeKey, rows);
    }

    private record BindingMeta(long id, String storeKey, String filterField, String filterRefType,
                               String filterRefTableName, Long filterRefTableId,
                               List<String> pkColumns) {}

    @SuppressWarnings("unchecked")
    private static List<Object> rowsOf(Map<String, Object> tables, String storeKey) {
        if (tables == null || storeKey == null) {
            return List.of();
        }
        Object raw = tables.get(storeKey);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return new ArrayList<>((List<Object>) list);
    }

    private static List<Object> removeMatching(List<Object> rows, String filterField, Object expected) {
        List<Object> kept = new ArrayList<>();
        for (Object raw : rows) {
            if (!(raw instanceof Map<?, ?> m)) {
                kept.add(raw);
                continue;
            }
            Object actual = m.get(filterField);
            if (actual == null) {
                actual = ignoreCase(m, filterField);
            }
            if (!valueMatchesExpected(expected, actual)) {
                kept.add(raw);
            }
        }
        return kept;
    }

    private static Map<String, Object> findRow(List<Object> rows, Map<String, Object> rowKey, List<String> pkColumns) {
        if (rowKey == null || rowKey.isEmpty()) {
            return null;
        }
        for (Object raw : rows) {
            if (!(raw instanceof Map<?, ?>)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) raw;
            Map<String, Object> candidate = rowKeyOf(row, pkColumns);
            if (keysMatch(rowKey, candidate)) {
                return row;
            }
        }
        return null;
    }

    private static Map<String, Object> rowKeyOf(Map<String, Object> row, List<String> pkColumns) {
        List<String> pk = pkColumns == null ? List.of() : pkColumns;
        if (!pk.isEmpty()) {
            Map<String, Object> fromPk = SubTableRowKeySupport.rowKeyFromVariableRow(row, pk);
            if (fromPk != null && !fromPk.isEmpty()) {
                return fromPk;
            }
        }
        Object uuid = row.get(SubTableRowIdentity.CANONICAL_FIELD);
        if (uuid != null && StringUtils.hasText(String.valueOf(uuid))) {
            Map<String, Object> key = new LinkedHashMap<>();
            key.put(SubTableRowIdentity.CANONICAL_FIELD, uuid);
            return key;
        }
        return Map.of();
    }

    private static boolean claimedContains(List<Map<String, Object>> claimed, Map<String, Object> key,
                                           List<String> pkColumns) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        for (Map<String, Object> c : claimed) {
            if (c == null || c.isEmpty()) {
                continue;
            }
            if (keysMatch(c, key) || keysMatch(key, c)) {
                return true;
            }
            Map<String, Object> claimedKey = rowKeyOf(c, pkColumns);
            if (keysMatch(claimedKey, key) || keysMatch(key, claimedKey)) {
                return true;
            }
        }
        return false;
    }

    private static boolean keysMatch(Map<String, Object> a, Map<String, Object> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, Object> e : a.entrySet()) {
            if (ROW_VERSION_FIELD.equals(e.getKey())) {
                continue;
            }
            if (!sameValue(e.getValue(), b.get(e.getKey()))
                    && !sameValue(e.getValue(), ignoreCase(b, e.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameBusinessPayload(Map<String, Object> a, Map<String, Object> b) {
        return Objects.equals(stripVersion(a), stripVersion(b));
    }

    private static Map<String, Object> stripVersion(Map<String, Object> row) {
        Map<String, Object> copy = new LinkedHashMap<>(row);
        copy.remove(ROW_VERSION_FIELD);
        return copy;
    }

    private static boolean valueMatchesExpected(Object expected, Object actual) {
        if (expected instanceof Collection<?> col) {
            for (Object one : col) {
                if (sameValue(one, actual)) {
                    return true;
                }
            }
            return false;
        }
        return sameValue(expected, actual);
    }

    private static boolean hasCurrentItemObject(Map<String, Object> formData) {
        if (formData == null) {
            return false;
        }
        Object raw = formData.get("_currentItem");
        if (!(raw instanceof Map)) {
            raw = formData.get("currentItem");
        }
        return raw instanceof Map;
    }

    @SuppressWarnings("unchecked")
    private static Object singleCurrentItemValue(Map<String, Object> formData) {
        if (formData == null) {
            return null;
        }
        Object raw = formData.get("_currentItem");
        if (!(raw instanceof Map)) {
            raw = formData.get("currentItem");
        }
        if (!(raw instanceof Map<?, ?> item)) {
            return null;
        }
        Object rowKey = item.get("rowKey");
        if (rowKey instanceof Map<?, ?> keys && keys.size() == 1) {
            return keys.values().iterator().next();
        }
        Object rowId = item.get("rowId");
        return rowId;
    }

    private static Object ignoreCase(Map<?, ?> row, String field) {
        if (row == null || field == null) {
            return null;
        }
        for (Map.Entry<?, ?> e : row.entrySet()) {
            if (e.getKey() != null && field.equalsIgnoreCase(String.valueOf(e.getKey()))) {
                return e.getValue();
            }
        }
        return null;
    }

    private static boolean sameValue(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        return String.valueOf(a).trim().equals(String.valueOf(b).trim());
    }
}
