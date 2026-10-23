package com.platform.common.jdbc;

import com.platform.common.functionunit.CatalogSnapshotReader;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.function.Supplier;

/**
 * The key a sub-table's rows are identified by. A legacy physical table keeps its PRIMARY KEY
 * constraint; a JSON-row-stored table (every current business table) uses the key its table declares
 * in the request's release — so an existing request keeps the key it was started with.
 */
public final class SubTablePrimaryKeys {

    private SubTablePrimaryKeys() {
    }

    /**
     * @param catalogId the request's pinned release; asked for only when there is no physical key
     */
    public static List<String> resolve(JdbcTemplate jdbcTemplate, CatalogSnapshotReader snapshots,
                                       Supplier<String> catalogId, String tableName) {
        List<String> physical = PostgresPhysicalTablePrimaryKeys.physicalPrimaryKeyColumns(jdbcTemplate, tableName);
        if (!physical.isEmpty()) {
            return physical;
        }
        return snapshots.primaryKeyFields(catalogId.get(), tableName);
    }
}
