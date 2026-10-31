package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.AssignmentCaseFence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "ASSIGNMENT_TEST_JDBC_URL", matches = ".+")
class AssignmentOperationDatabaseTest extends AssignmentPostgresFixture {
    AssignmentResponsibilityRepository records;
    AssignmentOperationRepository operations;
    UUID caseId;
    String taskId;
    final String digest = "a".repeat(64);

    @BeforeEach void createTask() {
        caseId = UUID.randomUUID();
        taskId = "task-" + caseId;
        String instance = "root-" + caseId;
        var json = new ObjectMapper();
        records = new AssignmentResponsibilityRepository(jdbc, json);
        operations = new AssignmentOperationRepository(jdbc, json);
        jdbc.update("INSERT INTO we_assignment_cases(case_id,initiator_user_id,runtime_contract_version) VALUES(?,'initiator',1)", caseId);
        jdbc.update("INSERT INTO we_assignment_instances(process_instance_id,case_id,round_id,process_definition_id,function_unit_code,call_path,state) "
                + "VALUES(?,?,?,'definition','synthetic-fu','[]','ACTIVE')", instance, caseId, UUID.randomUUID());
        jdbc.update("INSERT INTO we_assignment_task_states(task_id,case_id,process_instance_id,process_definition_id,node_key,slot_key,slot_identity,"
                + "policy_hash,primary_candidates,state) VALUES(?,?,?,'definition','work','slot','{}','hash','[]','ACTIVE')", taskId, caseId, instance);
    }

    @Test void lostResponseCanBeRecoveredAndPortalConfirmationIsIdempotent() {
        UUID id = accept();
        var receipt = operations.find(id).orElseThrow();
        assertEquals(taskId, receipt.taskId());
        assertEquals("PENDING_SYNC", receipt.state());
        assertEquals(Map.of("fields", Map.of("result", "accepted")), receipt.acceptedEffect());
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> operations.confirm(id, digest)));
        transaction.executeWithoutResult(status -> {
            var fence = new AssignmentCaseFence(jdbc);
            fence.locked(caseId);
            jdbc.update("INSERT INTO p0_saved VALUES(?,?)", id.toString(), "accepted");
            jdbc.update("INSERT INTO up_assignment_operation_sync(operation_id,case_id,operation_seq,request_digest,effect_digest) VALUES(?,?,1,?,?)",
                    id, caseId, digest, digest);
            fence.portalApplied(caseId, 1, 0);
        });
        transaction.executeWithoutResult(status -> operations.confirm(id, digest));
        transaction.executeWithoutResult(status -> operations.confirm(id, digest));
        assertEquals("APPLIED", operations.byTask(taskId).orElseThrow().state());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM p0_saved WHERE id=?", Integer.class, id.toString()));
    }

    @Test void sameOperationWithChangedPayloadIsRejectedWithoutAnotherCompletion() {
        UUID id = accept();
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> operations.accept(command(id, "b".repeat(64)))));
        assertEquals(digest, operations.find(id).orElseThrow().requestDigest());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM we_assignment_completion_records WHERE case_id=?", Integer.class, caseId));
    }

    @Test void failedCompletionRollsBackBothResponsibilityAndOperation() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            records.completed(taskId, "actor", Set.of("actor"));
            operations.accept(command(id, digest));
            throw new IllegalStateException("synthetic failure after acceptance");
        }));
        assertTrue(operations.find(id).isEmpty());
        assertTrue(records.effective(caseId).isEmpty());
        assertEquals(0, jdbc.queryForObject("SELECT accepted_operation_seq FROM we_assignment_cases WHERE case_id=?", Long.class, caseId));
    }

    @Test void pendingEffectsBlockNewWritesUsingStalePortalData() {
        accept();
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status ->
                new AssignmentCaseFence(jdbc).portalWritten(caseId, 0, false)));
        assertEquals(0, jdbc.queryForObject("SELECT portal_revision FROM we_assignment_cases WHERE case_id=?", Long.class, caseId));
    }

    @Test void synchronizationConfirmationMakesCandidateReconciliationDueImmediately() {
        UUID operation=accept();
        transaction.executeWithoutResult(status->new AssignmentCaseFence(jdbc).portalApplied(caseId,1,0));
        jdbc.update("UPDATE we_assignment_cases SET next_attempt_at=clock_timestamp()+interval '1 hour',last_error='ASSIGNMENT_SYNC_PENDING' WHERE case_id=?",caseId);
        transaction.executeWithoutResult(status->operations.confirm(operation,digest));
        assertTrue(jdbc.queryForObject("SELECT next_attempt_at<=clock_timestamp() FROM we_assignment_cases WHERE case_id=?",Boolean.class,caseId));
    }

    private UUID accept() {
        UUID id = UUID.randomUUID();
        transaction.executeWithoutResult(status -> {
            records.completed(taskId, "actor", Set.of("actor"));
            operations.accept(command(id, digest));
        });
        return id;
    }

    private AssignmentOperationRepository.AcceptedCompletion command(UUID id, String requestDigest) {
        return new AssignmentOperationRepository.AcceptedCompletion(id, caseId, taskId, "actor", null, "COMPLETE",
                requestDigest, 0, Map.of("fields", Map.of("result", "accepted")));
    }
}
