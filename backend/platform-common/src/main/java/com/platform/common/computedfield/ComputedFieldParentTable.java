package com.platform.common.computedfield;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The Function Unit MAIN table a SUB-table formula may read via {@code table.column}.
 *
 * <p>Relation Tables have no parent; callers pass {@code null} and any qualified field reference
 * is rejected. MVP forbids depending on a computed MAIN column so a sub-row formula cannot form
 * a cycle with a MAIN aggregate that sums this same sub-table.
 *
 * @param tableName     physical MAIN table name as written in formulas
 * @param columnsByName MAIN columns, keyed by lower-cased name
 */
public record ComputedFieldParentTable(
        String tableName,
        Map<String, ComputedFieldCandidate> columnsByName) {

    /**
     * Builds a parent descriptor, indexing columns for case-insensitive lookup.
     *
     * @param tableName physical MAIN table name
     * @param columns   MAIN columns; null entries are ignored
     * @return the descriptor
     */
    public static ComputedFieldParentTable of(String tableName,
                                              Collection<ComputedFieldCandidate> columns) {
        Map<String, ComputedFieldCandidate> indexed = new LinkedHashMap<>();
        if (columns != null) {
            for (ComputedFieldCandidate column : columns) {
                if (column == null || column.fieldName() == null) {
                    continue;
                }
                indexed.put(column.fieldName().trim().toLowerCase(Locale.ROOT), column);
            }
        }
        return new ComputedFieldParentTable(tableName, indexed);
    }

    /**
     * Looks up a MAIN column by name.
     *
     * @param column column name in any case
     * @return the column, or null when it is not on the MAIN table
     */
    public ComputedFieldCandidate column(String column) {
        if (column == null) {
            return null;
        }
        return columnsByName.get(column.trim().toLowerCase(Locale.ROOT));
    }
}
