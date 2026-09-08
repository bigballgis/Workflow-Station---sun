package com.portal.component;

import com.platform.common.jdbc.SubTableRowIdentity;
import com.platform.common.jdbc.SubTableRowKeySupport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges nested-lifted rows with later top-level slices by persisted identity
 * (designer PK first, then {@code row_id}). Blank values never overwrite a
 * filled field, so a thin nested copy cannot clear Email that a richer copy
 * already holds.
 *
 * <p>Persist writes use {@link #overlaySubmittedOnBaseline}: submitted membership
 * still wins (real DELETE/ADD), but a blank field on a matching identity cannot
 * clear a filled persisted value. A filled submitted value still overwrites
 * (Draft → Sent).
 */
final class ChangeHistorySubTableSliceMerger {

    private ChangeHistorySubTableSliceMerger() {
    }

    static void mergeSliceRows(
            Map<String, Map<String, Object>> rowsByIdentity,
            List<Map<String, Object>> filteredRows) {
        mergeSliceRows(rowsByIdentity, filteredRows, List.of());
    }

    static void mergeSliceRows(
            Map<String, Map<String, Object>> rowsByIdentity,
            List<Map<String, Object>> filteredRows,
            List<String> pkFields) {
        if (filteredRows == null) {
            return;
        }
        List<String> keys = pkFields == null ? List.of() : pkFields;
        for (Map<String, Object> row : filteredRows) {
            if (row == null) {
                continue;
            }
            Map<String, Object> candidate = row instanceof LinkedHashMap
                    ? row
                    : new LinkedHashMap<>(row);
            ChangeHistoryAuditRowKey.stamp(candidate, keys);
            String matchKey = findMatchingKey(rowsByIdentity, candidate, keys);
            if (matchKey != null) {
                Map<String, Object> merged = unionPreferringValues(rowsByIdentity.get(matchKey), candidate);
                ChangeHistoryAuditRowKey.stamp(merged, keys);
                String newKey = ChangeHistoryAuditRowKey.resolve(merged);
                if (newKey != null && !newKey.equals(matchKey)) {
                    rowsByIdentity.remove(matchKey);
                    rowsByIdentity.put(newKey, merged);
                } else {
                    rowsByIdentity.put(matchKey, merged);
                }
                continue;
            }
            String identity = ChangeHistoryAuditRowKey.resolve(candidate);
            if (identity == null) {
                continue;
            }
            rowsByIdentity.put(identity, candidate);
        }
    }

    static void mergeFilteredTableRows(
            Map<String, Map<String, Map<String, Object>>> rowsByTableAndIdentity,
            Map<String, Object> filteredTables,
            ChangeHistoryBindingAliases aliases) {
        if (filteredTables == null || filteredTables.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : filteredTables.entrySet()) {
            if (!(entry.getValue() instanceof List<?> rows)) {
                continue;
            }
            Map<String, Map<String, Object>> rowsByIdentity = rowsByTableAndIdentity
                    .computeIfAbsent(entry.getKey(), ignored -> new LinkedHashMap<>());
            mergeSliceRows(rowsByIdentity, castRows(rows), primaryKeyFields(aliases, entry.getKey()));
        }
    }

    static Object retainSubmittedTables(Object filteredPersisted, Object submittedFiltered) {
        if (!(filteredPersisted instanceof Map<?, ?> persisted)
                || !(submittedFiltered instanceof Map<?, ?> submitted)) {
            return filteredPersisted;
        }
        if (submitted.isEmpty()) {
            return filteredPersisted;
        }
        Set<String> submittedNames = new LinkedHashSet<>();
        for (Object key : submitted.keySet()) {
            String name = ChangeHistoryComponent.normalizeSubTableNameForHistory(
                    key == null ? null : String.valueOf(key));
            if (name != null) {
                submittedNames.add(name);
            }
        }
        Map<String, Object> retained = new LinkedHashMap<>();
        persisted.forEach((key, value) -> {
            String name = ChangeHistoryComponent.normalizeSubTableNameForHistory(
                    key == null ? null : String.valueOf(key));
            if (name != null && submittedNames.contains(name)) {
                retained.put(String.valueOf(key), value);
            }
        });
        return retained;
    }

    /**
     * Combines persisted {@code __subTables__} with a complete/save payload.
     * Slice keys follow the submitted map. When a submitted key does not exist
     * on persist (numeric binding id vs {@code dw:name}), overlay may adopt
     * persist rows that share a {@code row_id}-family value — never a bare
     * {@code id}, and never by concatenating slices from more than one
     * identity group.
     */
    static Object overlaySubmittedOnBaseline(Object baseline, Object submitted) {
        if (!(submitted instanceof Map<?, ?> submittedMap) || submittedMap.isEmpty()) {
            return submitted;
        }
        if (!(baseline instanceof Map<?, ?> baselineMap) || baselineMap.isEmpty()) {
            return submitted;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : submittedMap.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (!(value instanceof List<?> submittedRows)) {
                result.put(key, value);
                continue;
            }
            result.put(key, overlayRowList(
                    baselineRowsForSubmittedSlice(baselineMap, key, submittedRows),
                    submittedRows));
        }
        return result;
    }

    static Map<String, Object> overlayNonBlankFields(
            Map<String, Object> baseline, Map<String, Object> submitted) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (baseline != null) {
            merged.putAll(baseline);
        }
        if (submitted == null) {
            return merged;
        }
        for (Map.Entry<String, Object> field : submitted.entrySet()) {
            if (isBlankAuditValue(field.getValue())) {
                continue;
            }
            merged.put(field.getKey(), field.getValue());
        }
        return merged;
    }

    static Map<String, Object> unionPreferringValues(
            Map<String, Object> left, Map<String, Object> right) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (left != null) {
            merged.putAll(left);
        }
        if (right == null) {
            return merged;
        }
        for (Map.Entry<String, Object> field : right.entrySet()) {
            Object incoming = field.getValue();
            if (isBlankAuditValue(incoming)) {
                continue;
            }
            Object existing = merged.get(field.getKey());
            if (isBlankAuditValue(existing)) {
                merged.put(field.getKey(), incoming);
            }
        }
        return merged;
    }

    static boolean isBlankAuditValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        return false;
    }

    private static String findMatchingKey(
            Map<String, Map<String, Object>> rowsByIdentity,
            Map<String, Object> row,
            List<String> pkFields) {
        for (Map.Entry<String, Map<String, Object>> entry : rowsByIdentity.entrySet()) {
            if (ChangeHistoryAuditRowKey.sameLogicalRow(entry.getValue(), row, pkFields)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static List<String> primaryKeyFields(ChangeHistoryBindingAliases aliases, String historyName) {
        if (aliases == null || historyName == null) {
            return List.of();
        }
        for (Map.Entry<String, String> entry : aliases.bindingToHistoryName().entrySet()) {
            if (historyName.equalsIgnoreCase(entry.getValue())) {
                return aliases.primaryKeyFields(entry.getKey());
            }
        }
        String bindingId = aliases.aliasToBinding().get(ChangeHistoryFilterMaps.normalizeAlias(historyName));
        return aliases.primaryKeyFields(bindingId);
    }

    private static List<Map<String, Object>> baselineRowsForSubmittedSlice(
            Map<?, ?> baselineMap, String submittedKey, List<?> submittedRows) {
        Object exact = baselineMap.get(submittedKey);
        if (exact instanceof List<?> list && !list.isEmpty()) {
            return castRows(list);
        }
        for (Map.Entry<?, ?> entry : baselineMap.entrySet()) {
            if (entry.getKey() != null
                    && submittedKey.equalsIgnoreCase(String.valueOf(entry.getKey()))
                    && entry.getValue() instanceof List<?> list
                    && !list.isEmpty()) {
                return castRows(list);
            }
        }
        return identityOverlappingBaselineRows(baselineMap, submittedRows);
    }

    private static List<Map<String, Object>> identityOverlappingBaselineRows(
            Map<?, ?> baselineMap, List<?> submittedRows) {
        Set<String> submittedIds = rowIdFamilyValuesOfRows(castRows(submittedRows));
        if (submittedIds.isEmpty()) {
            return List.of();
        }
        List<List<Map<String, Object>>> candidates = new ArrayList<>();
        List<Set<String>> fingerprints = new ArrayList<>();
        for (Map.Entry<?, ?> entry : baselineMap.entrySet()) {
            if (!(entry.getValue() instanceof List<?> list)) {
                continue;
            }
            List<Map<String, Object>> candidate = castRows(list);
            Set<String> sliceIds = rowIdFamilyValuesOfRows(candidate);
            if (sliceIds.isEmpty() || disjoint(submittedIds, sliceIds)) {
                continue;
            }
            candidates.add(candidate);
            fingerprints.add(sliceIds);
        }
        if (candidates.isEmpty() || countConnectedGroups(fingerprints) != 1) {
            return List.of();
        }
        Map<String, Map<String, Object>> collapsed = new LinkedHashMap<>();
        for (List<Map<String, Object>> slice : candidates) {
            mergeSliceRows(collapsed, slice, List.of());
        }
        return new ArrayList<>(collapsed.values());
    }

    private static Set<String> rowIdFamilyValuesOfRows(List<Map<String, Object>> rows) {
        Set<String> values = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            values.addAll(rowIdFamilyValues(row));
        }
        return values;
    }

    private static Set<String> rowIdFamilyValues(Map<String, Object> row) {
        Set<String> values = new LinkedHashSet<>();
        if (row == null) {
            return values;
        }
        for (String field : SubTableRowIdentity.IDENTITY_FIELDS) {
            if ("id".equals(field)) {
                continue;
            }
            Object raw = SubTableRowKeySupport.getRowValueIgnoreCase(row, field);
            if (raw == null) {
                continue;
            }
            String text = String.valueOf(raw).trim();
            if (!text.isEmpty()) {
                values.add(text);
            }
        }
        return values;
    }

    private static boolean disjoint(Set<String> left, Set<String> right) {
        for (String value : left) {
            if (right.contains(value)) {
                return false;
            }
        }
        return true;
    }

    private static int countConnectedGroups(List<Set<String>> fingerprints) {
        int n = fingerprints.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (!disjoint(fingerprints.get(i), fingerprints.get(j))) {
                    parent[findRoot(parent, j)] = findRoot(parent, i);
                }
            }
        }
        Set<Integer> roots = new HashSet<>();
        for (int i = 0; i < n; i++) {
            roots.add(findRoot(parent, i));
        }
        return roots.size();
    }

    private static int findRoot(int[] parent, int index) {
        while (parent[index] != index) {
            parent[index] = parent[parent[index]];
            index = parent[index];
        }
        return index;
    }

    private static List<Map<String, Object>> overlayRowList(
            List<Map<String, Object>> baselineRows, List<?> submittedRows) {
        List<Map<String, Object>> remaining = new ArrayList<>(baselineRows);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> submitted : castRows(submittedRows)) {
            Map<String, Object> match = takeMatching(remaining, submitted);
            result.add(match == null
                    ? new LinkedHashMap<>(submitted)
                    : overlayNonBlankFields(match, submitted));
        }
        return result;
    }

    private static Map<String, Object> takeMatching(
            List<Map<String, Object>> remaining, Map<String, Object> submitted) {
        for (int i = 0; i < remaining.size(); i++) {
            if (ChangeHistoryAuditRowKey.sameLogicalRow(remaining.get(i), submitted, List.of())) {
                return remaining.remove(i);
            }
        }
        return null;
    }

    private static List<Map<String, Object>> castRows(List<?> rows) {
        List<Map<String, Object>> typed = new ArrayList<>();
        for (Object rowObj : rows) {
            if (rowObj instanceof Map<?, ?> rawRow) {
                typed.add(ChangeHistoryFilterMaps.castMap(rawRow));
            }
        }
        return typed;
    }
}
