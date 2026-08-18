package com.platform.common.computedfield;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A table an aggregate formula is allowed to reach into, reduced to a name and its columns.
 *
 * <p>The caller decides what counts as reachable — for a Function Unit table that is its sibling
 * tables, for a Relation Table it is the tables it is related to — and excludes the table being
 * saved, since nothing may aggregate over itself.
 *
 * @param tableName   table name as written in formulas
 * @param columnNames every column the table exposes, already lower-cased for lookup
 */
public record ComputedFieldSubTable(String tableName, Set<String> columnNames) {

    /**
     * Builds a sub-table descriptor, normalising column names for case-insensitive lookup.
     *
     * @param tableName table name as written in formulas
     * @param columns   column names in any case; nulls are ignored
     * @return the descriptor
     */
    public static ComputedFieldSubTable of(String tableName, Collection<String> columns) {
        Set<String> normalized = new LinkedHashSet<>();
        if (columns != null) {
            for (String column : columns) {
                if (column != null) {
                    normalized.add(column.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return new ComputedFieldSubTable(tableName, normalized);
    }

    /**
     * Whether this table exposes the given column.
     *
     * @param column column name in any case
     * @return true when the column exists
     */
    public boolean hasColumn(String column) {
        return column != null && columnNames.contains(column.trim().toLowerCase(Locale.ROOT));
    }
}
