package com.platform.common.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;

/**
 * Map form / variable keys to physical sub-table column names for data merge and write-back.
 * <p>Matches column names only; never treats arbitrary strings as SQL identifiers. Designer labels are not
 * consulted: the runtime never reads Developer Workstation's design tables.</p>
 */
public final class SubTablePhysicalColumnResolver {

    private SubTablePhysicalColumnResolver() {
    }

    /**
     * Resolves {@code candidateKey} to a key present in {@code physicalColumns}: exact match, then
     * case-insensitive identifier match.
     *
     * @param jdbcTemplate      not used (kept for existing callers)
     * @param designerTableName not used (kept for existing callers)
     * @param candidateKey      variable name from the client / Flowable history
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
        return null;
    }
}
