package com.platform.common.assignment;

import com.platform.common.jdbc.PostgresTransactionLock;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/** Narrow cross-service case coordination; contains no candidate or responsibility decisions. */
public final class AssignmentCaseFence {
    private final JdbcTemplate jdbc;

    public AssignmentCaseFence(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Current case state from the caller's connection after acquiring its transaction lock. */
    public State locked(UUID caseId) {
        if (caseId == null) throw new IllegalArgumentException("Missing assignment case");
        PostgresTransactionLock.exclusive(jdbc, "assignment", caseId.toString());
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            if (connection.getTransactionIsolation() != java.sql.Connection.TRANSACTION_READ_COMMITTED) {
                throw new IllegalStateException("Assignment writes require READ COMMITTED isolation");
            }
            return null;
        });
        return jdbc.queryForObject("SELECT revision,portal_revision,accepted_operation_seq,portal_applied_operation_seq,"
                        + "confirmed_operation_seq,pending_reopen_operation_id,portal_applied_reopen_operation_id FROM we_assignment_cases WHERE case_id=?",
                (rs, n) -> new State(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5),
                        (UUID) rs.getObject(6), (UUID) rs.getObject(7)), caseId);
    }

    /** Publish a Portal business write in the same transaction as the data, after acquiring locked. */
    public void portalWritten(UUID caseId, long expectedPortalRevision, boolean changesCandidates) {
        locked(caseId);
        int changed = jdbc.update("UPDATE we_assignment_cases SET portal_revision=portal_revision+1,revision=revision+? "
                        + "WHERE case_id=? AND portal_revision=? AND accepted_operation_seq=confirmed_operation_seq AND pending_reopen_operation_id IS NULL",
                changesCandidates ? 1 : 0, caseId, expectedPortalRevision);
        if (changed != 1) throw new IllegalStateException("Assignment case changed or has pending synchronization");
    }

    /** Advance the Portal application watermark in the transaction that inserts its own sync ledger. */
    public void portalApplied(UUID caseId, long operationSeq, long expectedPortalRevision) {
        locked(caseId);
        int changed = jdbc.update("UPDATE we_assignment_cases SET portal_applied_operation_seq=?,portal_revision=portal_revision+1,revision=revision+1 "
                        + "WHERE case_id=? AND portal_applied_operation_seq=? AND portal_revision=? AND accepted_operation_seq>=?",
                operationSeq, caseId, operationSeq - 1, expectedPortalRevision, operationSeq);
        if (changed != 1) throw new IllegalStateException("Assignment effect is out of order or its data baseline changed");
    }

    public boolean hasPendingSynchronization() {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM we_assignment_cases WHERE accepted_operation_seq>confirmed_operation_seq)", Boolean.class));
    }

    public boolean hasPendingReopen() {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM we_assignment_cases WHERE pending_reopen_operation_id IS NOT NULL)", Boolean.class));
    }

    public void portalReopenApplied(UUID caseId, UUID operationId) {
        var state = locked(caseId);
        if (!operationId.equals(state.pendingReopen())) throw new IllegalStateException("Unknown pending reopen effect");
        if (operationId.equals(state.portalAppliedReopen())) return;
        int changed = jdbc.update("UPDATE we_assignment_cases SET portal_applied_reopen_operation_id=?,portal_revision=portal_revision+1,revision=revision+1 "
                        + "WHERE case_id=? AND pending_reopen_operation_id=?", operationId, caseId, operationId);
        if (changed != 1) throw new IllegalStateException("Reopen synchronization changed");
    }

    /** Compare accepted and synchronized operation watermarks. */
    public record State(long revision, long portalRevision, long acceptedOperationSeq,
            long portalAppliedOperationSeq, long confirmedOperationSeq, UUID pendingReopen, UUID portalAppliedReopen) {
        public State(long revision, long portalRevision, long acceptedOperationSeq, long portalAppliedOperationSeq, long confirmedOperationSeq) {
            this(revision, portalRevision, acceptedOperationSeq, portalAppliedOperationSeq, confirmedOperationSeq, null, null);
        }
        public boolean synchronizationPending() {
            return acceptedOperationSeq != confirmedOperationSeq || pendingReopen != null;
        }
    }
}
