package com.platform.common.jdbc;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Identity of a sub-table row as it is stored in JSON (the {@code __subTables__} process
 * variable), which is a different problem from {@link SubTableRowKeySupport}: that class
 * resolves the <em>physical</em> primary key columns of the few sub-tables that really have
 * a PostgreSQL table, and returns nothing for the rest. Designer sub-tables are JSON-row
 * stored, so their identity can only come from a key inside the row itself.
 *
 * <p>This is the single source for that key list and its priority. Anything that needs to
 * tell two sub-table rows apart — audit diffing, list de-duplication, the SQL that expands
 * {@code __subTables__} for Main Table Views — must read the order from here rather than
 * hard-coding a subset, and must never fall back to hashing the row's content: two rows
 * that happen to carry the same values are two rows, not one.
 */
public final class SubTableRowIdentity {

    /**
     * Candidate identity keys, most authoritative first. {@code rowId} and {@code row_id}
     * differ only by an underscore, so a case-insensitive lookup cannot collapse them —
     * both spellings are listed explicitly.
     */
    public static final List<String> IDENTITY_FIELDS = List.of(
            "row_id", "rowId", "rowID", "id_idw", "_rowKey", "rowKey", "id");

    /** The key written when a row arrives with no identity at all. */
    public static final String CANONICAL_FIELD = "row_id";

    private SubTableRowIdentity() {
    }

    /** @return the highest-priority identity key present on the row, or null if it has none */
    public static String identityFieldOf(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        for (String field : IDENTITY_FIELDS) {
            if (stringValue(SubTableRowKeySupport.getRowValueIgnoreCase(row, field)) != null) {
                return field;
            }
        }
        return null;
    }

    /**
     * @return {@code field=value} for the highest-priority identity key present, or null.
     *         The field name is part of the string so a row identified by {@code row_id=7}
     *         is not confused with one identified by {@code id=7}.
     */
    public static String identityOf(Map<String, Object> row) {
        String field = identityFieldOf(row);
        if (field == null) {
            return null;
        }
        return field + "=" + stringValue(SubTableRowKeySupport.getRowValueIgnoreCase(row, field));
    }

    public static boolean hasIdentity(Map<String, Object> row) {
        return identityFieldOf(row) != null;
    }

    /**
     * Every identity value the row carries, not only the highest-priority one. Two records
     * of the same row (say a user submission and its enriched counterpart) may each expose
     * a different subset of the keys, so they are the same row if any value matches.
     */
    public static Set<String> identityValuesOf(Map<String, Object> row) {
        Set<String> values = new LinkedHashSet<>();
        if (row == null) {
            return values;
        }
        for (String field : IDENTITY_FIELDS) {
            String value = stringValue(SubTableRowKeySupport.getRowValueIgnoreCase(row, field));
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    /**
     * Give the row an identity if it has none, so every row that reaches storage can be
     * addressed later. Rows that already carry one are left untouched — in particular a
     * designer-allocated primary key wins over a generated {@code row_id}.
     *
     * @return true when a {@code row_id} was assigned
     */
    public static boolean ensureIdentity(Map<String, Object> row) {
        if (row == null || hasIdentity(row)) {
            return false;
        }
        row.put(CANONICAL_FIELD, UUID.randomUUID().toString());
        return true;
    }

    /**
     * The same priority, expressed as SQL over a jsonb row.
     *
     * <p>Derived from {@link #IDENTITY_FIELDS} rather than written out, so SQL that
     * de-duplicates sub-table rows cannot drift from the Java that compares them. The two
     * sides need not produce byte-identical strings — only the same answer to "are these the
     * same row". Note SQL matches keys case-sensitively while Java does not; a row spelling
     * its key differently in case is therefore treated as having no identity in SQL, which
     * surfaces as an error rather than as a silent merge.
     *
     * @param rowExpression SQL expression yielding the row as jsonb, e.g. {@code expanded.elem}
     */
    public static String sqlIdentityExpression(String rowExpression) {
        StringBuilder sql = new StringBuilder("COALESCE(");
        for (int i = 0; i < IDENTITY_FIELDS.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(rowExpression).append("->>'").append(IDENTITY_FIELDS.get(i)).append('\'');
        }
        return sql.append(')').toString();
    }

    /** Blank strings do not identify anything, so they count as absent. */
    private static String stringValue(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = String.valueOf(raw).trim();
        return s.isEmpty() ? null : s;
    }
}
