package com.platform.common.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Transaction-bound PostgreSQL coordination; the lock always uses the caller's bound JDBC connection. */
public final class PostgresTransactionLock {
    private PostgresTransactionLock() { }

    /** Acquire an exclusive lock for a server-derived namespace and identity. */
    public static void exclusive(JdbcTemplate jdbc, String namespace, String identity) {
        acquire(jdbc, namespace, identity, false);
    }

    /** Acquire a shared lock for a server-derived namespace and identity. */
    public static void shared(JdbcTemplate jdbc, String namespace, String identity) {
        acquire(jdbc, namespace, identity, true);
    }

    private static void acquire(JdbcTemplate jdbc, String namespace, String identity, boolean shared) {
        if (namespace == null || namespace.isBlank() || identity == null || identity.isBlank()) {
            throw new IllegalArgumentException("Missing transaction lock identity");
        }
        if (jdbc.getDataSource() == null || !TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(jdbc.getDataSource())) {
            throw new IllegalStateException("Coordination requires this data source's transaction");
        }
        jdbc.query("SELECT set_config('lock_timeout',CASE WHEN current_setting('lock_timeout')::interval=interval '0' "
                + "OR current_setting('lock_timeout')::interval>interval '2 seconds' THEN '2s' ELSE current_setting('lock_timeout') END,true)", rs -> {});
        String sql = shared
                ? "SELECT pg_advisory_xact_lock_shared(hashtextextended(?, 1179))"
                : "SELECT pg_advisory_xact_lock(hashtextextended(?, 1179))";
        jdbc.query(sql, rs -> {}, namespace + ":" + identity);
    }
}
