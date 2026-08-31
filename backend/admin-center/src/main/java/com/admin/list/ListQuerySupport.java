package com.admin.list;

import org.slf4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared mechanics for list queries that page in SQL: parameter binding, slow-query log required by the shared-list spec (&gt;1s WARN, no row
 * content, no filter values).
 */
public final class ListQuerySupport {

    private static final long SLOW_MS = 1_000L;

    private ListQuerySupport() {
    }

    public static <T> T query(JdbcTemplate jdbcTemplate, String sql, List<Object> params,
                              ResultSetExtractor<T> extractor) {
        PreparedStatementCreator creator = connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            int index = 1;
            for (Object param : params) {
                statement.setObject(index++, param);
            }
            return statement;
        };
        return jdbcTemplate.query(creator, extractor);
    }

    public static long requireCount(Long total, String listKey) {
        if (total == null) {
            throw new IllegalStateException("COUNT(*) returned null for " + listKey);
        }
        return total;
    }


    public static void logIfSlow(Logger log, String listKey, int page, int size, long total,
                                 long startedNanos) {
        long elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000L;
        if (elapsedMs > SLOW_MS) {
            log.warn("Slow list query listKey={} page={} size={} total={} elapsedMs={}",
                    listKey, page, size, total, elapsedMs);
        }
    }
}
