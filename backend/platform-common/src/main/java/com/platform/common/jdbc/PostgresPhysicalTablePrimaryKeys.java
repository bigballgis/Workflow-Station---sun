package com.platform.common.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
/**
 * Resolves ordered PRIMARY KEY column names for a physical table in the current PostgreSQL schema.
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
        return CACHE.computeIfAbsent(cacheKey, k -> List.copyOf(queryPkColumns(jdbcTemplate, k)));
    }

    private static List<String> queryPkColumns(JdbcTemplate jdbcTemplate, String tableNameForInformationSchema) {
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
                tableNameForInformationSchema);
        if (cols == null || cols.isEmpty()) {
            throw new MissingPhysicalTablePrimaryKeyException(tableNameForInformationSchema);
        }
        List<String> validated = new ArrayList<>(cols.size());
        for (String col : cols) {
            if (col == null || !SAFE_IDENTIFIER.matcher(col).matches()) {
                throw new IllegalStateException("Invalid primary key column name from catalog: " + col);
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
