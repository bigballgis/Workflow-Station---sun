package com.platform.common.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Map form / variable keys to physical sub-table column names for data merge and write-back.
 * <p>Uses {@code field_name} and designer {@code dw_field_definitions.display_name} (UI label, often same as form
 * {@code title}); never treats arbitrary strings as SQL identifiers.</p>
 */
public final class SubTablePhysicalColumnResolver {

    private static final Pattern SAFE_TABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private SubTablePhysicalColumnResolver() {
    }

    /**
     * Resolves {@code candidateKey} to a key present in {@code physicalColumns}: exact match, then
     * case-insensitive identifier match, then a single designer field whose {@code display_name} equals the candidate
     * (trimmed) for the given {@code designerTableName}.
     *
     * @param jdbcTemplate      optional; if null, only exact / case-insensitive column match is attempted
     * @param designerTableName physical / designer table name (validated identifier)
     * @param candidateKey      variable name or label from the client / Flowable history
     * @param physicalColumns   column names present on the loaded row (e.g. {@code ResultSetMetaData})
     * @return canonical column name from {@code physicalColumns}, or null
     */
    public static String resolvePhysicalColumnKey(
            JdbcTemplate jdbcTemplate,
            String designerTableName,
            String candidateKey,
            Set<String> physicalColumns) {
        if (physicalColumns == null || physicalColumns.isEmpty() || candidateKey == null) {
            return null;
        }
        String trimmed = candidateKey.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (physicalColumns.contains(trimmed)) {
            return trimmed;
        }
        for (String c : physicalColumns) {
            if (c != null && c.equalsIgnoreCase(trimmed)) {
                return c;
            }
        }
        if (jdbcTemplate == null || designerTableName == null || !SAFE_TABLE_NAME.matcher(designerTableName).matches()) {
            return null;
        }
        try {
            List<String> matches = jdbcTemplate.query(
                    """
                            SELECT fd.field_name
                            FROM dw_field_definitions fd
                            WHERE fd.table_id = (
                                SELECT td.id FROM dw_table_definitions td
                                WHERE lower(td.table_name) = lower(?)
                                ORDER BY td.id DESC
                                LIMIT 1
                            )
                              AND fd.display_name IS NOT NULL
                              AND TRIM(fd.display_name) = TRIM(?)
                            """,
                    (rs, i) -> rs.getString(1),
                    designerTableName,
                    trimmed);
            if (matches.size() != 1) {
                return null;
            }
            String fn = matches.get(0);
            for (String c : physicalColumns) {
                if (c != null && c.equalsIgnoreCase(fn)) {
                    return c;
                }
            }
        } catch (Exception ignored) {
            // Dev DBs without dw_* tables or MR read-only: skip label resolution
        }
        return null;
    }
}
