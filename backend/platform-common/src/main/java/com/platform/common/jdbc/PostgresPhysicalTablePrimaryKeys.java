package com.platform.common.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
/**
 * Resolves ordered PRIMARY KEY column names for a physical table in the current PostgreSQL schema.
 * <p>If {@code information_schema} has no PRIMARY KEY constraint (common when DDL omitted the constraint
 * even though developer-workstation marks columns as PK in metadata), falls back to
 * {@code dw_table_definitions} / {@code dw_field_definitions} when those tables exist on the same
 * datasource — so designer PK fields such as {@code id_idw} are still honored.</p>
 */
public final class PostgresPhysicalTablePrimaryKeys {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final ConcurrentHashMap<String, List<String>> CACHE = new ConcurrentHashMap<>();

    private PostgresPhysicalTablePrimaryKeys() {
    }

    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * @param physicalTableName unquoted physical table name (e.g. {@code dw_data_12})
     * @return primary key columns in ordinal order (never empty; throws if no PK)
     */
    public static List<String> resolvePrimaryKeyColumns(JdbcTemplate jdbcTemplate, String physicalTableName) {
        if (physicalTableName == null || !SAFE_IDENTIFIER.matcher(physicalTableName).matches()) {
            throw new IllegalArgumentException("Invalid physical table name");
        }
        String cacheKey = physicalTableName.toLowerCase(Locale.ROOT);
        return CACHE.computeIfAbsent(cacheKey, k -> List.copyOf(resolvePkColumnsUncached(jdbcTemplate, k)));
    }

    private static List<String> resolvePkColumnsUncached(JdbcTemplate jdbcTemplate, String tableNameLower) {
        List<String> fromCatalog = queryInformationSchemaPkColumns(jdbcTemplate, tableNameLower);
        if (!fromCatalog.isEmpty()) {
            return fromCatalog;
        }
        List<String> fromDesigner = queryDwDesignerPkColumns(jdbcTemplate, tableNameLower);
        if (!fromDesigner.isEmpty()) {
            return fromDesigner;
        }
        throw new MissingPhysicalTablePrimaryKeyException(tableNameLower);
    }

    private static List<String> queryInformationSchemaPkColumns(JdbcTemplate jdbcTemplate, String tableName) {
        List<String> cols = jdbcTemplate.query(
                """
                        SELECT kcu.column_name
                        FROM information_schema.table_constraints tc
                        JOIN information_schema.key_column_usage kcu
                          ON tc.constraint_name = kcu.constraint_name
                         AND tc.table_schema = kcu.table_schema
                        WHERE tc.table_schema = current_schema()
                          AND tc.table_name = ?
                          AND tc.constraint_type = 'PRIMARY KEY'
                        ORDER BY kcu.ordinal_position
                        """,
                (rs, i) -> rs.getString(1),
                tableName);
        if (cols == null || cols.isEmpty()) {
            return List.of();
        }
        return validatePkColumnNames(cols, "catalog");
    }

    /**
     * Fallback when the physical table exists but has no PK constraint in PostgreSQL.
     * Uses the newest {@code dw_table_definitions} row matching {@code table_name} (highest {@code id}).
     */
    private static List<String> queryDwDesignerPkColumns(JdbcTemplate jdbcTemplate, String tableName) {
        try {
            List<String> cols = jdbcTemplate.query(
                    """
                            SELECT fd.field_name
                            FROM dw_field_definitions fd
                            WHERE fd.table_id = (
                                SELECT td.id
                                FROM dw_table_definitions td
                                WHERE lower(td.table_name) = lower(?)
                                ORDER BY td.id DESC
                                LIMIT 1
                            )
                              AND COALESCE(fd.is_primary_key, false) = true
                            ORDER BY fd.sort_order ASC, fd.id ASC
                            """,
                    (rs, i) -> rs.getString(1),
                    tableName);
            if (cols == null || cols.isEmpty()) {
                return List.of();
            }
            return validatePkColumnNames(cols, "dw_field_definitions");
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<String> validatePkColumnNames(List<String> cols, String source) {
        List<String> validated = new ArrayList<>(cols.size());
        for (String col : cols) {
            if (col == null || !SAFE_IDENTIFIER.matcher(col).matches()) {
                throw new IllegalStateException("Invalid primary key column name from " + source + ": " + col);
            }
            validated.add(col);
        }
        return validated;
    }

    /**
     * For SQL generation: comma-separated, validated identifiers only.
     */
    public static String commaJoinedPkSelect(List<String> pkCols) {
        return String.join(", ", pkCols);
    }
}
