package com.portal.component;

import com.platform.common.jdbc.SubTableRowIdentity;
import com.platform.common.jdbc.SubTableRowKeySupport;
import com.platform.common.subtable.SubTableStoreKeys;
import com.portal.dto.SubTableBindingScope;
import com.portal.exception.PortalException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Binding-scoped MI merge for a table-keyed {@code __subTables__} slice.
 *
 * <p>When the request carries {@code subTableBindingScopes}, classification uses those
 * binding ids — not every binding that happens to exist on the physical table. Mixed
 * MAIN + nested filters on one store keep other participants' nested rows at baseline.
 */
final class MiSubTableScopeAwareMerge {

    enum ScopeKind { SHARED, PARTICIPANT, MIXED }

    record ScopeFilter(String bindingId, String filterField, String refTableType, String linkMode) {}

    @FunctionalInterface
    interface ParticipantRowMerge {
        List<Object> merge(List<String> fkColumns, boolean explicitlyEmptied);
    }

    private MiSubTableScopeAwareMerge() {
    }

    static Map<String, List<SubTableBindingScope>> indexByStoreKey(List<SubTableBindingScope> scopes) {
        Map<String, List<SubTableBindingScope>> byKey = new LinkedHashMap<>();
        if (scopes == null) {
            return byKey;
        }
        for (SubTableBindingScope scope : scopes) {
            if (scope == null || !StringUtils.hasText(scope.getBindingId())) {
                throw unresolved("Binding scope is missing bindingId");
            }
            if (!StringUtils.hasText(scope.getStoreKey())) {
                throw unresolved("Binding scope is missing storeKey");
            }
            byKey.computeIfAbsent(scope.getStoreKey(), k -> new ArrayList<>()).add(scope);
        }
        return byKey;
    }

    static List<Object> apply(
            JdbcTemplate jdbc,
            String storeKey,
            List<Object> submittedRows,
            List<Object> baselineRows,
            Map<String, Object> currentItemKey,
            List<SubTableBindingScope> keyScopes,
            boolean storeKeyEmptied,
            String functionUnitCode,
            ParticipantRowMerge participantMerge) {
        List<ScopeFilter> filters = loadFilters(jdbc, keyScopes, storeKey, functionUnitCode);
        ScopeKind kind = classify(filters);
        if (kind == ScopeKind.SHARED) {
            return new ArrayList<>(submittedRows);
        }
        boolean emptied = storeKeyEmptied || anyEmptied(keyScopes);
        if (kind == ScopeKind.PARTICIPANT) {
            return participantMerge.merge(nestedFilterFields(filters), emptied);
        }
        return mergeMixed(submittedRows, baselineRows, currentItemKey, keyScopes, filters);
    }

    static List<ScopeFilter> loadFilters(
            JdbcTemplate jdbc, List<SubTableBindingScope> keyScopes, String storeKey, String functionUnitCode) {
        if (!StringUtils.hasText(functionUnitCode)) {
            throw unresolved("Binding-scoped merge requires a function unit on the process");
        }
        List<Long> ids = bindingIds(keyScopes);
        if (ids.isEmpty()) {
            throw unresolved("Binding scopes are missing a binding identity");
        }
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        String sql = """
                SELECT CAST(b.id AS varchar) AS binding_id,
                       f.field_name AS filter_field,
                       ref.table_type AS ref_table_type,
                       b.binding_link_mode AS link_mode,
                       lower(t.table_name) AS table_name
                FROM dw_form_table_bindings b
                JOIN dw_form_definitions fd ON fd.id = b.form_id
                JOIN dw_function_units fu ON fu.id = fd.function_unit_id
                JOIN dw_table_definitions t ON t.id = b.table_id
                LEFT JOIN dw_field_definitions f ON f.id = b.filter_fk_field_id
                LEFT JOIN dw_table_definitions ref ON ref.id = f.ref_table_id
                WHERE b.id IN (""" + placeholders + ") AND fu.code = ? /* mi-scope-binding-filter */";
        RowMapper<ScopeFilter> mapper = (rs, i) -> {
            String table = rs.getString("table_name");
            if (StringUtils.hasText(storeKey) && StringUtils.hasText(table)
                    && !storeKey.equalsIgnoreCase(SubTableStoreKeys.DW_PREFIX + table)) {
                throw new PortalException("403", "Binding scope storeKey does not match the binding's table");
            }
            return new ScopeFilter(
                    rs.getString("binding_id"),
                    rs.getString("filter_field"),
                    rs.getString("ref_table_type"),
                    rs.getString("link_mode"));
        };
        Object[] args = new Object[ids.size() + 1];
        for (int i = 0; i < ids.size(); i++) {
            args[i] = ids.get(i);
        }
        args[ids.size()] = functionUnitCode.trim();
        List<ScopeFilter> loaded = jdbc.query(sql, mapper, args);
        if (loaded == null || loaded.size() != ids.size()) {
            throw new PortalException("403", "Binding is not part of this function unit");
        }
        return loaded;
    }

    static ScopeKind classify(List<ScopeFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            throw unresolved("Binding scopes did not resolve any filter declarations");
        }
        boolean anyMain = false;
        boolean anyNested = false;
        for (ScopeFilter filter : filters) {
            if (isMiParticipantRow(filter)) {
                anyNested = true;
                continue;
            }
            if (!StringUtils.hasText(filter.refTableType())) {
                throw unresolved("Binding " + filter.bindingId() + " has no declared filter FK target");
            }
            if ("MAIN".equalsIgnoreCase(filter.refTableType())) {
                anyMain = true;
            } else {
                anyNested = true;
            }
        }
        if (anyMain && anyNested) {
            return ScopeKind.MIXED;
        }
        return anyMain ? ScopeKind.SHARED : ScopeKind.PARTICIPANT;
    }

    static List<Object> mergeMixed(
            List<Object> submittedRows,
            List<Object> baselineRows,
            Map<String, Object> currentItemKey,
            List<SubTableBindingScope> keyScopes,
            List<ScopeFilter> filters) {
        if (currentItemKey == null || currentItemKey.size() != 1) {
            throw unresolved("Mixed MAIN/nested bindings need a single-column MI row key");
        }
        String participantId = singleValue(currentItemKey);
        if (!StringUtils.hasText(participantId)) {
            throw unresolved("Mixed MAIN/nested bindings need a single-column MI row key");
        }
        List<String> nestedFields = nestedFilterFields(filters);
        if (nestedFields.isEmpty()) {
            throw unresolved("Mixed MAIN/nested bindings need a declared nested filter FK column");
        }
        List<SubTableBindingScope> mainScopes = scopesWithKind(keyScopes, filters, true);
        List<SubTableBindingScope> nestedScopes = scopesWithKind(keyScopes, filters, false);
        List<String> pkCols = pkColumnsFromScopes(keyScopes);
        boolean nestedEmptied = anyEmptied(nestedScopes);
        boolean mainEmptied = anyEmptied(mainScopes);
        return mergeMixedRows(submittedRows, baselineRows, pkCols, nestedFields, participantId,
                mainScopes, nestedScopes, mainEmptied, nestedEmptied);
    }

    static List<String> nestedFilterFields(List<ScopeFilter> filters) {
        List<String> fields = new ArrayList<>();
        for (ScopeFilter filter : filters) {
            if (isSharedFilter(filter) || !StringUtils.hasText(filter.filterField())) {
                continue;
            }
            if (!fields.contains(filter.filterField())) {
                fields.add(filter.filterField());
            }
        }
        return fields;
    }

    static boolean anyEmptied(List<SubTableBindingScope> scopes) {
        if (scopes == null) {
            return false;
        }
        for (SubTableBindingScope scope : scopes) {
            if (scope != null && Boolean.TRUE.equals(scope.getEmptied())) {
                return true;
            }
        }
        return false;
    }

    private static List<Object> mergeMixedRows(
            List<Object> submittedRows,
            List<Object> baselineRows,
            List<String> pkCols,
            List<String> nestedFields,
            String participantId,
            List<SubTableBindingScope> mainScopes,
            List<SubTableBindingScope> nestedScopes,
            boolean mainEmptied,
            boolean nestedEmptied) {
        List<Map<String, Object>> submitted = mapsOf(submittedRows);
        List<Map<String, Object>> baseline = mapsOf(baselineRows);
        List<Object> out = new ArrayList<>();
        Set<Integer> usedSubmitted = new LinkedHashSet<>();
        for (Map<String, Object> base : baseline) {
            int subIdx = indexOfSameRow(submitted, base, pkCols);
            Map<String, Object> sub = subIdx >= 0 ? submitted.get(subIdx) : null;
            if (belongsToOther(base, nestedFields, participantId)
                    || belongsToOther(sub, nestedFields, participantId)) {
                out.add(base);
                continue;
            }
            boolean mainClaim = claimedBy(mainScopes, base, pkCols);
            boolean nestedClaim = claimedBy(nestedScopes, base, pkCols);
            boolean ours = belongsToUs(base, nestedFields, participantId)
                    || belongsToUs(sub, nestedFields, participantId);
            if (replaceWithSubmitted(sub, mainClaim, nestedClaim, ours)) {
                out.add(sub);
                usedSubmitted.add(subIdx);
            } else if (keepBaseline(mainClaim, nestedClaim, ours, mainEmptied, nestedEmptied)) {
                out.add(base);
            }
        }
        appendNewClaimed(out, submitted, usedSubmitted, pkCols, nestedFields, participantId,
                mainScopes, nestedScopes);
        return out;
    }

    private static boolean replaceWithSubmitted(
            Map<String, Object> sub, boolean mainClaim, boolean nestedClaim, boolean ours) {
        return sub != null && (mainClaim || nestedClaim || ours);
    }

    private static boolean keepBaseline(
            boolean mainClaim, boolean nestedClaim, boolean ours,
            boolean mainEmptied, boolean nestedEmptied) {
        if (ours && nestedEmptied && !nestedClaim) {
            return false;
        }
        if (mainClaim && mainEmptied) {
            return false;
        }
        if (nestedClaim && nestedEmptied) {
            return false;
        }
        return true;
    }

    private static void appendNewClaimed(
            List<Object> out,
            List<Map<String, Object>> submitted,
            Set<Integer> usedSubmitted,
            List<String> pkCols,
            List<String> nestedFields,
            String participantId,
            List<SubTableBindingScope> mainScopes,
            List<SubTableBindingScope> nestedScopes) {
        for (int i = 0; i < submitted.size(); i++) {
            if (usedSubmitted.contains(i)) {
                continue;
            }
            Map<String, Object> sub = submitted.get(i);
            if (belongsToOther(sub, nestedFields, participantId)) {
                continue;
            }
            if (claimedBy(mainScopes, sub, pkCols)
                    || claimedBy(nestedScopes, sub, pkCols)
                    || belongsToUs(sub, nestedFields, participantId)) {
                out.add(sub);
            }
        }
    }

    private static boolean claimedBy(
            List<SubTableBindingScope> scopes, Map<String, Object> row, List<String> pkCols) {
        if (scopes == null || row == null) {
            return false;
        }
        for (SubTableBindingScope scope : scopes) {
            if (scope.getRowKeys() == null) {
                continue;
            }
            for (Map<String, Object> key : scope.getRowKeys()) {
                if (sameClaimedKey(row, key, pkCols)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean sameClaimedKey(
            Map<String, Object> row, Map<String, Object> claimed, List<String> pkCols) {
        if (claimed == null || claimed.isEmpty()) {
            return false;
        }
        List<String> cols = pkCols.isEmpty() ? List.copyOf(claimed.keySet()) : pkCols;
        Map<String, Object> rowKey = SubTableRowKeySupport.rowKeyFromVariableRow(row, cols);
        return rowKeysEqual(rowKey, claimed, cols);
    }

    private static boolean rowKeysEqual(
            Map<String, Object> a, Map<String, Object> b, List<String> cols) {
        if (a == null || b == null) {
            return false;
        }
        for (String col : cols) {
            Object av = SubTableRowKeySupport.getRowValueIgnoreCase(a, col);
            Object bv = SubTableRowKeySupport.getRowValueIgnoreCase(b, col);
            if (av == null || bv == null
                    || !String.valueOf(av).trim().equals(String.valueOf(bv).trim())) {
                return false;
            }
        }
        return !cols.isEmpty();
    }

    private static int indexOfSameRow(
            List<Map<String, Object>> rows, Map<String, Object> want, List<String> pkCols) {
        for (int i = 0; i < rows.size(); i++) {
            if (sameRow(rows.get(i), want, pkCols)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean sameRow(Map<String, Object> a, Map<String, Object> b, List<String> pkCols) {
        if (a == null || b == null) {
            return false;
        }
        if (!pkCols.isEmpty()) {
            Map<String, Object> ak = SubTableRowKeySupport.rowKeyFromVariableRow(a, pkCols);
            Map<String, Object> bk = SubTableRowKeySupport.rowKeyFromVariableRow(b, pkCols);
            if (rowKeysEqual(ak, bk, pkCols)) {
                return true;
            }
        }
        Set<String> av = SubTableRowIdentity.identityValuesOf(a, pkCols);
        Set<String> bv = SubTableRowIdentity.identityValuesOf(b, pkCols);
        if (av.isEmpty() || bv.isEmpty()) {
            return false;
        }
        for (String value : av) {
            if (bv.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean belongsToOther(
            Map<String, Object> row, List<String> nestedFields, String participantId) {
        if (row == null || participantId == null) {
            return false;
        }
        for (String field : nestedFields) {
            Object v = SubTableRowKeySupport.getRowValueIgnoreCase(row, field);
            if (v == null || String.valueOf(v).trim().isEmpty()) {
                continue;
            }
            if (!participantId.equals(String.valueOf(v).trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean belongsToUs(
            Map<String, Object> row, List<String> nestedFields, String participantId) {
        if (row == null || participantId == null) {
            return false;
        }
        for (String field : nestedFields) {
            Object v = SubTableRowKeySupport.getRowValueIgnoreCase(row, field);
            if (v != null && participantId.equals(String.valueOf(v).trim())) {
                return true;
            }
        }
        return false;
    }

    private static List<Map<String, Object>> mapsOf(List<Object> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (Object row : rows) {
            if (row instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                out.add(typed);
            }
        }
        return out;
    }

    private static List<SubTableBindingScope> scopesWithKind(
            List<SubTableBindingScope> scopes, List<ScopeFilter> filters, boolean main) {
        Set<String> ids = new LinkedHashSet<>();
        for (ScopeFilter filter : filters) {
            if (isSharedFilter(filter) == main) {
                ids.add(filter.bindingId());
            }
        }
        List<SubTableBindingScope> out = new ArrayList<>();
        for (SubTableBindingScope scope : scopes) {
            if (scope.getBindingId() != null && ids.contains(scope.getBindingId().trim())) {
                out.add(scope);
            }
        }
        return out;
    }

    private static boolean isSharedFilter(ScopeFilter filter) {
        return !isMiParticipantRow(filter) && "MAIN".equalsIgnoreCase(filter.refTableType());
    }

    private static boolean isMiParticipantRow(ScopeFilter filter) {
        return filter.linkMode() != null && "miParticipantRow".equalsIgnoreCase(filter.linkMode());
    }

    private static List<String> pkColumnsFromScopes(List<SubTableBindingScope> scopes) {
        for (SubTableBindingScope scope : scopes) {
            if (scope.getRowKeys() == null) {
                continue;
            }
            for (Map<String, Object> key : scope.getRowKeys()) {
                if (key != null && !key.isEmpty()) {
                    return List.copyOf(key.keySet());
                }
            }
        }
        return List.of();
    }

    private static List<Long> bindingIds(List<SubTableBindingScope> scopes) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (SubTableBindingScope scope : scopes) {
            if (scope == null || !StringUtils.hasText(scope.getBindingId())) {
                throw unresolved("Binding scope is missing bindingId");
            }
            try {
                ids.add(Long.parseLong(scope.getBindingId().trim()));
            } catch (NumberFormatException ex) {
                throw unresolved("Binding scope id is not a binding identity");
            }
        }
        return List.copyOf(ids);
    }

    private static String singleValue(Map<String, Object> rowKey) {
        Object v = rowKey.values().iterator().next();
        return v == null ? null : String.valueOf(v).trim();
    }

    private static PortalException unresolved(String message) {
        return new PortalException("MI_SCOPE_FILTER_UNRESOLVED", message);
    }
}
