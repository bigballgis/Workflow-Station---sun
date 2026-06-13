package com.portal.component;

import com.platform.common.jdbc.PostgresPhysicalTablePrimaryKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sub-table physical-table metadata (PK columns, existence) is queried per row/slice while walking the
 * recursive {@code __subTables__} payload, which previously fired tens of thousands of identical
 * information_schema / to_regclass queries for the same handful of table names (see issue: portal task
 * detail 30s load). Schema is stable at runtime, so memoize per table name. Business tables are JSON-row
 * stored (no physical table) per json-row-storage rule, so most lookups are stable "absent" results.
 * Extracted from {@link ProcessComponent}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubTablePhysicalMetadataCache {

    private final JdbcTemplate jdbcTemplate;

    private final Map<String, List<String>> pkColumnsCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> tableExistsCache = new ConcurrentHashMap<>();

    List<String> resolvePkColumnsCached(String safeTableName) {
        List<String> cached = pkColumnsCache.get(safeTableName);
        if (cached != null) {
            return cached;
        }
        List<String> resolved = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate, safeTableName);
        pkColumnsCache.put(safeTableName, resolved);
        return resolved;
    }

    boolean subTableExists(String tableName) {
        Boolean cached = tableExistsCache.get(tableName);
        if (cached != null) {
            return cached;
        }
        boolean exists;
        try {
            String resolved = jdbcTemplate.queryForObject("SELECT to_regclass(?)::text", String.class, tableName);
            exists = resolved != null && !resolved.isBlank();
        } catch (Exception e) {
            exists = false;
        }
        tableExistsCache.put(tableName, exists);
        return exists;
    }

    boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName);
        return count != null && count > 0;
    }

    static String requireSafeIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid table name");
        }
        return identifier;
    }
}
