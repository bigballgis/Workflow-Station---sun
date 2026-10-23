package com.portal.component;

import com.platform.common.functionunit.CatalogSnapshotReader;
import com.platform.common.functionunit.FunctionUnitVersionException;
import com.platform.common.subtable.SubTableStoreKeys;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves the primary-key columns a change-history slice's table declares, in the release the
 * request was started on — never in the live designer, which may have changed the key since.
 */
final class ChangeHistoryDesignerPrimaryKeyLookup {

    private ChangeHistoryDesignerPrimaryKeyLookup() {
    }

    static List<String> resolve(CatalogSnapshotReader snapshots, String catalogId, String sliceKey) {
        if (sliceKey == null || sliceKey.isBlank()) {
            return List.of();
        }
        if (catalogId == null || catalogId.isBlank()) {
            throw new FunctionUnitVersionException("VERSION_RECOVERY_REQUIRED", sliceKey);
        }
        Map<String, Map<String, Object>> tables = new LinkedHashMap<>();
        for (Map<String, Object> table : snapshots.contents(catalogId, "DATA_TABLE")) {
            Object name = table.get("tableName");
            if (name != null) {
                tables.putIfAbsent(String.valueOf(name).toLowerCase(Locale.ROOT), table);
            }
        }
        String tableName = SubTableStoreKeys.tableNameOf(sliceKey);
        if (tableName != null && !tableName.isBlank()) {
            return primaryKeys(tables, tableName);
        }
        if (sliceKey.chars().allMatch(Character::isDigit)) {
            return primaryKeys(tables, tableNameOfBinding(snapshots, catalogId, sliceKey));
        }
        List<String> byHistoryName = primaryKeys(tables, sliceKey);
        if (!byHistoryName.isEmpty()) {
            return byHistoryName;
        }
        String recovered = tableNameFromHistoryNormalizedCanonicalKey(sliceKey);
        return recovered != null ? primaryKeys(tables, recovered) : List.of();
    }

    /**
     * {@link ChangeHistoryComponent#normalizeSubTableNameForHistory} replaces non-alphanumerics
     * with {@code _}, so {@link SubTableStoreKeys#DW_PREFIX} {@code dw:} becomes {@code dw_}.
     * Invert that encoding; do not treat an arbitrary {@code dw_*} physical table name as a prefix.
     */
    static String tableNameFromHistoryNormalizedCanonicalKey(String historyName) {
        if (historyName == null || historyName.isBlank()) {
            return null;
        }
        String dwHistoryPrefix = storeKeyHistoryPrefix(SubTableStoreKeys.DW_PREFIX);
        if (historyName.startsWith(dwHistoryPrefix)
                && historyName.length() > dwHistoryPrefix.length()) {
            return historyName.substring(dwHistoryPrefix.length());
        }
        String rtHistoryPrefix = storeKeyHistoryPrefix(SubTableStoreKeys.RT_PREFIX);
        if (historyName.startsWith(rtHistoryPrefix)
                && historyName.length() > rtHistoryPrefix.length()) {
            return historyName.substring(rtHistoryPrefix.length());
        }
        return null;
    }

    private static String storeKeyHistoryPrefix(String storePrefix) {
        return storePrefix.replace(':', '_');
    }

    /** The table's primary-key fields in its declared field order. */
    private static List<String> primaryKeys(Map<String, Map<String, Object>> tables, String tableName) {
        Map<String, Object> table = tableName == null ? null : tables.get(tableName.toLowerCase(Locale.ROOT));
        if (table == null) {
            return List.of();
        }
        return CatalogSnapshotReader.objects(table.get("fields")).stream()
                .filter(field -> Boolean.TRUE.equals(field.get("isPrimaryKey")) && field.get("fieldName") != null)
                .sorted(Comparator.comparing(ChangeHistoryDesignerPrimaryKeyLookup::sortOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(field -> String.valueOf(field.get("fieldName")))
                .toList();
    }

    private static Integer sortOrder(Map<String, Object> field) {
        return field.get("sortOrder") instanceof Number number ? number.intValue() : null;
    }

    /** A numeric slice key is a designer binding id: the release's forms say which table it binds. */
    private static String tableNameOfBinding(CatalogSnapshotReader snapshots, String catalogId, String bindingId) {
        for (Map<String, Object> form : snapshots.contents(catalogId, "FORM")) {
            for (Map<String, Object> binding : CatalogSnapshotReader.objects(
                    form.getOrDefault("tableBindings", List.of()))) {
                if (bindingId.equals(String.valueOf(binding.get("bindingId"))) && binding.get("tableName") != null) {
                    return String.valueOf(binding.get("tableName"));
                }
            }
        }
        return null;
    }
}
