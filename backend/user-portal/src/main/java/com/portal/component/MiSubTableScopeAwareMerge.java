package com.portal.component;

import com.platform.common.jdbc.SubTableRowIdentity;
import com.platform.common.jdbc.SubTableRowKeySupport;
import com.portal.dto.SubTableBindingScope;
import com.portal.exception.PortalException;
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

    /**
     * @param frozen the bindings of the form as published in the request's release; the scopes are
     *               classified from these only, never from the live designer
     */
    static List<Object> apply(String storeKey, List<Object> submittedRows,
            List<Object> baselineRows, Map<String, Object> currentItemKey,
            List<SubTableBindingScope> keyScopes, boolean storeKeyEmptied,
            ParticipantRowMerge participantMerge, Map<String, SubTableWriteDesign.Binding> frozen) {
        if (frozen == null) {
            throw new com.platform.common.functionunit.FunctionUnitVersionException("VERSION_RECOVERY_REQUIRED", storeKey);
        }
        List<ScopeFilter> filters = keyScopes.stream().map(scope -> {
            if (scope == null || !StringUtils.hasText(scope.getBindingId())) {
                throw new PortalException("400", "Binding scope is missing bindingId");
            }
            SubTableWriteDesign.Binding b = frozen.get(scope.getBindingId().trim());
            if (b == null || !storeKey.equals(b.storeKey())) {
                throw new PortalException("403", "Binding is not part of the pinned form");
            }
            return new ScopeFilter(b.id(), b.filterField(), b.refType(), b.linkMode());
        }).toList();
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

    private static String singleValue(Map<String, Object> rowKey) {
        Object v = rowKey.values().iterator().next();
        return v == null ? null : String.valueOf(v).trim();
    }

    private static PortalException unresolved(String message) {
        return new PortalException("MI_SCOPE_FILTER_UNRESOLVED", message);
    }
}
