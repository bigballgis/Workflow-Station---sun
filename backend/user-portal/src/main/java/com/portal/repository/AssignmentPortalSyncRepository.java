package com.portal.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.assignment.AssignmentOperationReceipt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Portal-owned idempotency ledger and content-checked local variable writes. */
@Repository
public class AssignmentPortalSyncRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public AssignmentPortalSyncRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public Optional<Applied> applied(UUID id) {
        return jdbc.query("SELECT request_digest,effect_digest FROM up_assignment_operation_sync WHERE operation_id=?",
                (rs,n) -> new Applied(rs.getString(1), rs.getString(2)), id).stream().findFirst();
    }

    public Variables variables(String instance) {
        return jdbc.query("SELECT variables::text,lock_version FROM up_process_instance WHERE id=?",
                (rs,n) -> new Variables(instance, rs.getString(1), rs.getObject(2, Long.class), object(rs.getString(1))), instance)
                .stream().findFirst().orElseThrow(() -> new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_SYNC_CONFLICT));
    }

    public void replace(Variables baseline, ObjectNode accepted) {
        int changed = jdbc.update("UPDATE up_process_instance SET variables=CAST(? AS jsonb),lock_version=COALESCE(lock_version,0)+1,"
                        + "updated_at=clock_timestamp() WHERE id=? AND lock_version IS NOT DISTINCT FROM ? "
                        + "AND variables IS NOT DISTINCT FROM CAST(? AS jsonb)",
                accepted.toString(), baseline.instance(), baseline.version(), baseline.raw());
        if (changed != 1) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_SYNC_CONFLICT);
    }

    public void record(AssignmentOperationReceipt receipt, String effectDigest) {
        jdbc.update("INSERT INTO up_assignment_operation_sync(operation_id,case_id,operation_seq,request_digest,effect_digest) VALUES(?,?,?,?,?)",
                receipt.operationId(), receipt.caseId(), receipt.operationSeq(), receipt.requestDigest(), effectDigest);
    }

    public void applyAudit(AssignmentOperationReceipt receipt,com.platform.common.assignment.AssignmentCompletionRequest.Effect effect) {
        for (var change:effect.auditChanges()) {
            if (change.kind()==null || change.timestamp()==null || change.fieldName()==null || change.fieldName().isBlank()) {
                throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_SYNC_CONFLICT);
            }
            jdbc.update("INSERT INTO up_change_history(process_instance_id,task_instance_id,stage_id,user_id,timestamp,field_name,old_value,new_value,"
                            + "change_type,sub_table_name,row_identifier,is_concurrent,batch_id,batch_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,false,?,?)",
                    effect.instanceId(),receipt.taskId(),effect.stageId(),receipt.actorUserId(),java.sql.Timestamp.from(change.timestamp()),change.fieldName(),
                    change.oldValue(),change.newValue(),change.kind().name(),change.subTableName(),change.rowIdentifier(),change.batchId(),
                    change.batchTime()==null?null:java.sql.Timestamp.from(change.batchTime()));
        }
    }

    private ObjectNode object(String value) {
        if (value == null) return json.createObjectNode(); // The schema permits a genuinely empty initial variables column.
        try {
            var node = json.readTree(value);
            if (!(node instanceof ObjectNode object)) throw new IllegalStateException("Stored Portal variables are not an object");
            return object;
        } catch (java.io.IOException e) { throw new IllegalStateException("Stored Portal variables are invalid", e); }
    }

    public record Applied(String requestDigest, String effectDigest) { }
    public record Variables(String instance, String raw, Long version, ObjectNode values) { }
}
