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
        assertAndApply(scopes, functionUnitCode, formData, submitted, baseline, null);
    }

    void assertAndApply(List<SubTableBindingScope> scopes, String functionUnitCode,
            Map<String, Object> formData, Map<String, Object> submitted, Map<String, Object> baseline,
            Map<String, SubTableWriteDesign.Binding> frozen) {
        if (scopes == null || scopes.isEmpty() || submitted == null) {
            return;
        }
        if (!StringUtils.hasText(functionUnitCode)) {
            throw new PortalException("403", "Binding-scoped submit requires a function unit on the process");
        }
        Map<String, Object> base = baseline != null ? baseline : Map.of();
        Map<String, List<String>> pkByStoreKey = new HashMap<>();
        for (SubTableBindingScope scope : scopes) {
            applyOneScope(scope, functionUnitCode.trim(), formData, submitted, base, pkByStoreKey, frozen);
        }
        rejectUnclaimedChanges(scopes, submitted, base, pkByStoreKey);
        // All bindings validate the original client version before any row is mutated.
        // Intersections are one business row, so increment each table's claimed rows once.
        for (Map.Entry<String, List<String>> entry : pkByStoreKey.entrySet()) {
            List<Map<String, Object>> claims = new ArrayList<>();
            for (SubTableBindingScope scope : scopes) {
                if (entry.getKey().equals(scope.getStoreKey()) && scope.getRowKeys() != null) {
                    claims.addAll(scope.getRowKeys());
                }
            }
            bumpVersions(submitted, entry.getKey(), claims, entry.getValue(), rowsOf(base, entry.getKey()));
        }
    }

    private void applyOneScope(SubTableBindingScope scope, String functionUnitCode,
                               Map<String, Object> formData, Map<String, Object> submitted,
                               Map<String, Object> baseline, Map<String, List<String>> pkByStoreKey,
                               Map<String, SubTableWriteDesign.Binding> frozen) {
        BindingMeta meta = loadBinding(scope, functionUnitCode, frozen);
        pkByStoreKey.put(meta.storeKey(), meta.pkColumns());
        List<Map<String, Object>> claimed = scope.getRowKeys() != null ? scope.getRowKeys() : List.of();
        Object expected = expectedFilterValue(meta, formData, submitted, baseline);
        List<Object> submittedRows = rowsOf(submitted, meta.storeKey());
        List<Object> baselineRows = rowsOf(baseline, meta.storeKey());
        for (Map<String, Object> rowKey : claimed) {
            Map<String, Object> row = findRow(submittedRows, rowKey, meta.pkColumns());
            Map<String, Object> baselineRow = findRow(baselineRows, rowKey, meta.pkColumns());
            if (row == null) {
                row = baselineRow;
            }
            if (row == null) {
                throw new PortalException("403", "Binding scope claimed a row that is not in the table store");
            }
            assertRowMatchesFilter(meta, expected, row);
            assertNewRowOwnership(meta, row, baselineRow);
            assertRowVersion(row, baselineRow);
        }
        // Empty is a UI hint, never authority to delete rows the client did not read.
        List<Map<String, Object>> deletions = scope.getDeletedRows() == null ? List.of() : scope.getDeletedRows();
        for (Map<String, Object> deletion : deletions) {
            Map<String, Object> before = findRow(baselineRows, deletion, meta.pkColumns());
            if (before == null) {
                throw new PortalException("409", "Sub-table deletion no longer matches a saved row");
            }
            assertDeletionStillOwned(meta, expected, before, formData, submitted, baseline);
            Object version = before.getOrDefault(ROW_VERSION_FIELD, 0);
            if (!sameValue(version, deletion.get(ROW_VERSION_FIELD))) {
                throw new PortalException("409", "Sub-table row was modified by another save; reload and retry");
            }
            submittedRows.removeIf(raw -> raw instanceof Map<?, ?> row
                    && keysMatch(rowKeyOf((Map<String, Object>) row, meta.pkColumns()),
                            rowKeyOf(deletion, meta.pkColumns())));
        }
        submitted.put(meta.storeKey(), submittedRows);
    }

    private void rejectUnclaimedChanges(List<SubTableBindingScope> scopes,
                                        Map<String, Object> submitted, Map<String, Object> baseline,
                                        Map<String, List<String>> pkByStoreKey) {
        Map<String, List<Map<String, Object>>> claimedByKey = new HashMap<>();
        for (String storeKey : pkByStoreKey.keySet()) {
            claimedByKey.put(storeKey, new ArrayList<>());
        }
        for (SubTableBindingScope scope : scopes) {
            if (scope == null || scope.getStoreKey() == null || scope.getRowKeys() == null) {
                continue;
            }
            claimedByKey.computeIfAbsent(scope.getStoreKey(), k -> new ArrayList<>())
                    .addAll(scope.getRowKeys());
        }
        for (Map.Entry<String, List<Map<String, Object>>> e : claimedByKey.entrySet()) {
            rejectUnclaimedForKey(e.getKey(), e.getValue(), submitted, baseline,
                    pkByStoreKey.getOrDefault(e.getKey(), List.of()));
            List<String> pk = pkByStoreKey.getOrDefault(e.getKey(), List.of());
            List<Map<String, Object>> deleted = scopes.stream()
                    .filter(s -> e.getKey().equals(s.getStoreKey()) && s.getDeletedRows() != null)
                    .flatMap(s -> s.getDeletedRows().stream()).toList();
            for (Object raw : rowsOf(baseline, e.getKey())) {
                if (!(raw instanceof Map<?, ?> row)) continue;
                Map<String, Object> key = rowKeyOf((Map<String, Object>) row, pk);
                if (findRow(rowsOf(submitted, e.getKey()), key, pk) == null
                        && !claimedContains(deleted, key, pk)) {
                    throw new PortalException("403", "Sub-table deletion requires an explicit versioned deletion claim");
                }
            }
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

    private BindingMeta loadBinding(SubTableBindingScope scope, String functionUnitCode,
                                    Map<String, SubTableWriteDesign.Binding> frozen) {
        if (scope == null || !StringUtils.hasText(scope.getBindingId())) {
            throw new PortalException("400", "Binding scope is missing bindingId");
        }
        long bindingId;
        try {
            bindingId = Long.parseLong(scope.getBindingId().trim());
        } catch (NumberFormatException e) {
            throw new PortalException("400", "Binding scope bindingId is not a number");
        }
        String requestedBindingId = scope.getBindingId().trim();
        if (frozen != null) {
            SubTableWriteDesign.Binding b = frozen.get(requestedBindingId);
            if (b == null || !b.storeKey().equals(scope.getStoreKey())) {
                throw new PortalException("403", "Binding is not part of the pinned form");
            }
            return new BindingMeta(bindingId, b.storeKey(), b.filterField(), b.refType(),
                    b.refTableName(), null, b.pk(), b.refPk(), b.foreignKeyFields(),
                    b.explicitFillFields());
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
                            List.of(), null, List.of(), List.of());
                },
                bindingId, functionUnitCode);
        if (rows.isEmpty()) {
            throw new PortalException("403", "Binding is not part of this function unit");
        }
        BindingMeta raw = rows.get(0);
        BindingMeta meta = new BindingMeta(raw.id(), raw.storeKey(), raw.filterField(),
                raw.filterRefType(), raw.filterRefTableName(), raw.filterRefTableId(),
                pkColumns(bindingId), null, foreignKeyColumns(bindingId),
                explicitFillColumns(bindingId));
        if (!StringUtils.hasText(scope.getStoreKey())
                || !meta.storeKey().equalsIgnoreCase(scope.getStoreKey().trim())) {
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

    private List<String> foreignKeyColumns(long bindingId) {
        return jdbcTemplate.queryForList(
                """
                        SELECT f.field_name
                        FROM dw_field_definitions f
                        JOIN dw_form_table_bindings b ON b.table_id = f.table_id
                        WHERE b.id = ? AND COALESCE(f.is_foreign_key, false) = true
                        ORDER BY f.sort_order NULLS LAST, f.id
                        """,
                String.class, bindingId);
    }

    private List<String> explicitFillColumns(long bindingId) {
        return jdbcTemplate.queryForList(
                """
                        SELECT DISTINCT COALESCE(NULLIF(BTRIM(source.value ->> 'fieldName'), ''), f.field_name)
                        FROM dw_form_table_bindings b
                        CROSS JOIN LATERAL jsonb_array_elements(
                            COALESCE(b.fk_fill_sources, '[]'::jsonb)
                        ) AS source(value)
                        LEFT JOIN dw_field_definitions f ON f.id = CASE
                            WHEN source.value ->> 'fieldId' ~ '^[0-9]+$'
                            THEN (source.value ->> 'fieldId')::bigint
                            ELSE NULL
                        END
                        WHERE b.id = ?
                          AND COALESCE(NULLIF(BTRIM(source.value ->> 'fieldName'), ''), f.field_name) IS NOT NULL
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
        if (formData == null || (meta.filterRefTableId() == null && meta.refPkColumns() == null)) {
            return null;
        }
        List<String> pk = meta.refPkColumns() != null ? meta.refPkColumns() : pkColumnsOfTable(meta.filterRefTableId());
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
        List<Object> current = parentKeys(meta, submitted);
        if (!current.isEmpty()) {
            return current;
        }
        return parentKeys(meta, baseline);
    }

    private List<Object> parentKeys(BindingMeta meta, Map<String, Object> tables) {
        if (!StringUtils.hasText(meta.filterRefTableName())
                || (meta.filterRefTableId() == null && meta.refPkColumns() == null)) {
            return List.of();
        }
        String parentKey = "dw:" + meta.filterRefTableName();
        List<Object> parentRows = rowsOf(tables, parentKey);
        List<String> pk = meta.refPkColumns() != null ? meta.refPkColumns() : pkColumnsOfTable(meta.filterRefTableId());
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

    /**
     * A child row may be deleted together with its parent. Surviving sibling parents are the
     * write allow-list, so the removed parent's key is absent from {@code expected}. The deletion
     * is still owned when that key was on the baseline parent row and is gone from the submitted one.
     */
    private void assertDeletionStillOwned(BindingMeta meta, Object expected, Map<String, Object> before,
                                          Map<String, Object> formData, Map<String, Object> submitted,
                                          Map<String, Object> baseline) {
        if (!StringUtils.hasText(meta.filterField()) || expected == null) {
            return;
        }
        Object actual = before.get(meta.filterField());
        if (actual == null) {
            actual = ignoreCase(before, meta.filterField());
        }
        if (valueMatchesExpected(expected, actual)) {
            return;
        }
        if (!hasCurrentItemObject(formData)
                && meta.filterRefType() != null
                && "SUB".equalsIgnoreCase(meta.filterRefType())
                && valueMatchesExpected(parentKeys(meta, baseline), actual)
                && !valueMatchesExpected(parentKeys(meta, submitted), actual)) {
            return;
        }
        throw new PortalException("403", "Binding is not allowed to write this sub-table row");
    }

    private void assertNewRowOwnership(BindingMeta meta, Map<String, Object> row,
                                       Map<String, Object> baselineRow) {
        if (baselineRow != null || !StringUtils.hasText(meta.filterField())) {
            return;
        }
        List<String> allowed = new ArrayList<>(meta.explicitFillFields());
        allowed.add(meta.filterField());
        for (String foreignKey : meta.foreignKeyFields()) {
            if (containsIgnoreCase(allowed, foreignKey)) {
                continue;
            }
            Object value = row.get(foreignKey);
            if (value == null) {
                value = ignoreCase(row, foreignKey);
            }
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                throw new PortalException("403",
                        "New sub-table row contains a foreign key not owned by this binding");
            }
        }
    }

    private static boolean containsIgnoreCase(List<String> values, String target) {
        return values != null && target != null && values.stream()
                .anyMatch(value -> value != null && value.equalsIgnoreCase(target));
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
            copyVersionOntoNestedCopies(submitted, storeKey, pkColumns, key, next);
        }
        submitted.put(storeKey, rows);
    }

    /**
     * A parent row keeps a second copy of these children under {@code __subTables__}.
     * Bumping only the flat row leaves that copy on the previous number, and the next
     * save then submits the stale one.
     */
    @SuppressWarnings("unchecked")
    private static void copyVersionOntoNestedCopies(Map<String, Object> submitted, String storeKey,
                                                     List<String> pkColumns, Map<String, Object> key, int next) {
        for (Object table : submitted.values()) {
            if (!(table instanceof List<?> rows)) {
                continue;
            }
            for (Object raw : rows) {
                if (!(raw instanceof Map<?, ?> parent) || !(parent.get("__subTables__") instanceof Map<?, ?> nested)) {
                    continue;
                }
                for (Map.Entry<?, ?> entry : nested.entrySet()) {
                    if (!storeKey.equals(String.valueOf(entry.getKey()))) {
                        continue;
                    }
                    if (!(entry.getValue() instanceof List<?> children)) {
                        continue;
                    }
                    List<Object> updated = replaceNestedVersion(children, pkColumns, key, next);
                    if (updated != null) {
                        ((Map<String, Object>) nested).put(String.valueOf(entry.getKey()), updated);
                    }
                }
            }
        }
    }

    private static List<Object> replaceNestedVersion(List<?> children, List<String> pkColumns,
                                                      Map<String, Object> key, int next) {
        List<Object> updated = new ArrayList<>(children.size());
        boolean changed = false;
        for (Object childRaw : children) {
            if (!(childRaw instanceof Map<?, ?> child)) {
                updated.add(childRaw);
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> childMap = (Map<String, Object>) child;
            if (!keysMatch(rowKeyOf(childMap, pkColumns), key)) {
                updated.add(childRaw);
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>(childMap);
            copy.put(ROW_VERSION_FIELD, next);
            updated.add(copy);
            changed = true;
        }
        return changed ? updated : null;
    }

    private record BindingMeta(long id, String storeKey, String filterField, String filterRefType,
                               String filterRefTableName, Long filterRefTableId,
                               List<String> pkColumns, List<String> refPkColumns,
                               List<String> foreignKeyFields, List<String> explicitFillFields) {}

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
