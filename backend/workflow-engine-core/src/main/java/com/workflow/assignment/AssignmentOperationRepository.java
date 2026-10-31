package com.workflow.assignment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.AssignmentCaseFence;
import com.platform.common.assignment.AssignmentOperationReceipt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Completion transaction's durable recovery queue, independent of successful HTTP response delivery. */
@Repository
public class AssignmentOperationRepository {
    private static final String COLUMNS = "operation_id,case_id,task_id,actor_user_id,represented_user_id,operation,request_digest,"
            + "operation_seq,portal_base_revision,accepted_effect::text,state";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AssignmentOperationRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Optional<AssignmentOperationReceipt> find(UUID operationId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM we_assignment_operations WHERE operation_id=?", this::receipt, operationId)
                .stream().findFirst();
    }

    public Optional<AssignmentOperationReceipt> byTask(String taskId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM we_assignment_operations WHERE task_id=?", this::receipt, taskId)
                .stream().findFirst();
    }

    /** Caller already validated actor, digest, data baseline and completed the task in this transaction. */
    public AssignmentOperationReceipt accept(AcceptedCompletion command) {
        UUID operationId = command.operationId(), caseId = command.caseId();
        String taskId = command.taskId(), actor = command.actor(), represented = command.represented();
        String operation = command.operation(), digest = command.digest();
        long portalBase = command.portalBase();
        Map<String,Object> effect = command.effect();
        if (operationId == null || actor == null || actor.isBlank() || digest == null || !digest.matches("[0-9a-f]{64}")
                || effect == null) throw new IllegalArgumentException("Incomplete accepted operation");
        var fence = new AssignmentCaseFence(jdbc).locked(caseId);
        var previous = find(operationId);
        if (previous.isPresent()) {
            var receipt = previous.get();
            if (!receipt.caseId().equals(caseId) || !receipt.taskId().equals(taskId) || !receipt.actorUserId().equals(actor)
                    || !java.util.Objects.equals(receipt.representedUserId(), represented)
                    || !receipt.operation().equals(operation) || !receipt.requestDigest().equals(digest)) {
                throw new IllegalStateException("ASSIGNMENT_OPERATION_CONFLICT");
            }
            return receipt;
        }
        if (fence.synchronizationPending() || fence.portalRevision() != portalBase) {
            throw new IllegalStateException("ASSIGNMENT_SYNC_PENDING");
        }
        Long sequence = jdbc.queryForObject("UPDATE we_assignment_cases SET accepted_operation_seq=accepted_operation_seq+1 "
                + "WHERE case_id=? RETURNING accepted_operation_seq", Long.class, caseId);
        jdbc.update("INSERT INTO we_assignment_operations(operation_id,case_id,task_id,actor_user_id,represented_user_id,operation,"
                        + "request_digest,operation_seq,portal_base_revision,accepted_effect,state) VALUES(?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),'PENDING_SYNC')",
                operationId, caseId, taskId, actor, represented, operation, digest, sequence, portalBase, encode(effect));
        return find(operationId).orElseThrow(() -> new IllegalStateException("Accepted operation was not persisted"));
    }

    /** Internal recovery caller must have verified Portal's committed idempotency ledger. */
    public void confirm(UUID operationId, String requestDigest) {
        var located = find(operationId).orElseThrow(() -> new IllegalStateException("Unknown accepted operation"));
        var state = new AssignmentCaseFence(jdbc).locked(located.caseId());
        var receipt = find(operationId).orElseThrow(() -> new IllegalStateException("Unknown accepted operation"));
        if (!receipt.requestDigest().equals(requestDigest)) throw new IllegalStateException("ASSIGNMENT_OPERATION_CONFLICT");
        if ("APPLIED".equals(receipt.state())) return;
        if (state.portalAppliedOperationSeq() < receipt.operationSeq()
                || state.confirmedOperationSeq() + 1 != receipt.operationSeq()) {
            throw new IllegalStateException("Portal has not committed this accepted effect");
        }
        jdbc.update("UPDATE we_assignment_operations SET state='APPLIED',last_error=NULL WHERE operation_id=?", operationId);
        jdbc.update("UPDATE we_assignment_cases SET confirmed_operation_seq=?,next_attempt_at=clock_timestamp() WHERE case_id=?",
                receipt.operationSeq(), receipt.caseId());
    }

    public List<AssignmentOperationReceipt> pending(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid recovery batch size");
        return jdbc.query("SELECT " + COLUMNS + " FROM we_assignment_operations WHERE state='PENDING_SYNC' "
                + "AND next_attempt_at<=now() ORDER BY next_attempt_at,created_at LIMIT ?", this::receipt, limit);
    }

    public void retry(UUID operationId, boolean conflict) {
        var receipt = find(operationId).orElseThrow(() -> new IllegalStateException("Unknown accepted operation"));
        new AssignmentCaseFence(jdbc).locked(receipt.caseId());
        jdbc.update("UPDATE we_assignment_operations SET attempts=attempts+1,next_attempt_at=clock_timestamp()+interval '5 seconds',"
                        + "last_error=?,state=? WHERE operation_id=? AND state='PENDING_SYNC'",
                conflict ? "ASSIGNMENT_SYNC_CONFLICT" : "ASSIGNMENT_DEPENDENCY_UNAVAILABLE",
                conflict ? "SYNC_CONFLICT" : "PENDING_SYNC", operationId);
    }

    private AssignmentOperationReceipt receipt(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new AssignmentOperationReceipt((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getLong(8), rs.getLong(9), effect(rs.getString(10)), rs.getString(11));
    }

    private Map<String,Object> effect(String value) {
        try { return json.readValue(value, new TypeReference<Map<String,Object>>() { }); }
        catch (java.io.IOException e) { throw new IllegalStateException("Invalid stored assignment operation", e); }
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalArgumentException("Invalid accepted assignment effect", e); }
    }
    public record AcceptedCompletion(UUID operationId, UUID caseId, String taskId, String actor, String represented,
            String operation, String digest, long portalBase, Map<String,Object> effect) { }

}
