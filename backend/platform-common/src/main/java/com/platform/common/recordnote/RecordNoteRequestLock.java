package com.platform.common.recordnote;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Transaction-scoped coordination for note delivery and committed engine state changes. */
public final class RecordNoteRequestLock {
    private RecordNoteRequestLock() {}

    /** Acquire on the caller's transaction connection; PostgreSQL releases it on rollback/commit. */
    public static void acquire(JdbcTemplate jdbc, String processId) {
        if (processId == null || processId.isBlank()) {
            throw new IllegalArgumentException("Missing process identity");
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Record Note coordination requires a transaction");
        }
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 718926))",
                (rs, n) -> null, "record-note:" + processId);
    }
}
