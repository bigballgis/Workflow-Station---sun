package com.platform.common.sql;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility for safe SQL identifier quoting to prevent second-order SQL injection.
 * All identifiers (table names, column names) must pass validation before being
 * used in dynamic SQL. Uses PostgreSQL double-quote escaping.
 */
@Slf4j
public final class SafeSqlIdentifier {

    /** Pattern for valid SQL identifiers: starts with letter/underscore, then alphanumeric/underscore */
    private static final String IDENTIFIER_PATTERN = "^[a-zA-Z_][a-zA-Z0-9_]*$";

    /** Maximum allowed identifier length */
    private static final int MAX_IDENTIFIER_LENGTH = 128;

    private SafeSqlIdentifier() { /* utility class */ }

    /**
     * Validates a table or column name and returns it safely double-quoted for PostgreSQL.
     *
     * @param identifier the raw identifier to validate and quote
     * @return safely double-quoted identifier
     * @throws IllegalArgumentException if the identifier fails validation
     */
    public static String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("SQL identifier must not be null or blank");
        }
        String trimmed = identifier.trim();
        if (trimmed.length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(
                    "SQL identifier exceeds maximum length of " + MAX_IDENTIFIER_LENGTH + ": " + truncated(trimmed));
        }
        if (!trimmed.matches(IDENTIFIER_PATTERN)) {
            log.warn("Rejected unsafe SQL identifier: {}", truncated(trimmed));
            throw new IllegalArgumentException(
                    "SQL identifier contains invalid characters: " + truncated(trimmed));
        }
        // PostgreSQL double-quote escaping: replace " with ""
        String escaped = trimmed.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    /**
     * Validates and quotes a table name.
     * Uses the same rules as {@link #quoteIdentifier(String)}.
     */
    public static String quoteTableName(String tableName) {
        return quoteIdentifier(tableName);
    }

    /**
     * Validates and quotes a column name.
     * Uses the same rules as {@link #quoteIdentifier(String)}.
     */
    public static String quoteColumnName(String columnName) {
        return quoteIdentifier(columnName);
    }

    /**
     * Validates a table name without quoting. Use for whitelist checks.
     *
     * @return true if the table name is a valid SQL identifier
     */
    public static boolean isValidIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) return false;
        String trimmed = identifier.trim();
        return trimmed.length() <= MAX_IDENTIFIER_LENGTH && trimmed.matches(IDENTIFIER_PATTERN);
    }

    private static String truncated(String s) {
        return s.length() > 64 ? s.substring(0, 61) + "..." : s;
    }
}
